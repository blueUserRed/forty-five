package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.InitialiseableActor
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.screen.ZIndexActor
import ktx.actors.contains


/**
 * displays the cards
 */
class CardHand : Widget(), ZIndexActor, InitialiseableActor {

    private lateinit var screenDataProvider: ScreenDataProvider

    override var fixedZIndex: Int = 0
    var cardScale: Float = 1.0f
    var cardSpacing: Float = 0.0f
    var cardZIndex: Int = 0
    var draggedCardZIndex: Int = 0

    private var cards: MutableList<Card> = mutableListOf()
    private var neededWidth: Float = 0f
    private var neededHeight: Float = 0f

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
    }

    fun addCard(card: Card) {
        cards.add(card)
        if (card.actor !in screenDataProvider.stage.root) screenDataProvider.addActorToRoot(card.actor)
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

        for (card in cards) {
            if (!card.actor.isDragged) {
                card.actor.setPosition(curX, curY)
                card.actor.setScale(cardScale)
                card.actor.zIndex = cardZIndex
            } else {
                card.actor.zIndex = draggedCardZIndex
            }
            curX += card.actor.width * cardScale + cardSpacing
        }
        screenDataProvider.resortRootZIndices()

        this.neededWidth = neededWidth
        this.neededHeight = cards[0].actor.height * cards[0].actor.scaleY
    }


    override fun getPrefWidth(): Float {
        return neededWidth
    }

    override fun getMinWidth(): Float {
        return neededWidth
    }

    override fun getMinHeight(): Float {
        return neededHeight
    }

    override fun getPrefHeight(): Float {
        return neededHeight
    }
}

class CardActor(val card: Card) : Image(card.texture), ZIndexActor {
    override var fixedZIndex: Int = 0
    var isDragged: Boolean = false
}
