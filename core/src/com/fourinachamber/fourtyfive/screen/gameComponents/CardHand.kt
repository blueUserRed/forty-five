package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.game.card.CardActor
import com.fourinachamber.fourtyfive.screen.general.ZIndexActor
import com.fourinachamber.fourtyfive.screen.general.ZIndexGroup
import com.fourinachamber.fourtyfive.utils.between
import ktx.actors.contains
import kotlin.math.min


/**
 * displays the cards in the hand
 * @param targetWidth the width this aims to be
 */
class CardHand(
    private val targetWidth: Float
) : WidgetGroup(), ZIndexActor, ZIndexGroup {

    override var fixedZIndex: Int = 0

    /**
     * the scale of the card-Actors
     */
    var cardScale: Float = 1.0f

    /**
     * scaling applied to the card when hovered over
     */
    var hoveredCardScale = 1.0f

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

    /**
     * the cards currently in the hand
     */
    val cards: List<Card>
        get() = _cards

    private var _cards: MutableList<Card> = mutableListOf()
    private var currentWidth: Float = 0f

    private var currentHoverDetailActor: CardDetailActor? = null

    /**
     * adds a card to the hand
     */
    fun addCard(card: Card) {
        _cards.add(card)
        if (card.actor !in this) addActor(card.actor)
        updateCards()
        invalidateHierarchy()
    }

    /**
     * removes a card from the hand
     */
    fun removeCard(card: Card) {
        _cards.remove(card)
        removeActor(card.actor)
        updateCards()
        invalidateHierarchy()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        updateCards()
    }

    private fun updateCards() {

        fun doZIndexFor(card: CardActor, zIndex: Int) {
            if (card.fixedZIndex == zIndex) return
            card.fixedZIndex = zIndex
            invalidate()
        }

        if (_cards.isEmpty()) return

        val isCardHoveredOver = _cards.find { it.actor.isHoveredOver } != null

        val detailCard: Card? = _cards.find {
            if (it.actor.isDragged) return@find false
            if (it.actor.isHoveredOver) return@find true
            if (!isCardHoveredOver && it.actor.isSelected) return@find true
            return@find false
        }

        _cards.forEachIndexed { i, card ->
            if (card.actor.isDragged) {
                card.actor.setScale(cardScale)
                doZIndexFor(card.actor, draggedCardZIndex)
            } else if (card === detailCard) {
                detailCard.actor.setScale(hoveredCardScale)
                doZIndexFor(card.actor, hoveredCardZIndex)
            } else {
                if (card.actor.isSelected) FourtyFive.currentGame!!.keySelectedCard = null
                card.actor.setScale(cardScale)
                doZIndexFor(card.actor, startCardZIndicesAt + i)
            }
        }

        if (detailCard != null) {
            if (currentHoverDetailActor === detailCard.actor.hoverDetailActor) return
            currentHoverDetailActor?.isVisible = false
            removeActor(currentHoverDetailActor)
            currentHoverDetailActor = detailCard.actor.hoverDetailActor
            addActor(currentHoverDetailActor)
            currentHoverDetailActor!!.isVisible = true
            invalidate()
            return
        }
        if (currentHoverDetailActor != null) {
            currentHoverDetailActor?.isVisible = false
            removeActor(currentHoverDetailActor)
            currentHoverDetailActor = null
            invalidate()
        }
    }

    override fun layout() {
        super.layout()

        if (_cards.isEmpty()) return

        val targetWidth = width

        val cardWidth = _cards[0].actor.width * cardScale
        val hoveredCardWidth = _cards[0].actor.width * hoveredCardScale

        var neededWidth = _cards.size * (cardSpacing + cardWidth) - cardSpacing

        val xDistanceOffset = if (targetWidth < neededWidth) {
            -(neededWidth - targetWidth + cardWidth) / _cards.size
        } else 0f

        neededWidth = _cards.size * (xDistanceOffset + cardSpacing + cardWidth) - cardSpacing - xDistanceOffset

        var curX = if (targetWidth > neededWidth) {
            ((width - neededWidth) / 2)
        } else 0f

        val curY = 0f

        for (i in _cards.indices) {
            val card = _cards[i]
            if (card.actor.isDragged) {
                curX += cardWidth + cardSpacing + xDistanceOffset
                continue
            }
            if (currentHoverDetailActor == card.actor.hoverDetailActor) {
                val detailActor = currentHoverDetailActor!!
                val detailWidth = hoveredCardWidth * 2
                detailActor.forcedWidth = detailWidth
                detailActor.fixedZIndex = hoveredCardZIndex
                val adjustedX = curX - (hoveredCardWidth - cardWidth) / 2
                detailActor.setBounds(
                    (adjustedX - detailWidth / 4).between(-detailWidth, width - detailWidth),
                    curY + hoveredCardWidth,
                    detailActor.prefWidth,
                    detailActor.prefHeight
                )
                card.actor.setPosition(adjustedX, curY)
            } else {
                card.actor.setPosition(curX, curY)
            }
            curX += cardWidth + cardSpacing + xDistanceOffset
        }
        resortZIndices()
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    override fun getPrefWidth(): Float {
        return min(targetWidth, currentWidth)
    }

    override fun getMinWidth(): Float {
        return min(targetWidth, currentWidth)
    }

}