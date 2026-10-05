package com.microwavestudios.fortyfive.game.widgets

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.microwavestudios.fortyfive.animation.PropertyAnimation
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.game.widgets.RevolverSlot.Companion.revolverSlotGroup
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.CustomImageActor
import com.microwavestudios.fortyfive.screen.actors.PropertyAction
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Colors
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Promise
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.Utils
import com.microwavestudios.fortyfive.utils.alpha
import com.microwavestudios.fortyfive.utils.asPromise
import com.microwavestudios.fortyfive.utils.collectParallelTimeline
import com.microwavestudios.fortyfive.utils.component1
import com.microwavestudios.fortyfive.utils.component2
import com.microwavestudios.fortyfive.utils.contains
import com.microwavestudios.fortyfive.utils.degrees
import com.microwavestudios.fortyfive.utils.requireNull
import com.microwavestudios.fortyfive.utils.setPosition
import com.microwavestudios.fortyfive.utils.unreachable
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.ranges.contains

class NewRevolver(
    private val screen: RenderableScreen,
    private val events: EventPipeline
) : IRevolver {

    override val slots: Array<out NewRevolverSlotRepr> = Array(5) {
        NewRevolverSlotRepr(it + 1)
    }

    private val slotActors: Array<out NewRevolverSlotActor> = Array(5) {
        NewRevolverSlotActor(it + 1, this, events, screen)
    }

    private val orderedChildren: List<Actor> by lazy {
        listOf(slotActors[4], slotActors[0], slotActors[1], slotActors[2], slotActors[3])
    }

    private val addedListenerToCards: MutableList<Card> = mutableListOf()

    private var createdActor: CustomGroup? = null
    private lateinit var revolverDrum: CustomImageActor

    fun getActor(creator: ScreenCreator): CustomGroup {
        createdActor?.let { return it }
        with(creator) {
            val actor = createWithReceiver()
            createdActor = actor
            return actor
        }
    }

    override fun setCard(slot: Int, card: Card?) {
        requireNotNull(createdActor)
        require(slot in 1..5) { "slot must be in 1..5" }
        val slot = slots[slot - 1]
        if (card != null) {
            requireNull(slot.card) { "slot already contains a card: $slot, $card, in slot: ${slot.card}" }
            slot.card = card
            setupCard(card)
            slot.slotActor.position(angleForIndex(slot.num - 1))
        } else {
            val cardInSlot = slot.card
            requireNotNull(cardInSlot) { "cant remove card from slot without card: $slot" }
            slot.card = null
            createdActor!!.removeActor(cardInSlot.presentation.forceGetActor())
        }
        createdActor!!.invalidateHierarchy()
    }

    private fun setupCard(card: Card) {
        val cardActor = card.presentation.forceGetActor()
        cardActor.fixedZIndex = cardZIndex
        cardActor.width = cardSize
        cardActor.height = cardSize
        createdActor!!.addActor(cardActor)
        if (card !in addedListenerToCards) addListenerToCard(card)
    }

    private fun addListenerToCard(card: Card) {
        addedListenerToCards.add(card)
        val actor = card.presentation.forceGetActor()
        val createdActor = createdActor
        requireNotNull(createdActor)
        actor.observeInputState(
            GameInputs.States.focused,
            {
                if (actor !in createdActor) return@observeInputState
                actor.fixedZIndex = 100
                createdActor.resortZIndices()
            },
            {
                if (actor !in createdActor) return@observeInputState
                actor.fixedZIndex = cardZIndex
                createdActor.resortZIndices()
            },
        )
    }

    override fun preAddCard(slot: Int, card: Card) {
        requireNotNull(createdActor)
        require(slot in 1..5) { "slot must be in 1..5" }
        val slot = slots[slot - 1]
        setupCard(card)
        val cardActor = card.presentation.forceGetActor()
        cardActor.setPosition(slot.slotActor.cardPosition())
    }

    override fun removeCard(slot: Int) {
        setCard(slot, null)
    }

    override fun removeCard(card: Card) {
        val slot = slots.find { it.card == card }
        requireNotNull(slot) { "no slot with card: $card" }
        setCard(slot.num, null)
    }

    override fun getCardInSlot(slot: Int): Card? {
        require(slot in 1..5) { "slot must be in 1..5" }
        return slots[slot - 1].card
    }

    override fun getCardTriggerPosition(): Vector2 =
        Vector2(slots[0].slotActor.x - slotSize * 2, slots[4].slotActor.y + slotSize * 2)

    override fun getMirroredCardTriggerPosition(): Vector2 =
        Vector2(slots[3].slotActor.x - slotSize * 2, slots[4].slotActor.y + slotSize * 2)

    override fun getCardOnShotTriggerPosition(): Vector2 =
        Vector2(slots[4].slotActor.x, slots[4].slotActor.y + slots[0].slotActor.height * 3)

    override fun rotate(rotation: RevolverRotation): Timeline {
        if (rotation.amount == 0) return Timeline.emptyTimeline
        return when (rotation) {
            is RevolverRotation.Right -> rotateRight(rotation.amount)
            is RevolverRotation.Left -> rotateLeft(rotation.amount)
            else -> unreachable()
        }
    }

    private fun rotateRight(amount: Int): Timeline = Timeline.timeline {
        val oneSlotAngle = -(2 * Math.PI) / 5
        val totalRotationAngle = amount * oneSlotAngle
        val duration = 0.4f * amount

        val slotAnimTimeline = slotActors.map { slot ->
            val action = PropertyAction(Double::class, slot, slot::rotationOff, totalRotationAngle)
            action.duration = duration
            action.interpolation = defaultInterpolation
            Timeline.timeline {
                action { slot.addAction(action) }
                delayUntil { action.isComplete }
            }
        }.collectParallelTimeline()

        val revolverDrumAction = PropertyAction(
            Float::class, revolverDrum,
            { revolverDrum.rotation },
            { value -> revolverDrum.rotation = value },
            revolverDrum.rotation + totalRotationAngle.degrees.toFloat()
        )
        revolverDrumAction.duration = duration
        revolverDrumAction.interpolation = defaultInterpolation
        val revolverDrumTimeline = Timeline.timeline {
            action { revolverDrum.addAction(revolverDrumAction) }
            delayUntil { revolverDrumAction.isComplete }
        }

        parallelActions(
            slotAnimTimeline.asAction(),
            revolverDrumTimeline.asAction()
        )
        later {
            revolverDrum.rotation %= 360f
            slotActors.forEach { slot ->
                var num = slot.num - 1
                num += 500 // prevent negative, gets removed by % 5
                num -= amount
                num %= 5
                slot.num = num + 1
            }
            slotActors.forEach { it.rotationOff = 0.0 }
        }
    }
    private fun rotateLeft(amount: Int): Timeline = Timeline.timeline {
        val oneSlotAngle = (2 * Math.PI) / 5
        val totalRotationAngle = amount * oneSlotAngle
        val duration = 0.4f * amount

        val slotAnimTimeline = slotActors.map { slot ->
            val action = PropertyAction(Double::class, slot, slot::rotationOff, totalRotationAngle)
            action.duration = duration
            action.interpolation = defaultInterpolation
            Timeline.timeline {
                action { slot.addAction(action) }
                delayUntil { action.isComplete }
            }
        }.collectParallelTimeline()

        val revolverDrumAction = PropertyAction(
            Float::class, revolverDrum,
            { revolverDrum.rotation },
            { value -> revolverDrum.rotation = value },
            revolverDrum.rotation + totalRotationAngle.degrees.toFloat()
        )
        revolverDrumAction.duration = duration
        revolverDrumAction.interpolation = defaultInterpolation
        val revolverDrumTimeline = Timeline.timeline {
            action { revolverDrum.addAction(revolverDrumAction) }
            delayUntil { revolverDrumAction.isComplete }
        }

        parallelActions(
            slotAnimTimeline.asAction(),
            revolverDrumTimeline.asAction()
        )
        later {
            revolverDrum.rotation %= 360f
            slotActors.forEach { slot ->
                var num = slot.num - 1
                num += amount
                num %= 5
                slot.num = num + 1
            }
            slotActors.forEach { it.rotationOff = 0.0 }
        }
    }

    override fun forceGetActor(): Actor = createdActor!!

    private fun ScreenCreator.createWithReceiver() = newGroup {
        width = revolverWidth
        heightByAspectRatio(1856.0 / 2347.0)

        onLayout { slotActors.forEach { it.position(angleForIndex(it.num - 1)) } }

        image {
            backgroundHandle = "encounter_revolver_handle"
            relativeWidth(100f)
            relativeHeight(100f)
            x = 0f
            y = 0f
            fixedZIndex = 0
            originCenter()
            rotation = 3f
        }

        image {
            backgroundHandle = "encounter_revolver_drum_bottom"
            relativeWidth(100f)
            relativeHeight(100f)
            x = 0f
            y = 0f
            fixedZIndex = 1
        }

        group {
            relativeWidth(100f)
            heightByAspectRatio(1.0)
            x = 0f
            y = 50f

            val slotSize = 70f
            repeat(5) { i -> group {
                height = slotSize
                width = slotSize
                label("red wing", Utils.convertSlotRepresentation(i + 1).toString(), Colors.Taupe_gray, 40) {
                    syncWidth()
                    syncHeight()
                    centerX()
                    centerY()
                }
                val r = revolverWidth / 2
                val innerR = r - 73f
                val angle = angleForIndex(i)
                val (dx, dy) = posForAngle(angle, innerR)
                setPosition(r + dx - slotSize / 2, r + dy - slotSize / 2)
            } }
            fixedZIndex = 2
        }

        group {
            relativeWidth(100f)
            heightByAspectRatio(1.0)
            x = 0f
            y = 50f
            childrenInCorrectOrderGetter = { orderedChildren }
            repeat(5) { i ->
                actor(slotActors[i])
            }
            fixedZIndex = 4
        }

        revolverDrum = image {
            backgroundHandle = "encounter_revolver_drum_top"
            relativeWidth(100f)
            heightByAspectRatio(1.0)
            x = 0f
            y = 50f
            fixedZIndex = 5
        }
    }


    class NewRevolverSlotActor(
        var num: Int,
        val revolver: IRevolver,
        private val events: EventPipeline,
        screen: RenderableScreen,
    ) : CustomGroup(screen) {

        private val selectionPromise: Promise<IRevolverSlot>? = null

        var rotationOff: Double = 0.0
            set(value) {
                field = value
                position(angleForIndex(num - 1) + value)
            }

        var card: Card? = null
            internal set

        init {
            name = "revolver slot $num"
            width = slotSize
            height = slotSize
            alpha = 0.4f
            backgroundHandle = "encounter_revolver_slot_gradient"
//            backgroundHandle = "encounter_revolver_slot_gold_shadow"
            touchable = Touchable.enabled
            keyboardFocusable = KeyboardFocusable.LEAF
            joinGroup(revolverSlotGroup)
            val slotNum = Utils.convertSlotRepresentation(num)
            focusShortcut(GameInputs.focusShortcutRevolverSlots[slotNum - 1])
            observeInputState(
                GameInputs.States.focused,
                {
                    card?.presentation?.forceGetActor()?.enterInputStateManually(GameInputs.States.manuallyFocused)
                },
                {
                    card?.presentation?.forceGetActor()?.leaveInputStateManually(GameInputs.States.manuallyFocused)
                }
            )
            onInput(GameInputs.interact) {
                selectionPromise?.resolve(revolver.slots.find { it.num == num }!!)
                card?.presentation?.forceGetActor()?.clickedViaSlot(false)
            }
            onInput(GameInputs.triggerCard) {
                card?.presentation?.forceGetActor()?.clickedViaSlot(true)
            }
            isDropTarget = true
            onDrop { actor ->
                if (actor !is CardActor) return@onDrop
                events.fire(CardHand.CardDraggedOntoSlotEvent(actor.card, revolver.slots.find { it.num == num }!!))
            }
        }

        fun position(angle: Double) {
            val r = revolverWidth / 2
            val innerR = r - 76f
            val (dx, dy) = posForAngle(angle, innerR)
            setPosition(r + dx - slotSize / 2, r + dy - slotSize / 2)
            val card = card ?: return
            val cardActor = card.presentation.forceGetActor()
            val cardPos = calcCardPosition()
            cardActor.setBounds(cardPos.x, cardPos.y, cardSize, cardSize)
        }

        private fun calcCardPosition(): Vector2 {
            val cardX = x + width / 2 - cardSize / 2
            val cardY = y + height - cardSize / 2
            return Vector2(cardX, cardY)
        }

        override fun draw(batch: Batch?, parentAlpha: Float) {
            super.draw(batch, parentAlpha)
        }

        fun cardPosition(): Vector2 = calcCardPosition()

        fun enterSelectionMode(promise: Promise<IRevolverSlot>) {
            TODO("Not yet implemented")
        }

        fun exitSelectionMode() {
            TODO("Not yet implemented")
        }

    }

    inner class NewRevolverSlotRepr(
        override val num: Int
    ) : IRevolverSlot {


        val slotActor: NewRevolverSlotActor
            get() = slotActors.find { it.num == num }!!

        override var card: Card?
            get() = slotActor.card
            set(value) { slotActor.card = value }

        override val revolver: IRevolver
            get() = this@NewRevolver

        override fun cardPosition(): Vector2 = slotActor.cardPosition()

        override fun forceGetActor(): InputActor = slotActor

        override fun enterSelectionMode(promise: Promise<IRevolverSlot>) = slotActor.enterSelectionMode(promise)

        override fun exitSelectionMode() = slotActor.exitSelectionMode()

    }

    companion object {

        private const val rotationOff: Double = (Math.PI / 2f) + (2f * Math.PI) / 5f
        private const val slotAngleOff: Double = (2 * Math.PI) / 5
        private const val slotSize: Float = 98f
        private const val cardSize: Float = 110f
        private const val revolverWidth: Float = 350f
        private val defaultInterpolation: Interpolation = Interpolation.swingOut
        const val cardZIndex = 3


        private fun posForAngle(angle: Double, r: Float): Vector2 {
            val dx = cos(angle) * r
            val dy = sin(angle) * r
            return Vector2(dx.toFloat(), dy.toFloat())
        }

        private fun angleForIndex(i: Int): Double = slotAngleOff * i + rotationOff

    }
}
