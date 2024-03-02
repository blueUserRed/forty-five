package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjObject

object UserPrefs {

    const val userPrefsVersion: Int = 0
    const val userPrefsPath: String = "saves/user_prefs.onj"
    const val defaultUserPrefsPath: String = "saves/default_user_prefs.onj"

    const val logTag: String = "UserPrefs"

    private val userPrefsSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/user_prefs.onjschema").file())
    }

    private var dirty: Boolean = false

    var soundEffectsVolume: Float = 1f
        set(value) {
            field = value
            SoundPlayer.soundEffectVolume = value
            dirty = true
        }

    var musicVolume: Float = 1f
        set(value) {
            field = value
            SoundPlayer.musicVolume = value
            dirty = true
        }

    var masterVolume: Float = 1f
        set(value) {
            field = value
            SoundPlayer.masterVolume = value
            dirty = true
        }

    var enableScreenShake: Boolean = true
        set(value) {
            field = value
            dirty = true
        }

    fun read() {
        FortyFiveLogger.debug(logTag, "reading user_prefs")

        val file = Gdx.files.local(userPrefsPath).file()
        if (!file.exists()) copyDefaultFile()

        var obj = try {
            OnjParser.parseFile(file)
        } catch (e: OnjParserException) {
            FortyFiveLogger.debug(logTag, "Userprefs invalid: ${e.message}")
            copyDefaultFile()
            OnjParser.parseFile(file)
        }

        val version = (obj as? OnjObject)?.getOr<Long?>("version", null)?.toInt()
        if (version?.equals(userPrefsVersion)?.not() ?: true) {
            FortyFiveLogger.warn(logTag, "incompatible userprefs found: version is $version; expected: $userPrefsVersion")
            copyDefaultFile()
            obj = OnjParser.parseFile(file)
        }

        val result = userPrefsSchema.check(obj)
        if (result != null) {
            FortyFiveLogger.debug(logTag, "Savefile invalid: $result")
            copyDefaultFile()
            obj = OnjParser.parseFile(Gdx.files.local(userPrefsPath).file())
            userPrefsSchema.assertMatches(obj)
        }

        obj as OnjObject
        
        soundEffectsVolume = obj.get<Double>("soundEffectsVolume").toFloat()
        musicVolume = obj.get<Double>("musicVolume").toFloat()
        masterVolume = obj.get<Double>("masterVolume").toFloat()
        enableScreenShake = obj.get<Boolean>("enableScreenShake")
        dirty = false
    }

    fun write() {
        if (!dirty) return
        val obj = buildOnjObject {
            "version" with userPrefsVersion
            "soundEffectsVolume" with soundEffectsVolume
            "musicVolume" with musicVolume
            "masterVolume" with masterVolume
            "enableScreenShake" with enableScreenShake
        }
        Gdx.files.internal(userPrefsPath).file().writeText(obj.toString())
        dirty = false
    }

    fun reset() {
        copyDefaultFile()
        read()
    }

    private fun copyDefaultFile() {
        FortyFiveLogger.debug(logTag, "copying default user prefs file")
        Gdx.files.local(defaultUserPrefsPath).copyTo(Gdx.files.local(userPrefsPath))
    }

}
