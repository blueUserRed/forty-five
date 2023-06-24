package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.game.card.Card
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


//    var eventData:

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

        _cards = obj.get<OnjArray>("cards").value.map { it.value as String }.toMutableList()
        FortyFiveLogger.debug(logTag, "cards: $_cards")

        val stats = obj.get<OnjObject>("stats")
        usedReserves = stats.get<Long>("usedReserves").toInt()
        enemiesDefeated = stats.get<Long>("enemiesDefeated").toInt()

        val position = obj.get<OnjObject>("position")
        currentMap = position.get<String>("map")
        currentNode = position.get<Long>("node").toInt()
        lastNode = position.get<Long?>("lastNode")?.toInt()

        playerLives = obj.get<Long>("playerLives").toInt()
        playerMoney = obj.get<Long>("playerMoney").toInt()

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

    fun buyCard(card: String) {
        _cards.add(card)
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
            "cards" with _cards
            "playerLives" with playerLives
            "playerMoney" with playerMoney
            "stats" with buildOnjObject {
                "usedReserves" with usedReserves
                "enemiesDefeated" with enemiesDefeated
            }
            "position" with buildOnjObject {
                "map" with currentMap
                "node" with currentNode
                "lastNode" with lastNode
            }
        }
        Gdx.files.local(saveFilePath).file().writeText(obj.toString())
        savefileDirty = false
    }

}