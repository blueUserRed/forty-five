package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjObject

object UserPrefs {

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
        val onj = OnjParser.parseFile(Gdx.files.internal(userPrefsPath).file())
        userPrefsSchema.assertMatches(onj)
        onj as OnjObject
        soundEffectsVolume = onj.get<Double>("soundEffectsVolume").toFloat()
        musicVolume = onj.get<Double>("musicVolume").toFloat()
        masterVolume = onj.get<Double>("masterVolume").toFloat()
        enableScreenShake = onj.get<Boolean>("enableScreenShake")
        dirty = false
    }

    fun write() {
        if (!dirty) return
        val obj = buildOnjObject {
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
        Gdx.files.internal(defaultUserPrefsPath).copyTo(Gdx.files.internal(userPrefsPath))
    }

}
