package com.microwavestudios.fortyfive.testing.mockcomponents

import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.widgets.ICardHand
import com.microwavestudios.fortyfive.utils.EventPipeline

class MockCardHand(
    override val events: EventPipeline
) : ICardHand {

    private val cards: MutableList<Card> = mutableListOf()

    override val amountOfCards: Int
        get() = cards.size

    override fun allCards(): List<Card> = cards

    override fun addCard(card: Card) {
        cards.add(card)
    }

    override fun removeCard(card: Card) {
        cards.remove(card)
    }

    override fun triggerPositionForCardActor(card: CardActor): Vector2 = Vector2(0f, 0f)
}
