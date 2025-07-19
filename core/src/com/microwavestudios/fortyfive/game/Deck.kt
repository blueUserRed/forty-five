package com.microwavestudios.fortyfive.game

import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjObject

class Deck(var name: String, val id: Int, private val _cardPositions: MutableMap<Int, String>) {

    var deckDirty: Boolean = true
        private set

    val cardPositions: Map<Int, String>
        get() = _cardPositions

    val cards: List<String>
        get() = _cardPositions.map { it.value }


    fun resetDeckDirty() {
        deckDirty = false
    }

    private fun dirty() {
        deckDirty = true
    }

    fun checkDeck(availableCards: List<String>) {
        if (cardPositions.size < minDeckSize && cardPositions.size < availableCards.size) {
            val onlyBackpackCards = mutableListOf<String>()
            val curDeck = cards.toMutableList()
            for (i in availableCards) {
                if (i in curDeck) {
                    curDeck.removeAt(curDeck.indexOf(i))
                } else {
                    onlyBackpackCards.add(i)
                }
            }
            while (cards.size < minDeckSize && onlyBackpackCards.isNotEmpty()) {
                val cur = onlyBackpackCards[0]
                _cardPositions[nextFreeSlot()] = cur
                onlyBackpackCards.removeAt(onlyBackpackCards.indexOf(cur))
            }
            dirty()
        }
        //TODO ugly, this code should never be necessary
        val remainingCards = availableCards.toMutableList()
        val iterator = _cardPositions.iterator()
        while (iterator.hasNext()) {
            val it = iterator.next()
            if (it.value in remainingCards) {
                remainingCards.remove(it.value)
            } else {
                iterator.remove()
            }
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

    fun addToDeck(index: Int, name: String) {
        if (index >= 0) {
            _cardPositions[index] = name
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
                "cardName" with it.value
            })
        }
        deck.sortBy { it.get<Long>("positionId") }
        return buildOnjObject {
            "index" with id
            "name" with name
            "cards" with OnjArray(deck)
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

    companion object {

        fun getFromOnj(onj: OnjObject): Deck {
            val id = onj.get<Long>("index").toInt()
            val name = onj.get<String?>("name") ?: "Deck $id"
            val cardPositions: MutableMap<Int, String> = mutableMapOf()
            onj.get<OnjArray>("cards").value.forEach {
                it as OnjObject
                cardPositions[it.get<Long>("positionId").toInt()] = it.get<String>("cardName")
            }
            return Deck(name, id, cardPositions)
        }

        const val minDeckSize = 14
        const val numberOfSlots = 35
    }
}