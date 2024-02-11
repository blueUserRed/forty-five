package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.templateParam
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

/**
 * stores data about the current run and can read/write it to a file
 */
object SaveState {

    /**
     * used for logging
     */
    const val logTag = "SaveState"

    /**
     * the path to the file from which the data is read/written to
     */
    const val saveFilePath: String = "saves/savefile.onj"

    /**
     * this file is used when the progress is reset or when no valid savefile can be found
     */
    const val defaultSavefilePath: String = "saves/default_savefile.onj"

    private var _cards: MutableList<String> = mutableListOf()

    val cards: List<String>
        get() = _cards

    private var _decks: MutableSet<Deck> = mutableSetOf()

    val curDeck: Deck
        get() = _decks.find { it.id == curDeckNbr }!!

    var playerCompletedFirstTutorialEncounter: Boolean = false
        set(value) {
            field = value
            savefileDirty = true
        }

    /**
     * counts the amount of reserves used by the player over the whole run
     */
    var usedReserves: Int = 0
        set(value) {
            field = value
            savefileDirty = true
        }

    var currentMap: String = ""
        set(value) {
            field = value
            savefileDirty = true
        }

    var currentNode: Int = 0
        set(value) {
            field = value
            savefileDirty = true
        }

    var lastNode: Int? = null
        set(value) {
            field = value
            savefileDirty = true
        }

    /**
     * how many enemies the player has defeated this run
     */
    var enemiesDefeated: Int = 0
        set(value) {
            field = value
            savefileDirty = true
        }

    var encountersWon: Int = 0
        set(value) {
            field = value
            savefileDirty = true
        }

    var bulletsShot: Int = 0
        set(value) {
            field = value
            savefileDirty = true
        }

    /**
     * true if value has changed since the savefile was last written; will not write if this is false
     */
    var savefileDirty: Boolean = false
        private set

    /**
     * the current lives of the player
     */
    var playerLives: Int by templateParam("stat.playerLives", 0) {
        savefileDirty = true
    }

    /**
     * the current lives of the player
     */
    var maxPlayerLives: Int by templateParam("stat.maxPlayerLives", 0) {
        savefileDirty = true
    }

    /**
     * the current money of the player
     */
    val playerMoney: Int
        get() = _playerMoney

    private var _playerMoney: Int by templateParam("stat.playerMoney", 0) {
        savefileDirty = true
    }

    var totalMoneyEarned: Int = 0
        private set

    var curDeckNbr = 1
        set(value) {
            if (field == value) return
            savefileDirty = true
            if (_decks.find { it.id == value } == null) {
                val curDeck = _decks.find { it.id == field }!!
                _decks.add(Deck("Deck $value", value, curDeck.cardPositions.toMutableMap()))
            }
            field = value
        }

    var currentDifficulty: Double = 1.0
        set(value) {
            field = value
            if (value < 0.5) {
                FortyFiveLogger.warn(logTag, "tried to set difficulty to too small value $value")
                field = 1.0
            }
            savefileDirty = true
        }

