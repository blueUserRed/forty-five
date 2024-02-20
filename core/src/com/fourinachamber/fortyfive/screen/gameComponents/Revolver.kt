package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.EncounterModifier
import com.fourinachamber.fortyfive.game.GameController.*
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.AnimationActor
import com.fourinachamber.fortyfive.screen.general.customActor.KeySelectableActor
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.component1
import com.fourinachamber.fortyfive.utils.component2
import com.fourinachamber.fortyfive.utils.setPosition
import ktx.actors.contains
import onj.value.OnjNamedObject
import kotlin.math.cos
import kotlin.math.sin

/**
 * actor representing the revolver
 * @param radiusExtension the width of [background] is set to radius + radiusExtension. necessary to create some space
 * between the slots and the edge of the revolver
 */
class Revolver(
    private val backgroundHandle: ResourceHandle,
    private val slotDrawableHandle: ResourceHandle,
    private val radiusExtension: Float,
    private val screen: OnjScreen
) : WidgetGroup(), ZIndexActor, StyledActor {


    override var styleManager: StyleManager? = null

    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean=false

    override var fixedZIndex: Int = 0

    var slotScale: Float? = null

    var cardZIndex: Int = 0

    /**
     * the scale of a card placed into the revolver
     */
    var cardScale: Float = 1f

    /**
     * the duration of the revolver spin animation
     */
    var animationDuration: Float = 1f

    /**
     * the radius of the circle in which the slots are laid out
     */
    var radius: Float = 1f

    /**
     * the rotation of all slots is offset by this
     */
    var rotationOff: Double = (Math.PI / 2) + slotAngleOff

    private var prefWidth: Float = 0f
    private var prefHeight: Float = 0f

    /**
     * the slots of the revolver
     */
    lateinit var slots: Array<RevolverSlot>
        private set

    private val background: Drawable by lazy {
        ResourceManager.get(screen, backgroundHandle)
    }

    private val iceShader: BetterShader by lazy {
        ResourceManager.get(screen, "ice_shader")
    }

    init {
        bindHoverStateListeners(this)
    }

    /**
     * assigns a card to a slot in the revolver; [card] can be set to null, but consider using [removeCard] instead to
     * remove a card
     */
    fun setCard(slot: Int, card: Card?) {
        if (slot !in 1..5) throw RuntimeException("slot must be between between 1 and 5")
        card?.isDraggable = false
        slots[slot - 1].card = card
        card?.actor?.let {
            it.width = slots[0].width * cardScale
            it.height = slots[0].width * cardScale
            it.rotation = 0f
            it.fixedZIndex = cardZIndex
        }
        if (card != null && card.actor !in this) addActor(card.actor)
        slots[slot - 1].position(Vector2(width / 2, height / 2), radius, angleForIndex(slot - 1))
    }

    fun preAddCard(slot: Int, card: Card) {
        if (slot !in 1..5) throw RuntimeException("slot must be between between 1 and 5")
        card.isDraggable = false
        val revolverSlot = slots[slot - 1]
        if (card.actor !in this) addActor(card.actor)
        card.actor.let {
            it.width = slots[0].width * cardScale
            it.height = slots[0].width * cardScale
            it.rotation = 0f
            it.toBack()
        }
        slots.forEach { it.toBack() }
        card.actor.setPosition(revolverSlot.cardPosition())

    }

    /**
     * removes a card from the revolver
     */
    fun removeCard(slot: Int) {
        if (slot !in 1..5) throw RuntimeException("slot must be between between 1 and 5")
        val card = getCardInSlot(slot) ?: return
        removeCard(card)
        setCard(slot, null)
    }

    /**
     * removes a card from the revolver
     */
    fun removeCard(card: Card) {
        for (slot in slots) if (slot.card === card) {
            if (card.actor in screen.stage.root) screen.removeActorFromRoot(card.actor)
            setCard(slot.num, null)
            removeActor(card.actor)
            return
        }
    }

    /**
     * @return the card in [slot]
     */
    fun getCardInSlot(slot: Int): Card? {
        if (slot !in 1..5) throw RuntimeException("slot must be between between 1 and 5")
        return slots[slot - 1].card
    }

    /**
     * true when at least one bullet is loaded into the revolver
     */
    fun isBulletLoaded(): Boolean = slots.any { it.card != null }

    fun initDragAndDrop(config:  Pair<DragAndDrop, OnjNamedObject>) {
        slots = Array(5) {
            val slot = RevolverSlot(it + 1, this, slotDrawableHandle, slotScale!!, screen, animationDuration)
            slot.reportDimensionsWithScaling = true
            slot.ignoreScalingWhenDrawing = true
            addActor(slot)
            screen.addNamedActor("revolverSlot-$it", slot)
            val (dragAndDrop, dropOnj) = config
            val dropBehaviour = DragAndDropBehaviourFactory.dropBehaviourOrError(
                dropOnj.name,
                dragAndDrop,
                slot,
                dropOnj
            )
            dragAndDrop.addTarget(dropBehaviour)
            slot
        }
        invalidateHierarchy()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()
        batch ?: return
        background.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)
        // This is really ugly but I won't bother with a better solution
        if (EncounterModifier.Frost in FortyFive.currentGame!!.encounterModifiers) {
            batch.flush()
            batch.shader = iceShader.shader
            iceShader.prepare(screen)
            background.draw(batch, x, y, width, height)
            batch.flush()
            batch.shader = null
        }
    }

    fun getCardTriggerPosition() = Vector2(slots[0].x - slots[0].width / 2f, slots[4].y + slots[0].width / 2f)

    fun getCardOnShotTriggerPosition() = Vector2(slots[4].x, slots[4].y + slots[0].height * 2)

    override fun layout() {
        super.layout()
        updateSlotsAndCards()
    }

    private fun updateSlotsAndCards() {
        val drawable = slots[0].drawable ?: return
        val slotSize = drawable.minWidth * slotScale!!
        val size = 2 * radius + slotSize + radiusExtension
        prefWidth = size
        prefHeight = size
        width = prefWidth
        height = prefHeight
        val basePos = Vector2(width / 2, height / 2)
        for (i in slots.indices) {
            val slot = slots[i]
            val angle = angleForIndex(i)
            slot.position(basePos, radius, angle)
            slot.width = slotSize
            slot.height = slotSize
        }
    }

    fun rotate(rotation: RevolverRotation): Timeline = Timeline.timeline {
        when (rotation) {

            is RevolverRotation.Right -> repeat(rotation.amount) {
                action {
                    SoundPlayer.situation("revolver_rotation", screen)
                    rotateRight()
                }
                delayUntil { animFinished() }
                delay(10)
            }

            is RevolverRotation.Left -> repeat(rotation.amount) {
                action {
                    SoundPlayer.situation("revolver_rotation", screen)
                    rotateLeft()
                }
                delayUntil { animFinished() }
                delay(10)
            }

            is RevolverRotation.None -> {}

        }
    }

    private fun rotateRight() {
        val basePos = Vector2(width / 2, height / 2)

        for (i in slots.indices) {
            slots[i].animateTo(basePos, radius, angleForIndex(i), angleForIndex((i + 1) % slots.size))
        }

        val firstCard = slots[0].card
        slots[0].card = slots[1].card
        slots[1].card = slots[2].card
        slots[2].card = slots[3].card
        slots[3].card = slots[4].card
        slots[4].card = firstCard
    }

    private fun animFinished(): Boolean = slots.all { !it.inAnimation }

    private fun rotateLeft() {
        val basePos = Vector2(width / 2, height / 2)

        for (i in slots.indices) {
            slots[i].animateToReversed(basePos, radius, angleForIndex(if (i == 0) 4 else i - 1), angleForIndex(i))
        }

        val firstCard = slots[4].card
        slots[4].card = slots[3].card
        slots[3].card = slots[2].card
        slots[2].card = slots[1].card
        slots[1].card = slots[0].card
        slots[0].card = firstCard
    }

    private fun angleForIndex(i: Int): Double = slotAngleOff * i + rotationOff

    override fun getMinWidth(): Float = prefWidth
    override fun getMinHeight(): Float = prefHeight
    override fun getPrefWidth(): Float = prefWidth
    override fun getPrefHeight(): Float = prefHeight

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

    companion object {
        private const val slotAngleOff: Double = ((2 * Math.PI) / 5)
    }

}

