package com.fourinachamber.fortyfive.game.controller

import com.fourinachamber.fortyfive.game.card.Card

class CardStack(
    private var cards: MutableList<Card>
) {

    private var dirty: Boolean = true

    fun addCardAtBottom(card: Card) {
        cards.add(card)
        dirty()
    }

    fun set(cards: MutableList<Card>) {
        this.cards = cards
        dirty()
    }

    fun drawCard(): Card? {
        ensureOrder()
        return cards.firstOrNull()
    }

    fun drawCardFromBottom(): Card? {
        ensureOrder()
        return cards.lastOrNull()
    }

    fun drawCard(fromBottom: Boolean): Card? = if (fromBottom) drawCardFromBottom() else drawCard()

    inline fun drawCardOr(alternative: () -> Card): Card = drawCard() ?: alternative()

    inline fun drawCardFromBottomOr(alternative: () -> Card): Card = drawCardFromBottom() ?: alternative()

    inline fun drawCardOr(fromBottom: Boolean, alternative: () -> Card): Card = drawCard(fromBottom) ?: alternative()

    fun remove(card: Card) {
        val removed = cards.remove(card)
        if (!removed) {
            throw RuntimeException("cant remove card $card because it wasnt in the stack")
        }
    }

    fun cards(): List<Card> {
        ensureOrder()
        return cards
    }

    fun isEmpty(): Boolean = cards.isEmpty()

    fun size(): Int = cards.size

    fun shuffle() {
        cards.shuffle()
        dirty()
    }

    fun dirty() {
        dirty = true
    }

    private fun ensureOrder() {
        if (!dirty) return
        val bottom = mutableListOf<Card>()
        val normal = mutableListOf<Card>()
        val top = mutableListOf<Card>()
        cards.forEach { card ->
            when (card.stackPosition) {
                Card.StackPosition.TOP -> top.add(card)
                Card.StackPosition.BOTTOM -> bottom.add(card)
                Card.StackPosition.NORMAL -> normal.add(card)
            }
        }
        val combined = ArrayList<Card>(normal.size + top.size + bottom.size) as MutableList<Card>
        combined.addAll(top)
        combined.addAll(normal)
        combined.addAll(bottom)
        cards = combined
        dirty = false
    }

}