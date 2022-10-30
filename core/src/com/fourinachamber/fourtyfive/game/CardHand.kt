package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.InitialiseableActor
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.screen.ZIndexActor
import ktx.actors.contains
import ktx.actors.onEnter
import ktx.actors.onExit
import kotlin.math.min


/**
 * displays the cards
 */
class CardHand : Widget(), ZIndexActor, InitialiseableActor {

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
    private var neededWidth: Float = 0f
    private var neededHeight: Float = 0f

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
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
        updateCards()
    }

    private fun updateCards() {

        if (cards.isEmpty()) return
        val neededWidth = cards.size * (cardSpacing + (cards[0].actor.width * cards[0].actor.scaleX))

        var curX = if (width <= neededWidth) x else x + ((width - neededWidth) / 2)
        val curY = y

        val xDistanceOffset = if (width < neededWidth) {
            -(neededWidth - width) / cards.size
        } else 0f

        for (i in cards.indices) {
            val card = cards[i]
            if (!card.actor.isDragged) {
                card.actor.setPosition(curX, curY)
                card.actor.setScale(cardScale)
                card.actor.fixedZIndex = if (card.actor.isHoveredOver) hoveredCardZIndex else startCardZIndicesAt + i
            } else {
                card.actor.fixedZIndex = draggedCardZIndex
            }
            curX += card.actor.width * cardScale + cardSpacing + xDistanceOffset
        }
        screenDataProvider.resortRootZIndices()

        this.neededWidth = neededWidth
        this.neededHeight = cards[0].actor.height * cards[0].actor.scaleY
    }


    override fun getPrefWidth(): Float {
//        return neededWidth
        return min(screenDataProvider.stage.viewport.worldWidth, neededWidth)
    }

    override fun getMinWidth(): Float {
//        return neededWidth
        return min(screenDataProvider.stage.viewport.worldWidth, neededWidth)
    }

    override fun getMinHeight(): Float {
        return neededHeight
    }

    override fun getPrefHeight(): Float {
        return neededHeight
    }
}

/**
 * the actor representing a card
 */
class CardActor(val card: Card) : Image(card.texture), ZIndexActor {
    override var fixedZIndex: Int = 0

    /**
     * true when the card is dragged; set by [CardDragSource][com.fourinachamber.fourtyfive.card.CardDragSource]
     */
    var isDragged: Boolean = false

    /**
     * true when the actor is hovered over
     */
    var isHoveredOver: Boolean = false
        private set

    init {
        onEnter { isHoveredOver = true }
        onExit { isHoveredOver = false }
    }
}
