package com.microwavestudios.fortyfive.game.widgets

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.contains
import kotlin.math.pow

class CardHand(
    screen: CustomScreen,
    private val centerGap: Float,
    private val cardSize: Float,
    private val maxDistanceBetweenCards: Float,
) : CustomGroup(screen) {

    private val leftSide: MutableList<Card> = mutableListOf()
    private val rightSide: MutableList<Card> = mutableListOf()

    val amountOfCards: Int
        get() = leftSide.size + rightSide.size

    val events: EventPipeline = EventPipeline()

    private val addedListenersToCards: MutableList<Card> = mutableListOf()

    private var orderedChildrenDirty: Boolean = true
    private var childrenInCorrectOrderCache: MutableList<Actor> = mutableListOf()

    init {
        focusShortcut(
            GameInputs.focusShortcutCardHand,
            variableActor = { leftSide.lastOrNull()?.actor ?: rightSide.firstOrNull()?.actor }
        )
    }

    fun allCards(): List<Card> = leftSide + rightSide

    fun addCard(card: Card) {
        orderedChildrenDirty = true
        if (leftSide.size < rightSide.size) leftSide.add(card)
        else rightSide.add(card)
        val actor = card.actor
        addActor(actor)
        actor.fixedZIndex = zIndexFor(card)
        resortZIndices()
        if (card !in addedListenersToCards) {
            card.actor.observeInputState(
                GameInputs.States.focused,
                {
                    if (card.actor !in this) return@observeInputState
                    actor.width = cardSize * 1.2f
                    actor.height = cardSize * 1.2f
                    actor.fixedZIndex = 100
                    resortZIndices()
                },
                {
                    if (card.actor !in this) return@observeInputState
                    actor.width = cardSize
                    actor.height = cardSize
                    actor.fixedZIndex = zIndexFor(card)
                    resortZIndices()
                }
            )
            addedListenersToCards.add(card)
        }
        layout() // layout added card immediately to make animations work
    }

    override fun childrenInCorrectOrder(): List<Actor>? {
        if (!orderedChildrenDirty) return childrenInCorrectOrderCache
        val new = mutableListOf<Actor>()
        var i = leftSide.size - 1
        while (i >= 0) {
            new.add(leftSide[i].actor)
            i--
        }
        new.addAll(rightSide.map { it.actor })
        childrenInCorrectOrderCache = new
        orderedChildrenDirty = false
        return new
    }

    private fun zIndexFor(card: Card): Int {
        var zIndex = leftSide.indexOf(card)
        if (zIndex == -1) zIndex = rightSide.indexOf(card)
        return 50 - zIndex
    }

    fun removeCard(card: Card) {
        orderedChildrenDirty = true
        when (card) {
            in leftSide -> leftSide.remove(card)
            in rightSide -> rightSide.remove(card)
            else -> throw RuntimeException("card $card can't be removed because it is not the cardHand")
        }
        removeActor(card.actor)
        card.actor.rotation = 0f
        evenOutCards()
        invalidate()
    }

    fun triggerPositionForCardActor(card: CardActor): Vector2 {
        val handMiddle = x + width / 2
        val extendedGap = centerGap + 330
        val isLeft = card.x < handMiddle
        var x = card.x
        if (isLeft) x += card.width
        if (isLeft) {
            if (x > handMiddle - extendedGap / 2) x = handMiddle - extendedGap / 2
        } else {
            if (x < handMiddle + extendedGap / 2) x = handMiddle + extendedGap / 2
        }
        if (isLeft) x -= card.width
        val y = card.y + 300
        return Vector2(x, y)
    }

    private fun evenOutCards() {
        // philip said cards shouldn't jump between sides
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
    }

    override fun layout() {
        super.layout()
        val widthPerSide = (width - centerGap) / 2f

        var x = 0f

        val cardDistLeftSide = (widthPerSide / (leftSide.size + 1)).coerceAtMost(maxDistanceBetweenCards)
        x = width / 2 - centerGap / 2 - cardSize
        leftSide.forEach { card ->
            val actor = card.actor
            actor.setBounds(x, cardHeightFunc(x), cardSize, cardSize)
            actor.rotation = cardHeightFuncDerivative(x) * 50f
            card.actor.fixedZIndex = zIndexFor(card)
            x -= cardDistLeftSide
        }

        val cardDistRightSide = (widthPerSide / (rightSide.size + 1)).coerceAtMost(maxDistanceBetweenCards)
        x = width / 2 + centerGap / 2
        rightSide.forEach { card ->
            val actor = card.actor
            actor.setBounds(x, cardHeightFunc(x), cardSize, cardSize)
            actor.rotation = cardHeightFuncDerivative(x) * 50f
            card.actor.fixedZIndex = zIndexFor(card)
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
