package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.TemplateString
import onj.*

object SaveState {

    const val logTag = "SaveState"
    const val saveFilePath: String = "saves/savefile.onj"
    const val defaultSavefilePath: String = "saves/default_savefile.onj"

    private var _additionalCards: MutableMap<String, Int> = mutableMapOf()

    private var _cardsToDraw: MutableMap<String, Int> = mutableMapOf()

    val additionalCards: Map<String, Int>
        get() = _additionalCards

    val cardsToDraw: Map<String, Int>
        get() = _cardsToDraw

    var usedReserves: Int = 0
        set(value) {
            field = value
            savefileDirty = true
        }

    var enemiesDefeated: Int = 0
        set(value) {
            field = value
            savefileDirty = true
        }

    var savefileDirty: Boolean = false
        private set

    var playerLives: Int = 0
        set(value) {
            field = value
            savefileDirty = true
        }

    var lastRunUsedReserves: Int = 0
        private set
    var lastRunEnemiesDefeated: Int = 0
        private set

    private val savefileSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/savefile.onjschema").file())
    }

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
    }

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