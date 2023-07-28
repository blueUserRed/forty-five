package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.templateParam
import kotlinx.coroutines.newFixedThreadPoolContext
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*

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

    private var _ownedCards: MutableList<String> = mutableListOf()

    val cards: List<String>
        get() = _ownedCards

    private var _decks: MutableSet<Deck> = mutableSetOf()

    val curDeck: Deck
        get() = _decks.find { it.id == curDeckNbr }!!

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
    var enemiesDefeated: Int by templateParam("stat.enemiesDefeated", 0) {
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
     * the current money of the player
     */
    var playerMoney: Int by templateParam("stat.playerMoney", 0) {
        savefileDirty = true
    }

    private var curDeckNbr = 1
        set(value) {
            if (curDeckNbr != value) {
                savefileDirty = true
                if (_decks.find { it.id == value } == null) {
                    val curDeck = _decks.find { it.id == 1 }!!
                    _decks.add(Deck("Deck $value", value, curDeck.cardPosition.toMutableMap()))
                }
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

    /**
     * temporarily stores the used reserves, so it can be shown on the death screen
     */
    var lastRunUsedReserves: Int by templateParam("stat.lastRun.usedReserves", 0)
        private set

    /**
     * temporarily stores the defeated enemies, so it can be shown on the death screen
     */
    var lastRunEnemiesDefeated: Int by templateParam("stat.lastRun.enemiesDefeated", 0)
        private set

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
            FortyFiveLogger.debug(logTag, "Savefile invalid: ${e.message}")
            copyDefaultFile()
            OnjParser.parseFile(file)
        }

        val result = savefileSchema.check(obj)
        if (result != null) {
            FortyFiveLogger.debug(logTag, "Savefile invalid: $result")
            copyDefaultFile()
            obj = OnjParser.parseFile(Gdx.files.local(saveFilePath).file())
            savefileSchema.assertMatches(obj)
        }

        obj as OnjObject

        _ownedCards = obj.get<OnjArray>("ownedCards").value.map { it.value as String }.toMutableList()
        FortyFiveLogger.debug(logTag, "cards: $_ownedCards")

        obj.get<OnjArray>("decks").value.forEach { _decks.add(Deck.getFromOnj(it as OnjObject)) }
        curDeckNbr = obj.get<Long>("curDeck").toInt()

        val stats = obj.get<OnjObject>("stats")
        usedReserves = stats.get<Long>("usedReserves").toInt()
        enemiesDefeated = stats.get<Long>("enemiesDefeated").toInt()

        val position = obj.get<OnjObject>("position")
        currentMap = position.get<String>("map")
        currentNode = position.get<Long>("node").toInt()
        lastNode = position.get<Long?>("lastNode")?.toInt()

        playerLives = obj.get<Long>("playerLives").toInt()
        playerMoney = obj.get<Long>("playerMoney").toInt()
        currentDifficulty = obj.get<Double>("currentDifficulty")

        FortyFiveLogger.debug(
            logTag, "stats: " +
                    "usedReserves = $usedReserves, " +
                    "enemiesDefeated = $enemiesDefeated, " +
                    "playerMoney = $playerMoney, " +
                    "playerLives = $playerLives"
        )

        FortyFiveLogger.debug(
            logTag, "position: " +
                    "currentMap = $currentMap, " +
                    "currentNode = $currentNode"
        )

        savefileDirty = true
    }

    /**
     * resets to the default save file
     */
    fun reset() {
        copyLastRunStats()
        copyDefaultFile()
        read()
    }

    fun buyCard(card: String) {
        _ownedCards.add(card)
        savefileDirty = true
    }

    private fun copyLastRunStats() {
        lastRunEnemiesDefeated = enemiesDefeated
        lastRunUsedReserves = usedReserves
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
            "ownedCards" with _ownedCards
            "playerLives" with playerLives
            "playerMoney" with playerMoney
            "currentDifficulty" with currentDifficulty
            "stats" with buildOnjObject {
                "usedReserves" with usedReserves
                "enemiesDefeated" with enemiesDefeated
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


    class Deck(val name: String, val id: Int, private val _cardPositions: MutableMap<Int, String>) {
        val cardPosition: Map<Int, String>
            get() = _cardPositions

        val cards: List<String>
            get() = _cardPositions.map { it.value }


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
            _cardPositions[index] = name
            savefileDirty = true
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
        }
    }
}