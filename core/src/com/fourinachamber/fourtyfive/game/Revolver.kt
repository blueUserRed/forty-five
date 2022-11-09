package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.*
import com.fourinachamber.fourtyfive.utils.rotate
import onj.OnjNamedObject

class Revolver : Widget(), ZIndexActor, InitialiseableActor {

    override var fixedZIndex: Int = 0
    var slotTexture: TextureRegion? = null
    var slotFont: BitmapFont? = null
    var fontColor: Color? = null
    var fontScale: Float? = null
    var slotScale: Float? = null
    var cardScale: Float = 1f
    var slotDropConfig: Pair<DragAndDrop, OnjNamedObject>? = null
    private var dirty: Boolean = true
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
     * assigns a card to a slot in the revolver
     */
    fun setCard(slot: Int, card: Card) {
        if (slot !in 1..5) throw RuntimeException("slot must be between between 1 and 5")
        card.isDraggable = false
        cards[slot - 1] = card
        updateCardPositions()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        width = prefWidth
        height = prefHeight
        super.draw(batch, parentAlpha)
        if (dirty) {
            calcOffsets()
            slots = Array(5) {
                val slot = RevolverSlot(it + 1, slotTexture!!, slotFont!!, fontColor!!, slotScale!!)

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
            updateCardPositions()
            dirty = false
        }
    }

    override fun positionChanged() {
        super.positionChanged()
        dirty = true
    }

    private fun updateCardPositions() {
        for (i in cards.indices) if (cards[i] != null) {
            val card = cards[i]!!
            card.actor.setPosition(
                slotOffsets[i].x + x + prefWidth / 2,
                slotOffsets[i].y + y + prefHeight / 2
            )
            card.actor.setScale(cardScale)
        }
        dirty = true
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
        dirty = true
    }

    override fun getMinWidth(): Float = prefWidth
    override fun getMinHeight(): Float = prefHeight
    override fun getPrefWidth(): Float = prefWidth
    override fun getPrefHeight(): Float = prefHeight

}

class RevolverSlot(
    val num: Int,
    private val texture: TextureRegion,
    font: BitmapFont,
    fontColor: Color,
    private val scale: Float
) : CustomImageActor(texture) {
//) : CustomLabel(num.toString(), LabelStyle(font, fontColor), TextureRegionDrawable(texture)) {

    init {
//        setAlignment(Align.center)
        setScale(scale)
    }

//    override fun getWidth(): Float = texture.regionWidth
//    override fun getHeight(): Float = texture.regionHeight
//    override fun setWidth(width: Float) { }
//    override fun setHeight(height: Float) { }

//    override fun getMaxWidth(): Float = slotWidth
//
//    override fun getMaxHeight(): Float = slotHeight
}
