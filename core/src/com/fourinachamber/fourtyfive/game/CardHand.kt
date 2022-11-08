package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.CustomLabel
import com.fourinachamber.fourtyfive.screen.InitialiseableActor
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.screen.ZIndexActor
import com.fourinachamber.fourtyfive.utils.between
import ktx.actors.contains
import ktx.actors.onEnter
import ktx.actors.onExit
import kotlin.math.min


/**
 * displays the cards
 */
class CardHand(
    detailFont: BitmapFont,
    detailFontColor: Color,
    detailBackground: Drawable,
    detailFontScale: Float,
    val detailOffset: Vector2,
    val hoverDetailPadding: Float
) : Widget(), ZIndexActor, InitialiseableActor {

    private lateinit var screenDataProvider: ScreenDataProvider

    override var fixedZIndex: Int = 0

    /**
     * the scale of the card-Actors
     */
    var cardScale: Float = 1.0f

    /**
     * the spacing between the cards
     */
    var cardSpacing: Float = 0.0f

    /**
     * the z-index of any card in the hand that is not hovered over
     * will be startCardZIndicesAt + the number of the card
     */
    var startCardZIndicesAt: Int = 0

    /**
     * the z-index of a card that is hovered over
     */
    var hoveredCardZIndex: Int = 0

    /**
     * the z-index of the card-actors while dragged
     */
    var draggedCardZIndex: Int = 0

    private var cards: MutableList<Card> = mutableListOf()
    private var currentWidth: Float = 0f
    private var currentHeight: Float = 0f

    private var hoverDetailActor: CustomLabel

    init {
        hoverDetailActor = CustomLabel("", Label.LabelStyle(detailFont, detailFontColor), detailBackground)
        hoverDetailActor.setFontScale(detailFontScale)
        hoverDetailActor.setAlignment(Align.center)
    }

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
//        screenDataProvider.addActorToRoot(hoverDetailActor)
    }

    /**
     * adds a card to the hand
     */
    fun addCard(card: Card) {
        cards.add(card)
        if (card.actor !in screenDataProvider.stage.root) screenDataProvider.addActorToRoot(card.actor)
        updateCards()
    }

    /**
     * removes a card from the hand (will not be removed from the stage)
     */
    fun removeCard(card: Card) {
        cards.remove(card)
//        screenDataProvider.removeActorFromRoot(card.actor)
        updateCards()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        updateCards() //TODO: calling this every frame is unnecessary
        if (hoverDetailActor.isVisible)
            hoverDetailActor.draw(screenDataProvider.stage.batch, 1.0f)
    }

    private fun updateCards() {

        if (cards.isEmpty()) return

        val cardWidth = cards[0].actor.width * cardScale

        var neededWidth = cards.size * (cardSpacing + cardWidth) - cardSpacing
        this.currentWidth = neededWidth

        val xDistanceOffset = if (width < neededWidth) {
            -(neededWidth - width + cardWidth) / cards.size
        } else 0f

        neededWidth = cards.size * (xDistanceOffset + cardSpacing + cardWidth) - cardSpacing - xDistanceOffset

        var curX = if (width > neededWidth) {
            x + ((width - neededWidth) / 2)
        } else x
        val curY = y

        var isCardHoveredOver = false
        for (i in cards.indices) {
            val card = cards[i]
            if (!card.actor.isDragged) {
                card.actor.setPosition(curX, curY)
                card.actor.setScale(cardScale)

                if (card.actor.isHoveredOver) {
                    isCardHoveredOver = true
                    displayHoverDetail(card)
                    card.actor.fixedZIndex = hoveredCardZIndex
                } else {
                    card.actor.fixedZIndex = startCardZIndicesAt + i
                }

            } else {
                card.actor.fixedZIndex = draggedCardZIndex
            }
            curX += cardWidth + cardSpacing + xDistanceOffset
        }

        if (!isCardHoveredOver && hoverDetailActor.isVisible) hideHoverDetailActor()

        screenDataProvider.resortRootZIndices()

//        this.currentWidth = neededWidth
        this.currentHeight = cards[0].actor.height * cardScale
    }

    private fun displayHoverDetail(card: Card) {

        hoverDetailActor.width = hoverDetailActor.prefWidth + hoverDetailPadding * 2
        hoverDetailActor.height = hoverDetailActor.prefHeight + hoverDetailPadding * 2

        hoverDetailActor.setText(card.description)

        val worldWidth = screenDataProvider.stage.viewport.worldWidth

        hoverDetailActor.setPosition(
            (card.actor.x + detailOffset.x - hoverDetailActor.width / 2 + (card.actor.width * cardScale) / 2)
                .between(0f, worldWidth - hoverDetailActor.width),
            card.actor.y + card.actor.height * cardScale + detailOffset.y
        )
        hoverDetailActor.isVisible = true
    }

    private fun hideHoverDetailActor() {
        hoverDetailActor.isVisible = false
    }

    override fun getPrefWidth(): Float {
//        return neededWidth
        return min(screenDataProvider.stage.viewport.worldWidth, currentWidth)
    }

    override fun getMinWidth(): Float {
//        return neededWidth
        return min(screenDataProvider.stage.viewport.worldWidth, currentWidth)
    }

    override fun getMinHeight(): Float {
        return currentHeight
    }

    override fun getPrefHeight(): Float {
        return currentHeight
    }
}
