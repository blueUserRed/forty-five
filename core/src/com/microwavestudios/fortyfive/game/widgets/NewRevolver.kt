package com.microwavestudios.fortyfive.game.widgets

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Colors
import com.microwavestudios.fortyfive.utils.Promise
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.Utils
import com.microwavestudios.fortyfive.utils.component1
import com.microwavestudios.fortyfive.utils.component2
import com.microwavestudios.fortyfive.utils.requireNull
import kotlin.math.cos
import kotlin.math.sin

class NewRevolver(private val screen: RenderableScreen) : IRevolver {

    override val slots: Array<out NewRevolverSlot> = Array(5) {
        NewRevolverSlot(it, this, screen)
    }

    private val orderedChildren: List<Actor> by lazy {
        listOf(slots[4], slots[0], slots[1], slots[2], slots[3])
    }

    private var createdActor: Actor? = null

    fun getActor(creator: ScreenCreator): Actor {
        createdActor?.let { return it }
        with(creator) {
            val actor = createWithReceiver()
            createdActor = actor
            return actor
        }
    }

    override fun setCard(slot: Int, card: Card?) {
        require(slot in 1..5) { "slot must be in 1..5" }
        val slot = slots[slot]
        if (card != null) {
            requireNull(slot.card) { "slot already contains a card: $slot, $card, in slot: ${slot.card}" }
            slot.card = card
        } else {
            requireNotNull(slot.card) { "cant remove card from slot without card: $slot" }
            slot.card = null
        }
    }

    override fun preAddCard(slot: Int, card: Card) {
        TODO("Not yet implemented")
    }

    override fun removeCard(slot: Int) {
        setCard(slot, null)
    }

    override fun removeCard(card: Card) {
        val slot = slots.find { it.card == card }
        requireNotNull(slot) { "no slot with card: $card" }
        slot.card = null
    }

    override fun getCardInSlot(slot: Int): Card? {
        require(slot in 1..5) { "slot must be in 1..5" }
        return slots[slot].card
    }

    override fun getCardTriggerPosition(): Vector2 {
        TODO("Not yet implemented")
    }

    override fun getMirroredCardTriggerPosition(): Vector2 {
        TODO("Not yet implemented")
    }

    override fun getCardOnShotTriggerPosition(): Vector2 {
        TODO("Not yet implemented")
    }

    override fun rotate(rotation: RevolverRotation): Timeline {
        TODO("Not yet implemented")
    }

    override fun forceGetActor(): Actor = createdActor!!

    private fun ScreenCreator.createWithReceiver() = newGroup {
        width = revolverWidth
        heightByAspectRatio(1856.0 / 2347.0)

        image {
            backgroundHandle = "encounter_revolver_handle"
            relativeWidth(100f)
            relativeHeight(100f)
            x = 0f
            y = 0f
        }

        image {
            backgroundHandle = "encounter_revolver_drum_bottom"
            relativeWidth(100f)
            relativeHeight(100f)
            x = 0f
            y = 0f
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
                label("red wing", Utils.convertSlotRepresentation(i).toString(), Colors.DARK_GRAY, 40) {
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
        }

        group {
            relativeWidth(100f)
            heightByAspectRatio(1.0)
            x = 0f
            y = 50f
            childrenInCorrectOrderGetter = { orderedChildren }
            repeat(5) { i ->
                actor(slots[i].getActor(this@createWithReceiver))
            }
        }

        image {
            backgroundHandle = "encounter_revolver_drum_top"
            relativeWidth(100f)
            heightByAspectRatio(1.0)
            x = 0f
            y = 50f
        }
    }


    class NewRevolverSlot(
        override var num: Int,
        override val revolver: IRevolver,
        screen: RenderableScreen,
    ) : CustomGroup(screen), IRevolverSlot {

        override var card: Card? = null
            internal set

        private var createdActor: Actor? = null

        fun getActor(creator: ScreenCreator): Actor {
            createdActor?.let { return it }
            with(creator) {
                val actor = createWithReceiver()
                createdActor = actor
                return actor
            }
        }

        override fun draw(batch: Batch?, parentAlpha: Float) {
            super.draw(batch, parentAlpha)
        }

        override fun cardPosition(): Vector2 {
            TODO("Not yet implemented")
        }

        override fun forceGetActor(): RevolverSlot {
            TODO("Not yet implemented")
        }

        override fun enterSelectionMode(promise: Promise<IRevolverSlot>) {
            TODO("Not yet implemented")
        }

        override fun exitSelectionMode() {
            TODO("Not yet implemented")
        }

        private fun ScreenCreator.createWithReceiver(): Actor = newGroup {
            width = slotSize
            height = slotSize
            backgroundHandle = "red_texture"
            val r = revolverWidth / 2
            val innerR = r - 75f
            val angle = angleForIndex(num)
            val (dx, dy) = posForAngle(angle, innerR)
            setPosition(r + dx - slotSize / 2, r + dy - slotSize / 2)
        }

    }

    companion object {

        private const val rotationOff: Double = (Math.PI / 2f) + (2f * Math.PI) / 5f
        private const val slotAngleOff: Double = (2 * Math.PI) / 5
        private const val slotSize: Float = 120f
        private const val revolverWidth: Float = 350f


        private fun posForAngle(angle: Double, r: Float): Vector2 {
            val dx = cos(angle) * r
            val dy = sin(angle) * r
            return Vector2(dx.toFloat(), dy.toFloat())
        }

        private fun angleForIndex(i: Int): Double = slotAngleOff * i + rotationOff

    }
}
