package com.fourinachamber.fortyfive.screen.gameWidgets

import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.onFocusChange
import com.fourinachamber.fortyfive.screen.general.onSelect
import com.fourinachamber.fortyfive.screen.general.onSelectChange
import kotlin.math.pow

class NewCardHand(
    screen: OnjScreen,
    private val centerGap: Float,
    private val cardSize: Float,
    private val maxDistanceBetweenCards: Float,
) : CustomGroup(screen) {

    private val leftSide: MutableList<Card> = mutableListOf()
    private val rightSide: MutableList<Card> = mutableListOf()

    fun addCard(card: Card) {
        if (leftSide.size < rightSide.size) leftSide.add(card)
        else rightSide.add(card)
        addActor(card.actor)
        invalidate()
        card.actor.group = cardFocusGroupName
        card.actor.onFocusChange { _, _ -> println(card.name) }
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

    companion object {
        const val cardFocusGroupName = "cardInCardHand"
    }

}
