package com.microwavestudios.fortyfive.game.widgets

import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.contains
import kotlin.random.Random

class NewCardHand : ICardHand {

    private var createdActor: CustomGroup? = null

    override val amountOfCards: Int
        get() = cards.size

    override val events: EventPipeline = EventPipeline()

    private var cards: MutableList<Card> = mutableListOf()

    private val addedListenerToCards: MutableList<Card> = mutableListOf()


    override fun allCards(): List<Card> = cards

    override fun addCard(card: Card) {
        require(card !in cards) { "card $card already in card hand" }
        val actor = createdActor
        requireNotNull(actor)
        val cardActor = card.presentation.forceGetActor()
        actor.addActor(cardActor)
        cards.add(card)
        actor.invalidate()
        if (card in addedListenerToCards) return
        addedListenerToCards.add(card)
        cardActor.observeInputState(
            GameInputs.States.focused,
            {
                if (cardActor !in actor) return@observeInputState
                cardActor.fixedZIndex = 100
                actor.resortZIndices()
                cardActor.width = cardSize * 1.13f
                cardActor.height = cardSize * 1.13f
                cardActor.drawOffsetY = 16f
            },
            {
                if (cardActor !in actor) return@observeInputState
                cardActor.fixedZIndex = cards.indexOf(card)
                actor.resortZIndices()
                cardActor.width = cardSize
                cardActor.height = cardSize
                cardActor.drawOffsetY = 0f
            }
        )
    }

    override fun removeCard(card: Card) {
        require(card in cards) { "card $card not in card hand" }
        val actor = createdActor
        requireNotNull(actor)
        val cardActor = card.presentation.forceGetActor()
        cardActor.drawOffsetY = 0f
        actor.removeActor(cardActor)
        cards.remove(card)
    }

    override fun triggerPositionForCardActor(card: CardActor): Vector2 = Vector2(
        card.x,
        card.y + 300f
    )

    fun getActor(creator: ScreenCreator): CustomGroup {
        createdActor?.let { return it }
        with(creator) {
            val actor = createActorWithReceiver()
            createdActor = actor
            return actor
        }
    }

    fun ScreenCreator.createActorWithReceiver(): CustomGroup = newGroup {
        val leftDropOff = 62f
        val minOverlap = 30f
        val heightVariance = 7f
        val gapVariancePercent = 0.3f

        onLayout {
            val random = Random(893324497834)
            val amountCards = cards.size
            val spacePerCard = (width - cardSize) / amountCards
            val cardDistance = spacePerCard.coerceAtMost(cardSize - minOverlap)
            val gapVarianceAbsolute = cardDistance * gapVariancePercent

            var x = 0f

            childrenInCorrectOrderOrOriginal().forEachIndexed { index, child ->
                if (child !is CardActor) return@forEachIndexed
                val usedSpacePercent = x / width
                val yVariance = random.nextFloat() * heightVariance - heightVariance / 2
                val xVariance = random.nextFloat() * gapVarianceAbsolute - gapVarianceAbsolute / 2
                val y = leftDropOff * usedSpacePercent + yVariance
                child.setBounds(x + xVariance, y, cardSize, cardSize)
                child.fixedZIndex = index
                x += cardDistance
            }
        }

        focusShortcut(
            GameInputs.focusShortcutCardHand,
            variableActor = {
                cards.firstOrNull()?.presentation?.forceGetActor()
            }
        )
    }

    companion object {
        const val cardSize = 160f
    }

}