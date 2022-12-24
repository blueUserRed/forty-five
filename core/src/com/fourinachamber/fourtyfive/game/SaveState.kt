package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.TemplateString
import onj.*

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


    private var _additionalCards: MutableMap<String, Int> = mutableMapOf()

    private var _cardsToDraw: MutableMap<String, Int> = mutableMapOf()

    /**
     * cards the player has in addition to the cards in the start deck; contains the name of the card and the amount
     */
    val additionalCards: Map<String, Int>
        get() = _additionalCards

    /**
     * the cards the player can choose on the win screen; contains the name of the card and the amount
     */
    val cardsToDraw: Map<String, Int>
        get() = _cardsToDraw

    /**
     * counts the amount of reserves used by the player over the whole run
     */
    var usedReserves: Int = 0
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

    /**
     * true if value has changed since the savefile was last written; will not write if this is false
     */
    var savefileDirty: Boolean = false
        private set

    /**
     * the current lives of the player
     */
    var playerLives: Int = 0
        set(value) {
            field = value
            savefileDirty = true
        }

    /**
     * temporarily stores the used reserves, so it can be shown on the death screen
     */
    var lastRunUsedReserves: Int = 0
        private set

    /**
     * temporarily stores the defeated enemies, so it can be shown on the death screen
     */
    var lastRunEnemiesDefeated: Int = 0
        private set

    private val savefileSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/savefile.onjschema").file())
    }

    /**
     * removes a card from [cardsToDraw] and adds it to [additionalCards]
     */
    fun drawCard(card: Card) {
        savefileDirty = true
        if (!_cardsToDraw.containsKey(card.name)) {
            throw RuntimeException("cannot draw card $card because it dosen't exist")
        }
        _cardsToDraw[card.name] = _cardsToDraw[card.name]!! - 1
        if (_cardsToDraw[card.name]!! <= 0) _cardsToDraw.remove(card.name)

        if (_additionalCards.containsKey(card.name)) {
            _additionalCards[card.name] = _additionalCards[card.name]!! + 1
        } else {
            _additionalCards[card.name] = 1
        }
    }

    /**
     * reads the savefile and sets the values
     */
    fun read() {

        FourtyFiveLogger.debug(logTag, "reading SaveState")

        val file = Gdx.files.local(saveFilePath).file()
        if (!file.exists()) copyDefaultFile()

        var obj = try {
            OnjParser.parseFile(file)
        } catch (e: OnjParserException) {
            FourtyFiveLogger.debug(logTag, "Savefile invalid:${e.message}")
            copyDefaultFile()
            OnjParser.parseFile(file)
        }

        val result = savefileSchema.check(obj)
        if (result != null) {
            FourtyFiveLogger.debug(logTag, "Savefile invalid: $result")
            copyDefaultFile()
            obj = OnjParser.parseFile(Gdx.files.local(saveFilePath).file())
            savefileSchema.assertMatches(obj)
        }

        obj as OnjObject

        _additionalCards = readCardArray(obj.get<OnjArray>("additionalCards")).toMutableMap()
        FourtyFiveLogger.debug(logTag, "additional cards: $_additionalCards")
        _cardsToDraw = readCardArray(obj.get<OnjArray>("cardsToDraw")).toMutableMap()
        FourtyFiveLogger.debug(logTag, "cards to draw: $_cardsToDraw")

        val stats = obj.get<OnjObject>("stats")
        usedReserves = stats.get<Long>("usedReserves").toInt()
        enemiesDefeated = stats.get<Long>("enemiesDefeated").toInt()

        playerLives = obj.get<Long>("playerLives").toInt()

        FourtyFiveLogger.debug(logTag, "stats: " +
                "usedReserves = $usedReserves, " +
                "enemiesDefeated = $enemiesDefeated, " +
                "playerLives = $playerLives")

        bindTemplateStringParams()
        savefileDirty = false
    }

    /**
     * resets to the default save file
     */
    fun reset() {
        copyLastRunStats()
        copyDefaultFile()
        read()
    }

    private fun copyLastRunStats() {
        lastRunEnemiesDefeated = enemiesDefeated
        lastRunUsedReserves = usedReserves
    }

    private fun bindTemplateStringParams() {
        TemplateString.bindParam("stat.usedReserves") { usedReserves }
        TemplateString.bindParam("stat.enemiesDefeated") { enemiesDefeated }
        TemplateString.bindParam("stat.lastRun.usedReserves") { lastRunUsedReserves }
        TemplateString.bindParam("stat.lastRun.enemiesDefeated") { lastRunEnemiesDefeated }
    }

    private fun copyDefaultFile() {
        FourtyFiveLogger.debug(logTag, "copying default save")
        Gdx.files.local(defaultSavefilePath).copyTo(Gdx.files.local(saveFilePath))
    }

    private fun readCardArray(arr: OnjArray): Map<String, Int> = arr
        .value
        .associate {
            it as OnjObject
            it.get<String>("name") to it.get<Long>("amount").toInt()
        }

    /**
     * writes the values to the savefile
     */
    fun write() {
        if (!savefileDirty) return
        FourtyFiveLogger.debug(logTag, "writing SaveState")
        val obj = buildOnjObject {
            "additionalCards" with getCardArray(_additionalCards)
            "cardsToDraw" with getCardArray(_cardsToDraw)
            "playerLives" with playerLives
            "stats" with buildOnjObject {
                "usedReserves" with usedReserves
                "enemiesDefeated" with enemiesDefeated
            }
        }
        Gdx.files.local(saveFilePath).file().writeText(obj.toString())
        savefileDirty = false
    }

    private fun getCardArray(cards: Map<String, Int>) = cards.entries.map {
        buildOnjObject {
            "name" with it.key
            "amount" with it.value
        }
    }

}