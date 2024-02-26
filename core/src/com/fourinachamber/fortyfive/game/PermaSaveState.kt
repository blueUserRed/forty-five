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
import kotlin.random.Random

object PermaSaveState {

    const val logTag = "PermaSaveState"
    const val saveFilePath: String = "saves/perma_savefile.onj"
    const val defaultSaveFilePath: String = "saves/default_perma_savefile.onj"

    private val savefileSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/perma_savefile.onjschema").file())
    }

    private var saveFileDirty: Boolean = false

    private var currentRandom: Long = 0

    var collection: List<String> = mutableListOf()
        set(value) {
            field = value
            saveFileDirty = true
        }

    var playerHasCompletedTutorial: Boolean = false
        set(value) {
            field = value
            saveFileDirty = true
        }

    private var _visitedAreas: MutableSet<String> = mutableSetOf()

    val visitedAreas: Set<String>
        get() = _visitedAreas

    var statTotalMoneyEarned: Int by templateParam("stat.totalCashCollected", 0)
    var statEncountersWon: Int by templateParam("stat.encountersWon", 0)
    var statBulletsShot: Int by templateParam("stat.bulletsShot", 0)
    var statUsedReserves: Int by templateParam("stat.usedReserves", 0)
    var statEnemiesDefeated: Int by templateParam("stat.enemiesDefeated", 0)

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

        currentRandom = obj.get<Long?>("lastRandom") ?: Random.nextLong()
        playerHasCompletedTutorial = obj.get<Boolean>("playerHasCompletedTutorial")
        collection = obj.get<OnjArray>("collection").value.map { it.value as String }
        _visitedAreas = obj.get<OnjArray>("visitedAreas").value.map { it.value as String }.toMutableSet()

        saveFileDirty = false
    }

    fun hasVisitedArea(area: String) = area in visitedAreas

    fun visitedNewArea(area: String) {
        _visitedAreas.add(area)
    }

    fun write() {
        if (!saveFileDirty) return
        val obj = buildOnjObject {
            "lastRandom" with currentRandom
            "collection" with collection
            "playerHasCompletedTutorial" with playerHasCompletedTutorial
            "visitedAreas" with _visitedAreas
        }
        Gdx.files.local(saveFilePath).file().writeText(obj.toString())
        saveFileDirty = false
    }

    fun newRun() {
        saveFileDirty = true
        currentRandom = Random.nextLong()
    }

    fun reset() {
        FortyFiveLogger.debug(logTag, "resetting perma_savefile")
        copyDefaultFile()
        read()
    }

    fun runRandom(i: Int): Long {
        val random = Random(currentRandom)
        repeat(i) { random.nextLong() } // TODO: find better solution
        return random.nextLong()
    }

    private fun copyDefaultFile() {
        FortyFiveLogger.debug(logTag, "copying default perma save")
        Gdx.files.local(defaultSaveFilePath).copyTo(Gdx.files.local(saveFilePath))
    }

}