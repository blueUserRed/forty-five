package com.microwavestudios.fortyfive.testing.mockservices

import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.game.Talisman
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.profile.IProfile
import com.microwavestudios.fortyfive.profile.IRunSave
import com.microwavestudios.fortyfive.profile.MapSaver
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.profile.RunSave
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.testing.notAvailableInMock

class MockProfile(
    override val name: String,
    val data: Profile.ProfileData,
    val runSaveData: MockRunSaveData?,
    val mockMapSaver: MapSaver?
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

    override val usedSteps: Int?
        get() = runSaveData?.usedSteps

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
    override var currentRunDeck: Deck?
        get() = runSaveData?.backpackDecks?.find { it.id == runSaveData.currentDeckId }
        set(value) { runSaveData?.currentDeckId = value!!.id }

    override val backpack: List<CardType>?
        get() = runSaveData?.backpack


    override val backpackDecks: List<Deck>?
        get() = runSaveData?.backpackDecks

    override val encountersStartedInRun: Int?
        get() = runSaveData?.encountersStarted

    override val talismans: List<Talisman>
        get() = runSaveData?.talismans ?: listOf()

    override val talismanRewardChanceBoost: Double
        get() = runSaveData?.talismanRewardChanceBoost ?: 0.0

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
        get() = mockMapSaver ?: notAvailableInMock()

    override val currentAreaMap: DetailMap
        get() = currentMapSaver.currentMap

    override val activeRun: Run?
        get() = runSaveData?.run

    override val isRunActive: Boolean
        get() = runSaveData != null

    override var healthInRun: Int?
        get() = runSaveData?.playerHealth
        set(value) {
            runSaveData?.playerHealth = value!!
        }

    override val maxHealthInRun: Int?
        get() = runSaveData?.run?.maxPlayerHealth

    override val areaMapSaver: MapSaver
        get() = mockMapSaver ?: notAvailableInMock()


    init {
        checkDecks()
    }

    override fun isSpecialRunCompleted(runName: String): Boolean =
        runName in data.completedSpecialRuns

    override fun addCardToBackpack(card: CardType) {
        require(isRunActive) { "not in run" }
        runSaveData!!.backpack.add(card)
        checkDecks()
    }

    override fun swapCardInBackpack(
        old: CardType,
        new: CardType
    ) {
        require(isRunActive) { "not in run" }
        val backpack = runSaveData?.backpack ?: return
        require(backpack.remove(old))
        backpack.add(old)
        checkDecks()
    }

    override fun addCardToCollection(card: CardType) {
        data.cardCollection.add(card)
        checkDecks()
    }

    override fun stepTaken() {
        require(isRunActive) { "not in run" }
        runSaveData!!.usedSteps++
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
        require(isRunActive) { "not in run" }
        runSaveData!!.backpack.add(card)
        checkDecks()
    }

    override fun getTalismanForRun(talisman: Talisman) {
        require(isRunActive) { "not in run" }
        runSaveData!!.talismans.add(talisman)
    }

    override fun boostTalismanRewardChance(increase: Double) {
        require(isRunActive) { "not in run" }
        runSaveData!!.talismanRewardChanceBoost += increase
    }

    override fun checkDecks() {
        backpackDecks?.forEach { it.checkDeck(backpack!!) }
        collectionDecks.forEach { it.checkDeck(cardCollection) }
    }

    override fun extractableCards(): List<CardType> {
        require(isRunActive) { "not in run" }
        val extractable = mutableListOf<CardType>()
        extractable.addAll(runSaveData!!.backpack)
        extractable.removeAll(runSaveData.cardsTakenAlong)
        return extractable
    }

    override fun readFromDisk() {
    }

    override fun dirty() {
    }

    override fun write() {
    }

    fun constructPreview(): MockPreview = MockPreview(
        true,
        name,
        null,
        true,
        currentAreaMapName,
        playerMoney,
        cardCollection,
        runSaveData?.let { saveData ->
            saveData.run?.let { run -> MockRunSavePreview(saveData.playerHealth, run) }
        }
    )


    class MockPreview(
        override val loadedSuccessfully: Boolean,
        override val name: String,
        override val loadFailure: IProfile.LoadFailure?,
        override val exists: Boolean,
        override val currentArea: String?,
        override val money: Int?,
        override val collection: List<CardType>?,
        override val runPreview: IRunSave.IPreview?
    ) : IProfile.IPreview {

        override fun read() {
        }
    }

    class MockRunSavePreview(
        override val playerHealth: Int,
        override val run: Run
    ) : IRunSave.IPreview

    class MockRunSaveData(
        var currentNode: Int,
        var lastNode: Int?,
        var playerHealth: Int,
        var usedSteps: Int,
        var backpack: MutableList<CardType>,
        var backpackDecks: MutableList<Deck>,
        var talismans: MutableList<Talisman>,
        var currentDeckId: Int,
        var encountersStarted: Int,
        var cardsTakenAlong: List<CardType>,
        var talismanRewardChanceBoost: Double,
        var run: Run?
    )
}
