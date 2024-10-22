package com.fourinachamber.fortyfive.screen.gameWidgets

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.FocusChangeEvent
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.EventPipeline
import kotlin.math.pow

class NewCardHand(
    screen: OnjScreen,
    private val centerGap: Float,
    private val cardSize: Float,
    private val maxDistanceBetweenCards: Float,
) : CustomGroup(screen) {

    private val leftSide: MutableList<Card> = mutableListOf()
    private val rightSide: MutableList<Card> = mutableListOf()

    val events: EventPipeline = EventPipeline()

    private val cardFocusListener: EventListener = object : EventListener {

        override fun handle(event: Event?): Boolean {
            if (event !is FocusChangeEvent) return false
            forAllCards { card ->
                val actor = card.actor
                if (actor === event.new) {
                    actor.rotation = 0f
                    actor.y = actor.y.coerceIn(0f, height)
                    actor.width = cardSize * 1.4f
                    actor.height = cardSize * 1.4f
                    actor.fixedZIndex = 100
                    resortZIndices()
                } else if (actor === event.old) {
                    actor.y = cardHeightFunc(actor.x)
                    actor.rotation = cardHeightFuncDerivative(actor.x) * 50f
                    actor.width = cardSize
                    actor.height = cardSize
                    actor.fixedZIndex = zIndexFor(card)
                    resortZIndices()
                }
            }
            return true
        }
    }

    init {
        addListener(cardFocusListener)
    }

    fun allCards(): List<Card> = leftSide + rightSide

    fun addCard(card: Card) {
        if (leftSide.size < rightSide.size) leftSide.add(card)
        else rightSide.add(card)
        val actor = card.actor
        addActor(actor)
        invalidate()
        actor.group = cardFocusGroupName
        actor.isFocusable = true
        actor.isSelectable = true
        actor.fixedZIndex = zIndexFor(card)
        resortZIndices()
        actor.targetGroups = listOf(RevolverSlot.revolverSlotFocusGroupName)
        actor.bindDragging(actor, screen)
        actor.makeDraggable(actor)
        actor.resetCondition = { true }
        actor.onDragAndDrop.add { _, target ->
            target as? RevolverSlot ?: return@add
            events.fire(CardDraggedOntoSlotEvent(card, target))
        }
    }

    private fun zIndexFor(card: Card): Int {
        var zIndex = leftSide.indexOf(card)
        if (zIndex == -1) zIndex = 50 - rightSide.indexOf(card)
        return zIndex
    }

    fun removeCard(card: Card) {
        if (card in leftSide) {
            leftSide.remove(card)
        } else if (card in rightSide) {
            rightSide.remove(card)
        } else {
            throw RuntimeException("card $card can't be removed because it is not the cardHand")
        }
        removeActor(card.actor)
        evenOutCards()
        invalidate()
    }

    private fun evenOutCards() {

    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
    }

    override fun layout() {
        super.layout()
        val widthPerSide = (width - centerGap) / 2f

        var x = 0f

        val cardDistLeftSide = (widthPerSide / leftSide.size).coerceAtMost(maxDistanceBetweenCards)
        x = width / 2 - centerGap / 2 - leftSide.size * cardDistLeftSide - (cardSize - cardDistLeftSide)
        leftSide.forEach { card ->
            val actor = card.actor
            actor.setBounds(x, cardHeightFunc(x), cardSize, cardSize)
            actor.rotation = cardHeightFuncDerivative(x) * 50f
            x += cardDistLeftSide
        }

        val cardDistRightSide = (widthPerSide / rightSide.size).coerceAtMost(maxDistanceBetweenCards)
        x = width / 2 + centerGap / 2
        rightSide.forEach { card ->
            val actor = card.actor
            actor.setBounds(x, cardHeightFunc(x), cardSize, cardSize)
            actor.rotation = cardHeightFuncDerivative(x) * 50f
            x += cardDistRightSide
        }
    }

    private fun cardHeightFuncDerivative(x: Float): Float = 0.16f - 0.0002f * x

    private fun cardHeightFunc(x: Float): Float = -(0.008f * (x - 800f)).pow(2)

    private inline fun forAllCards(block: (Card) -> Unit) {
        leftSide.forEach { block(it) }
        rightSide.forEach { block(it) }
    }

    data class CardDraggedOntoSlotEvent(val card: Card, val slot: RevolverSlot)

    companion object {
        const val cardFocusGroupName = "cardInCardHand"
    }

}
