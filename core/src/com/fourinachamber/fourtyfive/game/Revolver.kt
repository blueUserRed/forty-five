package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.*
import com.fourinachamber.fourtyfive.utils.rotate
import ktx.actors.contains
import onj.OnjNamedObject

/**
 * actor representing the revolver
 */
class Revolver : Widget(), ZIndexActor, InitialiseableActor {

    override var fixedZIndex: Int = 0

    /**
     * the texture for a revolver-slot
     */
    var slotTexture: TextureRegion? = null

    var slotFont: BitmapFont? = null
    var fontColor: Color? = null
    var fontScale: Float? = null
    var slotScale: Float? = null

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
    private var dirty: Boolean = true
    private var isInitialised: Boolean = false
    private var prefWidth: Float = 0f
    private var prefHeight: Float = 0f
    private var cards: Array<Card?> = Array(5) { null }
    private lateinit var screenDataProvider: ScreenDataProvider
    private lateinit var slots: Array<RevolverSlot>
    private lateinit var slotOffsets: Array<Vector2>

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
    }

    /**
     * assigns a card to a slot in the revolver; [card] can be set to null, but consider using [removeCard] instead to
     * remove a card
     */
    fun setCard(slot: Int, card: Card?) {
        if (slot !in 1..5) throw RuntimeException("slot must be between between 1 and 5")
        card?.isDraggable = false
        cards[slot - 1] = card
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
        return cards[slot - 1]
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        width = prefWidth
        height = prefHeight
        super.draw(batch, parentAlpha)
        if (!isInitialised) {
            initialise()
            isInitialised = true
        }
        if (dirty) {
            calcOffsets()
            updateSlotPositions()
            updateCardPositions()
            dirty = false
        }
    }

    private fun initialise() {
        calcOffsets()
        slots = Array(5) {
            val slot = RevolverSlot(it + 1, this, slotTexture!!, slotScale!!, animationDuration)

            slot.setPosition(
                slotOffsets[it].x + x + prefWidth / 2,
                slotOffsets[it].y + y + prefHeight / 2
            )

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

    private fun updateSlotPositions() {
        for (i in slots.indices) {
            val slot = slots[i]
            if (slot.inAnimation) continue
            slot.setPosition(
                slotOffsets[i].x + x + prefWidth / 2,
                slotOffsets[i].y + y + prefHeight / 2
            )
        }
    }

    private fun updateCardPositions() {
        for (i in cards.indices) if (cards[i] != null) {
            val card = cards[i]!!
            if (card.inAnimation) continue
            card.actor.setPosition(
                slotOffsets[i].x + x + prefWidth / 2,
                slotOffsets[i].y + y + prefHeight / 2
            )
            card.actor.setScale(cardScale)
        }
    }

    private fun calcOffsets() {
        val slotTexture = slotTexture!!
        val slotScale = slotScale!!
        val slotWidth = slotTexture.regionWidth * slotScale
        val slotHeight = slotTexture.regionHeight * slotScale
        prefHeight = slotHeight * 3.5f
        prefWidth = slotWidth * 4f
        slotOffsets = arrayOf(
            Vector2(slotWidth, 0f),
            Vector2(slotWidth / 2, -slotHeight * 1.5f),
            Vector2(-slotWidth * 1.5f, -slotHeight * 1.5f),
            Vector2(-slotWidth * 2f, 0f),
            Vector2(-slotWidth / 2, slotHeight)
        )
    }

    fun rotate() {
        cards = cards.rotate(-1)
        for (i in slots.indices) {
            val slot = slots[i]
            val nextOffset = (i + 1) % slots.size
            slot.cardToMoveAlong = cards[nextOffset]
            slot.animateTo(
                slotOffsets[nextOffset].x + x + prefWidth / 2,
                slotOffsets[nextOffset].y + y + prefHeight
            )
        }
    }

    fun markDirty() {
        dirty = true
    }

    override fun getMinWidth(): Float = prefWidth
    override fun getMinHeight(): Float = prefHeight
    override fun getPrefWidth(): Float = prefWidth
    override fun getPrefHeight(): Float = prefHeight

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
    private val texture: TextureRegion,
    private val scale: Float,
    private var animationDuration: Float
) : CustomImageActor(texture) {

    /**
     * true if this is in a animation
     */
    var inAnimation: Boolean = false
        private set

    /**
     * if set to a card, the card will be moved along with the spin animation
     */
    var cardToMoveAlong: Card? = null

    private var action: MoveToAction = MoveToAction()

    init {
        setScale(scale)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (inAnimation && action.isComplete) {
            removeAction(action)
            inAnimation = false
            cardToMoveAlong?.inAnimation = false
            revolver.markDirty()
        }
        if (inAnimation) cardToMoveAlong?.actor?.setPosition(x, y)
    }

    /**
     * animates the slot to a [x] [y]
     */
    fun animateTo(x: Float, y: Float) {
        if (inAnimation) return
        action = MoveToAction()
        action.setPosition(x, y)
        action.actor = this
        action.duration = animationDuration
        addAction(action)
        cardToMoveAlong?.inAnimation = true
        inAnimation = true
    }

}
