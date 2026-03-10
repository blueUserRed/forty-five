package com.microwavestudios.fortyfive.game

import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.card.RandomCardSelection
import com.microwavestudios.fortyfive.utils.iterateRemoving
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjObject

class Deck(var name: String, val id: Int, private val _cardPositions: MutableMap<Int, CardType>) {

    var deckDirty: Boolean = true
        private set

    val cardPositions: Map<Int, CardType>
        get() = _cardPositions

    val cards: List<CardType>
        get() = _cardPositions.map { it.value }


    fun resetDeckDirty() {
        deckDirty = false
    }

    private fun dirty() {
        deckDirty = true
    }

    fun checkDeck(availableCards: List<CardType>) {
        val availableCards = availableCards.toMutableList()
        val allCardProtos = RandomCardSelection.allCardPrototypes

        var numberOfCards = 0
        val cardAmounts = mutableMapOf<String, Int>()
        _cardPositions.iterateRemoving { value, remover ->
            val (_, card) = value
            val maxAmount = allCardProtos.find { it.name == card.name }?.deckMaximum
            requireNotNull(maxAmount) { "unknown card in deck: ${card.name}" }
            cardAmounts.putIfAbsent(card.name, 0)
            val available = availableCards.find { it == card }
            if (
                (maxAmount != -1 && cardAmounts[card.name]!! >= maxAmount) ||
                available == null ||
                numberOfCards >= numberOfSlots
            ) {
                remover()
                return@iterateRemoving
            }
            numberOfCards++
            availableCards.remove(available)
            cardAmounts[card.name] = cardAmounts[card.name]!! + 1
        }

        if (_cardPositions.size >= minDeckSize) return

        availableCards.forEach { candidate ->
            val amountInDeck = cardAmounts[candidate.name]
            val maxAmount = allCardProtos.find { it.name == candidate.name }?.deckMaximum
            requireNotNull(maxAmount) { "unknown card in backpack: ${candidate.name}" }
            if (maxAmount == -1 || amountInDeck == null || amountInDeck < maxAmount) {
                addToDeck(nextFreeSlot(), candidate)
            }
            if (_cardPositions.size >= minDeckSize) return
        }
    }

    fun nextFreeSlot(): Int {
        val keys = cardPositions.keys
        if (keys.size >= numberOfSlots) return -1
        for (i in keys.indices) {
            if (!keys.contains(i)) return i
        }
        return keys.size
    }

    fun swapCards(i1: Int, i2: Int) {
        val old1 = _cardPositions[i1]
        val old2 = _cardPositions[i2]
        if (old1 == null) _cardPositions.remove(i2)
        else _cardPositions[i2] = old1
        if (old2 == null) _cardPositions.remove(i1)
        else _cardPositions[i1] = old2
        dirty()
    }

    fun addToDeck(index: Int, type: CardType) {
        if (index >= 0) {
            _cardPositions[index] = type
            dirty()
        }
    }

    fun removeFromDeck(index: Int) {
        _cardPositions.remove(index)
        dirty()
    }

    fun asOnjObject(): OnjObject {
        val deck = mutableListOf<OnjObject>()
        _cardPositions.forEach {
            deck.add(buildOnjObject {
                "positionId" with it.key
                "card" with it.value.asOnj()
            })
        }
        deck.sortBy { it.get<Long>("positionId") }
        return buildOnjObject {
            "index" with id
            "name" with name
            "cards" with deck
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is Deck && other.id == this.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun canRemoveCards(): Boolean = cards.size > minDeckSize
    fun hasEnoughCards(): Boolean = cards.size >= minDeckSize

    fun canAddCards(): Boolean = cards.size < numberOfSlots

    fun countCards(name: String) = cards.count { it.name == name }

    companion object {

        fun getFromOnj(onj: OnjObject): Deck {
            val id = onj.get<Long>("index").toInt()
            val name = onj.get<String?>("name") ?: "Deck $id"
            val cardPositions: MutableMap<Int, CardType> = mutableMapOf()
            onj.get<OnjArray>("cards").value.forEach {
                it as OnjObject
                cardPositions[it.get<Long>("positionId").toInt()] = CardType.fromOnj(it.get<OnjObject>("card"))
            }
            return Deck(name, id, cardPositions)
        }

        const val minDeckSize = 14
        const val numberOfSlots = 35
    }
}