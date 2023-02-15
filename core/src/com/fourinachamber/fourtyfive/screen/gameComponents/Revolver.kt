package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.screen.general.*
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
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
    private val background: Drawable?,
    private val radiusExtension: Float,
    private val screen: OnjScreen
) : WidgetGroup(), ZIndexActor {

    override var fixedZIndex: Int = 0

    /**
     * the texture for a revolver-slot
     */
    var slotDrawable: Drawable? = null

    var slotFont: BitmapFont? = null
    var fontColor: Color? = null
    var fontScale: Float? = null
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

    private var currentHoverDetailActor: CardDetailActor? = null

    /**
     * assigns a card to a slot in the revolver; [card] can be set to null, but consider using [removeCard] instead to
     * remove a card
     */
    fun setCard(slot: Int, card: Card?) {
        if (slot !in 1..5) throw RuntimeException("slot must be between between 1 and 5")
        card?.isDraggable = false
        slots[slot - 1].card = card
        card?.actor?.setScale(cardScale)
        card?.actor?.fixedZIndex = cardZIndex
        if (card != null && card.actor !in this) addActor(card.actor)
        invalidate()
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

    fun initDragAndDrop(config:  Pair<DragAndDrop, OnjNamedObject>) {
        slots = Array(5) {
            val slot = RevolverSlot(it + 1, this, slotDrawable!!, slotScale!!, animationDuration)
            addActor(slot)
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
        background?.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)

        var isCardHoveredOver = false
        for (slot in slots) if (slot.card?.actor?.isHoveredOver ?: false) {
            isCardHoveredOver = true
            if (currentHoverDetailActor === slot.card?.actor?.hoverDetailActor) break
            currentHoverDetailActor?.isVisible = false
            removeActor(currentHoverDetailActor)
            currentHoverDetailActor = slot.card?.actor?.hoverDetailActor
            addActor(currentHoverDetailActor)
            currentHoverDetailActor!!.isVisible = true
            invalidate()
            break
        }
        if (!isCardHoveredOver && currentHoverDetailActor != null) {
            currentHoverDetailActor?.isVisible = false
            removeActor(currentHoverDetailActor)
            currentHoverDetailActor = null
            invalidate()
        }
    }

    override fun layout() {
        super.layout()
        currentHoverDetailActor?.forcedWidth = width
        currentHoverDetailActor?.setBounds(
            width / 2 - currentHoverDetailActor!!.forcedWidth / 2,
            height,
            currentHoverDetailActor!!.prefWidth,
            currentHoverDetailActor!!.prefHeight
        )
        updateSlotsAndCards()
    }

    private fun updateSlotsAndCards() {
        val slotSize = slotDrawable!!.minWidth * slotScale!!
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
        }
    }

    /**
     * rotates the revolver to the right
     */
    fun rotate() {
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

    /**
     * rotates the revolver to the left
     */
    fun rotateLeft() {
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
    drawable: Drawable,
    scale: Float,
    private val animationDuration: Float
) : CustomImageActor(drawable), AnimationActor, KeySelectableActor {

    override var isSelected: Boolean = false

    override var inAnimation: Boolean = false

    /**
     * if set to a card, the card will be moved along with the spin animation
     */
    var card: Card? = null

    private var action: RevolverSlotRotationAction? = null
    private var curAngle: Double = 0.0

    init {
        setScale(scale)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (inAnimation && action?.isComplete ?: false) {
            removeAction(action)
            inAnimation = false
            card?.inAnimation = false
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
        val slotSize = drawable.minWidth * scaleX
        val dx = cos(angle) * r
        val dy = sin(angle) * r
        setPosition(base.x + dx.toFloat() - slotSize / 2, base.y + dy.toFloat() - slotSize / 2)
        curAngle = angle
        card?.actor?.let {
            it.setPosition(
                x + slotSize / 2 - (it.width * it.scaleX) / 2,
                y + slotSize / 2 - (it.height * it.scaleY) / 2
            )
        }
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
        card?.inAnimation = true
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
        card?.inAnimation = true
        inAnimation = true
        this.action = action
    }

    override fun toString(): String {
        return "revolverSlot: $num with card $card"
    }

    override fun getHighlightArea(): Rectangle {
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
