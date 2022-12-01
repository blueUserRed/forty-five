package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.*
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import com.fourinachamber.fourtyfive.utils.plus
import ktx.actors.contains
import onj.OnjNamedObject
import java.lang.Math.cos
import java.lang.Math.sin

/**
 * actor representing the revolver
 */
class Revolver(
    detailFont: BitmapFont,
    detailFontColor: Color,
    detailBackground: Drawable,
    detailFontScale: Float,
    val detailOffset: Vector2,
    val detailWidth: Float,
    private val background: Drawable?,
    private val radiusExtension: Float
) : Widget(), ZIndexActor, InitialiseableActor {

    override var fixedZIndex: Int = 0

    /**
     * the texture for a revolver-slot
     */
    var slotTexture: TextureRegion? = null

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
     * the [DragAndDrop] used for the slots and the [OnjNamedObject] containing the config for drag and drop
     */
    var slotDropConfig: Pair<DragAndDrop, OnjNamedObject>? = null

    /**
     * the radius of the circle in which the slots are laid out
     */
    var radius: Float = 1f

    /**
     * the rotation of all slots is offset by this
     */
    var rotationOff: Double = (Math.PI / 2) + slotAngleOff

    private var dirty: Boolean = true
    private var isInitialised: Boolean = false
    private var prefWidth: Float = 0f
    private var prefHeight: Float = 0f
    private lateinit var screenDataProvider: ScreenDataProvider

    lateinit var slots: Array<RevolverSlot>
        private set

    private val hoverDetailActor: CustomLabel =
        CustomLabel("", Label.LabelStyle(detailFont, detailFontColor), detailBackground)

    init {
        hoverDetailActor.setFontScale(detailFontScale)
        hoverDetailActor.setAlignment(Align.center)
        hoverDetailActor.isVisible = false
        hoverDetailActor.fixedZIndex = Int.MAX_VALUE
        hoverDetailActor.wrap = true
    }

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
        screenDataProvider.addActorToRoot(hoverDetailActor)
    }

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
        dirty = true
    }

    /**
     * removes a card from the revolver
     */
    fun removeCard(slot: Int) {
        if (slot !in 1..5) throw RuntimeException("slot must be between between 1 and 5")
        val card = getCardInSlot(slot) ?: return
        if (card.actor in stage.root) screenDataProvider.removeActorFromRoot(card.actor)
        setCard(slot, null)
    }

    /**
     * @return the card in [slot]
     */
    fun getCardInSlot(slot: Int): Card? {
        if (slot !in 1..5) throw RuntimeException("slot must be between between 1 and 5")
        return slots[slot - 1].card
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        width = prefWidth
        height = prefHeight
        background?.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)
        if (!isInitialised) {
            initialise()
            updateSlotsAndCars()
            isInitialised = true
            invalidateHierarchy()
        }
        if (dirty) {
            updateSlotsAndCars()
            dirty = false
        }

        var isCardHoveredOver = false
        for (slot in slots) if (slot.card?.actor?.isHoveredOver ?: false) {
            isCardHoveredOver = true
            updateHoverDetailActor(slot.card!!)
        }
        hoverDetailActor.isVisible = isCardHoveredOver
    }

    private fun updateHoverDetailActor(card: Card) {
        hoverDetailActor.setText(card.description)

        hoverDetailActor.width = detailWidth
        hoverDetailActor.height = hoverDetailActor.prefHeight

        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))

        hoverDetailActor.setPosition(
            x + prefWidth + detailOffset.x,
            y + prefHeight / 2 - hoverDetailActor.height / 2 + detailOffset.y
        )
    }

    private fun initialise() {
        slots = Array(5) {
            val slot = RevolverSlot(it + 1, this, slotTexture!!, slotScale!!, animationDuration)

            screenDataProvider.addActorToRoot(slot)

            if (slotDropConfig != null) {
                val (dragAndDrop, dropOnj) = slotDropConfig!!
                val dropBehaviour = DragAndDropBehaviourFactory.dropBehaviourOrError(
                    dropOnj.name,
                    dragAndDrop,
                    screenDataProvider,
                    slot,
                    dropOnj
                )
                dragAndDrop.addTarget(dropBehaviour)
            }
            slot
        }
    }

    override fun positionChanged() {
        super.positionChanged()
        dirty = true
    }

    private fun updateSlotsAndCars() {
        val slotSize = slotTexture!!.regionWidth * slotScale!!
        val size = 2 * radius + slotSize + radiusExtension
        prefWidth = size
        prefHeight = size
        width = prefWidth
        height = prefHeight
        val basePos = localToStageCoordinates(Vector2(0f, 0f)) + Vector2(width / 2, height / 2)
        for (i in slots.indices) {
            val slot = slots[i]
            val angle = angleForIndex(i)
            slot.position(basePos, radius, angle)
        }
    }

    fun rotate() {
        val basePos = localToStageCoordinates(Vector2(0f, 0f)) + Vector2(width / 2, height / 2)

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

    fun rotateLeft() {
        val basePos = localToStageCoordinates(Vector2(0f, 0f)) + Vector2(width / 2, height / 2)

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

    fun markDirty() {
        dirty = true
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
 * @param texture the texture for this slot
 * @param animationDuration the duration of the spin animation
 */
class RevolverSlot(
    val num: Int,
    val revolver: Revolver,
    private val textureRegion: TextureRegion,
    private val scale: Float,
    private var animationDuration: Float
) : CustomImageActor(textureRegion), AnimationActor {

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
            revolver.markDirty()
        }
    }

    fun position(base: Vector2, r: Float, angle: Double) {
        val slotSize = texture.regionWidth * scaleX
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