    private val savefileSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/savefile.onjschema").file())
    }

    /**
     * reads the savefile and sets the values
     */
    fun read() {

        FortyFiveLogger.debug(logTag, "reading SaveState")

        val file = Gdx.files.local(saveFilePath).file()
        if (!file.exists()) copyDefaultFile()

        var obj = try {
            OnjParser.parseFile(file)
        } catch (e: OnjParserException) {
            FortyFiveLogger.warn(logTag, "Savefile invalid: ${e.message}")
            copyDefaultFile()
            OnjParser.parseFile(file)
        }

        val result = savefileSchema.check(obj)
        if (result != null) {
            FortyFiveLogger.warn(logTag, "Savefile invalid: $result")
            copyDefaultFile()
            obj = OnjParser.parseFile(Gdx.files.local(saveFilePath).file())
            savefileSchema.assertMatches(obj)
        }

        obj as OnjObject

        _cards = obj.get<OnjArray?>("cards")
            ?.value
            ?.map { it.value as String }
            ?.toMutableList()
            ?: PermaSaveState.collection.toMutableList()
        FortyFiveLogger.debug(logTag, "cards: $_cards")

        obj.get<OnjArray>("decks").value.forEach { _decks.add(Deck.getFromOnj(it as OnjObject)) }
        curDeckNbr = obj.get<Long>("curDeck").toInt()

        playerCompletedFirstTutorialEncounter = obj.get<Boolean>("playerCompletedFirstTutorialEncounter")

        val stats = obj.get<OnjObject>("stats")
        usedReserves = stats.get<Long>("usedReserves").toInt()
        enemiesDefeated = stats.get<Long>("enemiesDefeated").toInt()
        totalMoneyEarned = stats.get<Long>("totalMoneyEarned").toInt()
        encountersWon = stats.get<Long>("encountersWon").toInt()
        bulletsShot = stats.get<Long>("bulletsShot").toInt()

        val position = obj.get<OnjObject>("position")
        currentMap = position.get<String>("map")
        currentNode = position.get<Long>("node").toInt()
        lastNode = position.get<Long?>("lastNode")?.toInt()

        playerLives = obj.get<Long>("playerLives").toInt()
        maxPlayerLives = obj.get<Long>("maxPlayerLives").toInt()
        _playerMoney = obj.get<Long>("playerMoney").toInt()
        currentDifficulty = obj.get<Double>("currentDifficulty")

        FortyFiveLogger.debug(
            logTag, "stats: " +
                    "usedReserves = $usedReserves, " +
                    "enemiesDefeated = $enemiesDefeated, " +
                    "playerMoney = $_playerMoney, " +
                    "playerLives = $playerLives" +
                    "maxPlayerLives = $maxPlayerLives"
        )

        FortyFiveLogger.debug(
            logTag, "position: " +
                    "currentMap = $currentMap, " +
                    "currentNode = $currentNode"
        )

        savefileDirty = false
    }

    fun earnMoney(amount: Int) {
        _playerMoney += amount
        totalMoneyEarned += amount
        savefileDirty = true
    }

    fun payMoney(amount: Int) {
        _playerMoney -= amount
        savefileDirty = true
    }

    fun copyStats() {
        PermaSaveState.statTotalMoneyEarned = totalMoneyEarned
        PermaSaveState.statEncountersWon = encountersWon
        PermaSaveState.statBulletsShot = bulletsShot
    }

    /**
     * resets to the default save file
     */
    fun reset() {
        copyDefaultFile()
        read()
    }

    fun buyCard(card: String) {
        _cards.add(card)
        savefileDirty = true
    }

    fun extract() {
        PermaSaveState.collection = _cards.toMutableList()
    }

    private fun copyDefaultFile() {
        FortyFiveLogger.debug(logTag, "copying default save")
        Gdx.files.local(defaultSavefilePath).copyTo(Gdx.files.local(saveFilePath))
    }

    /**
     * writes the values to the savefile
     */
    fun write() {
        if (!savefileDirty) return
        FortyFiveLogger.debug(logTag, "writing SaveState")
        val obj = buildOnjObject {
            "cards" with _cards
            "playerLives" with playerLives
            "maxPlayerLives" with maxPlayerLives
            "playerMoney" with playerMoney
            "currentDifficulty" with currentDifficulty
            "playerCompletedFirstTutorialEncounter" with playerCompletedFirstTutorialEncounter
            "stats" with buildOnjObject {
                "usedReserves" with usedReserves
                "enemiesDefeated" with enemiesDefeated
                "totalMoneyEarned" with totalMoneyEarned
                "encountersWon" with encountersWon
                "bulletsShot" with bulletsShot
            }
            "position" with buildOnjObject {
                "map" with currentMap
                "node" with currentNode
                "lastNode" with lastNode
            }
            "decks" with OnjArray(_decks.map { it.asOnjObject() })
            "curDeck" with curDeckNbr
        }
        Gdx.files.local(saveFilePath).file().writeText(obj.toString())
        savefileDirty = false
    }


    class Deck(var name: String, val id: Int, private val _cardPositions: MutableMap<Int, String>) {
        val cardPositions: Map<Int, String>
            get() = _cardPositions

        val cards: List<String>
            get() = _cardPositions.map { it.value }


        init {
            checkDeck()
        }

        fun checkDeck() {
            if (cardPositions.size < minDeckSize && cardPositions.size < SaveState.cards.size) {
                val onlyBackpackCards = mutableListOf<String>()
                val curDeck = cards.toMutableList()
                for (i in SaveState.cards) {
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
                savefileDirty = true
            }
            //TODO ugly, this code should never be necessary
            val remainingCards = SaveState._cards.toMutableList()
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
            savefileDirty = true
        }

        fun addToDeck(index: Int, name: String) {
            if (index >= 0) {
                _cardPositions[index] = name
                savefileDirty = true
            }
        }

        fun removeFromDeck(index: Int) {
            _cardPositions.remove(index)
            savefileDirty = true
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

            const val minDeckSize = 12
            const val numberOfSlots = 35
        }
    }
}