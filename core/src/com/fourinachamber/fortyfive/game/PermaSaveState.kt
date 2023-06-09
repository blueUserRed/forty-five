package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
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

        saveFileDirty = false
    }

    fun write() {
        if (!saveFileDirty) return
        val obj = buildOnjObject {
            "lastRandom" with currentRandom
        }
        Gdx.files.local(saveFilePath).file().writeText(obj.toString())
        saveFileDirty = false
    }

    fun newRun() {
        saveFileDirty = true
        currentRandom = Random.nextLong()
    }

    fun reset() {
        copyDefaultFile()
        read()
    }


    fun runRandom(i: Int): Long {
        val random = Random(currentRandom)
        repeat(i) { random.nextLong() } // TODO: find better solution
        return random.nextLong()
    }

    private fun copyDefaultFile() {
        FortyFiveLogger.debug(logTag, "copying default save")
        Gdx.files.local(defaultSaveFilePath).copyTo(Gdx.files.local(saveFilePath))
    }

}