package com.microwavestudios.fortyfive.testing.mockservices

import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.game.Talisman
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.profile.IProfile
import com.microwavestudios.fortyfive.profile.MapSaver
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.testing.notAvailableInMock

class MockProfile(
    override val name: String,
    val data: Profile.ProfileData,
    override var healthInRun: Int?,
    override val maxHealthInRun: Int?,
    override var currentRunDeck: Deck?,
    override val talismans: List<Talisman>
) : IProfile {

    override val playerMoney: Int
        get() = data.playerMoney
    override val cardCollection: List<CardType>
        get() = data.cardCollection
    override val collectionDecks: List<Deck>
        get() = data.collectionDecks
    override val currentAreaMapName: String
        get() = data.currentMap
    override val wonRuns: Int
        get() = data.wonRuns

    override var usedSteps: Int = 0

    private var currentCollectionDeckId: Int
        get() = data.currentDeckId
        set(value) {
            data.currentDeckId = value
        }

    override var currentCollectionDeck: Deck
        get() =
            data.collectionDecks.find { it.id == currentCollectionDeckId }!!
        set(value) {
            currentCollectionDeckId = value.id
        }

    override val backpack: List<CardType>?
        get() = notAvailableInMock()

    override val backpackDecks: List<Deck>?
        get() = notAvailableInMock()

    override val encountersStartedInRun: Int?
        get() = notAvailableInMock()

    override var currentNodeIndex: Int
        get() = data.currentNode
        set(value) {
            data.currentNode = value
        }
    override var lastNodeIndex: Int?
        get() = data.lastNode
        set(value) {
            data.lastNode = value
        }

    override val currentMapSaver: MapSaver
        get() = notAvailableInMock()

    override val currentAreaMap: DetailMap
        get() = notAvailableInMock()

    override val activeRun: Run?
        get() = null
    override val isRunActive: Boolean
        get() = false

    override val areaMapSaver: MapSaver
        get() = notAvailableInMock()

    override fun isSpecialRunCompleted(runName: String): Boolean =
        runName in data.completedSpecialRuns

    override fun addCardToBackpack(card: CardType) {
        notAvailableInMock()
    }

    override fun swapCardInBackpack(
        old: CardType,
        new: CardType
    ) = notAvailableInMock()

    override fun addCardToCollection(card: CardType) {
        data.cardCollection.add(card)
    }

    override fun stepTaken() {
        usedSteps++
    }

    override fun swapCardInCollection(
        old: CardType,
        new: CardType
    ) {
        val result = data.cardCollection.remove(old)
        require(result) { "card $old not in collection" }
        data.cardCollection.add(new)
        checkDecks()
    }

    override fun changeToMap(map: String, fromEnd: Boolean) = notAvailableInMock()

    override fun runBoardForArea(area: DetailMap): Profile.RunBoard = notAvailableInMock()

    override fun addRunToRunBoard(areaName: String, run: Run) = notAvailableInMock()

    override fun startRun(run: Run) = notAvailableInMock()

    override fun encounterStarted() = notAvailableInMock()

    override fun loseRun() = notAvailableInMock()

    override fun winRun() = notAvailableInMock()

    override fun earnMoney(amount: Int) {
        data.playerMoney += amount
    }

    override fun payMoney(amount: Int) {
        data.playerMoney = (data.playerMoney - amount).coerceAtLeast(0)
    }

    override fun getCardForRun(card: CardType) {
    }

    override fun checkDecks() {
    }

    override fun extractableCards(): List<CardType> = notAvailableInMock()

    override fun readFromDisk() {
    }

    override fun dirty() {
    }

    override fun write() {
    }
}