/**
 * a slot of the revolver
 * @param num the number of the slot (1..5)
 * @param revolver the revolver to which this slot belongs
 * @param animationDuration the duration of the spin animation
 */
class RevolverSlot(
    val num: Int,
    val revolver: Revolver,
    drawableHandle: ResourceHandle,
    scale: Float,
    screen: OnjScreen,
    private val animationDuration: Float
) : CustomImageActor(drawableHandle, screen), AnimationActor, KeySelectableActor {

    override var isSelected: Boolean = false

    override var inAnimation: Boolean = false

    override var isHoverDetailActive: Boolean = true

    /**
     * if set to a card, the card will be moved along with the spin animation
     */
    var card: Card? = null

    private var action: RevolverSlotRotationAction? = null
    private var curAngle: Double = 0.0

    init {
        setScale(scale)
        reportDimensionsWithScaling = true
        ignoreScalingWhenDrawing = true
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (inAnimation && action?.isComplete ?: false) {
            removeAction(action)
            inAnimation = false
//            card?.inAnimation = false
            revolver.invalidate()
        }
    }

    /**
     * positions the slot on the screen using the position and the radius of the revolver
     * @param base the position of the middle of the revolver on the screen
     * @param r the radius of the revolver
     * @param angle the angle in radians where this slot should be positioned
     */
    fun position(base: Vector2, r: Float, angle: Double) {
        drawable ?: return
        val slotSize = drawable.minWidth * scaleX
        val dx = cos(angle) * r
        val dy = sin(angle) * r
        setPosition(base.x + dx.toFloat() - slotSize / 2, base.y + dy.toFloat() - slotSize / 2)
        curAngle = angle
        if (card?.actor?.inAnimation ?: true) return
        card?.actor?.setPosition(cardPosition())
    }

    fun cardPosition(): Vector2 {
        val slotSize = drawable.minWidth * scaleX
        val width = card?.actor?.width ?: slotSize
        val height = card?.actor?.height ?: slotSize
        return Vector2(
            x + slotSize / 2 - (width) / 2,
            y + slotSize / 2 - (height) / 2,
        )
    }

    /**
     * animates the slot to an angle
     *  @param base the position of the middle of the revolver on the screen
     *  @param radius the radius of the revolver
     *  @param from the start angle in radians
     *  @param to the end angle in radians
     */
    fun animateTo(base: Vector2, radius: Float, from: Double, to: Double) {
        if (inAnimation) return
        val action = RevolverSlotRotationAction(base, radius, this, from, to)
        action.isReverse = true
        action.duration = animationDuration
        addAction(action)
        inAnimation = true
        this.action = action
    }

    /**
     * animations the slot to an angle, but using the opposite animation of [animateTo]
     *  @param base the position of the middle of the revolver on the screen
     *  @param radius the radius of the revolver
     *  @param from the start angle in radians
     *  @param to the end angle in radians
     */
    fun animateToReversed(base: Vector2, radius: Float, from: Double, to: Double) {
        if (inAnimation) return
        val action = RevolverSlotRotationAction(base, radius, this, from, to)
        action.isReverse = false
        action.duration = animationDuration
        addAction(action)
        inAnimation = true
        this.action = action
    }

    override fun toString(): String {
        return "revolverSlot: $num with card $card"
    }

    override fun getBounds(): Rectangle {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        return if (reportDimensionsWithScaling) {
            Rectangle(x, y, width, height)
        } else {
            Rectangle(x, y, width * scaleX, height * scaleY)
        }
    }

    /**
     * the action used for animating the rotation of the revolver slots
     */
    class RevolverSlotRotationAction(
        val base: Vector2,
        val radius: Float,
        val slot: RevolverSlot,
        val from : Double,
        val to: Double
    ) : TemporalAction() {

        override fun update(percent: Float) {
            val to = if (to < from) to + (2 * Math.PI) else to
            val newAngle = (to - from) * percent + from
            slot.position(base, radius, newAngle)
        }

    }

}
