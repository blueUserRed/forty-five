package com.microwavestudios.fortyfive.game

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.FortyFive
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
            FortyFive.soundPlayer.soundEffectVolume = value
            dirty = true
        }

    var musicVolume: Float = 1f
        set(value) {
            field = value
            FortyFive.soundPlayer.musicVolume = value
            dirty = true
        }

    var masterVolume: Float = 1f
        set(value) {
            field = value
            FortyFive.soundPlayer.masterVolume = value
            dirty = true
        }

    var enableScreenShake: Boolean = true
        set(value) {
            field = value
            dirty = true
        }

    var startScreen: StartScreen = StartScreen.INTRO
        set(value) {
            field = value
            dirty = true
        }

    var disableRtMechanics: Boolean = false
        set(value) {
            field = value
            dirty = true
        }

    var windowMode: WindowMode = WindowMode.Window
        set(value) {
            field = value
            dirty = true
            value.setScreenToOption()
        }

    var windowWidth: Int = 700
        set(value) {
            field = value
            dirty = true
        }

    var lastFullScreenAsBorderless: Boolean = false
        set(value) {
            field = value
            dirty = true
        }

    fun read() {
        FortyFive.logger.debug(logTag, "reading user_prefs")

        val file = Gdx.files.local(userPrefsPath).file()
        if (!file.exists()) copyDefaultFile()

        var obj = try {
            OnjParser.parseFile(file)
        } catch (e: OnjParserException) {
            FortyFive.logger.debug(logTag, "Userprefs invalid: ${e.message}")
            copyDefaultFile()
            OnjParser.parseFile(file)
        }

        val version = (obj as? OnjObject)?.getOr<Long?>("version", null)?.toInt()
        if (version?.equals(userPrefsVersion)?.not() ?: true) {
            FortyFive.logger.warn(
                logTag,
                "incompatible userprefs found: version is $version; expected: $userPrefsVersion"
            )
            copyDefaultFile()
            obj = OnjParser.parseFile(file)
        }

        val result = userPrefsSchema.check(obj)
        if (result != null) {
            FortyFive.logger.debug(logTag, "Userprefs invalid: $result")
            copyDefaultFile()
            obj = OnjParser.parseFile(Gdx.files.local(userPrefsPath).file())
            userPrefsSchema.assertMatches(obj)
        }

        obj as OnjObject

        soundEffectsVolume = obj.get<Double>("soundEffectsVolume").toFloat()
        musicVolume = obj.get<Double>("musicVolume").toFloat()
        masterVolume = obj.get<Double>("masterVolume").toFloat()
        enableScreenShake = obj.get<Boolean>("enableScreenShake")
        startScreen = StartScreen.valueOf(obj.get<String>("startScreen").uppercase())
        disableRtMechanics = obj.getOr("disableRtMechanics", false)
        windowWidth = obj.get<Long>("windowWidth").toInt()
        lastFullScreenAsBorderless = obj.get<Boolean>("lastFullScreenAsBorderless")
        windowMode = WindowMode.valueOf(obj.get<String>("windowMode"))
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
            "startScreen" with startScreen.toString()
            "disableRtMechanics" with disableRtMechanics
            "windowWidth" with windowWidth
            "lastFullScreenAsBorderless" with lastFullScreenAsBorderless
            "windowMode" with windowMode.javaClass.simpleName.lowercase()
        }
        Gdx.files.internal(userPrefsPath).file().writeText(obj.toString())
        dirty = false
    }

    fun reset() {
        copyDefaultFile()
        read()
    }

    private fun copyDefaultFile() {
        FortyFive.logger.debug(logTag, "copying default user prefs file")
        Gdx.files.local(defaultUserPrefsPath).copyTo(Gdx.files.local(userPrefsPath))
    }

    enum class StartScreen {
        INTRO, TITLE, MAP
    }

    sealed class WindowMode {
        data object Window : WindowMode() {
            override fun setScreenToOption() {
                Gdx.graphics.setUndecorated(false)
                Gdx.graphics.setWindowedMode(windowWidth, (windowWidth * 9 / 16))
            }
        }

        data object BorderlessWindow : WindowMode() {
            override fun setScreenToOption() {
                lastFullScreenAsBorderless = true
                val displayMode = Gdx.graphics.displayMode
                Gdx.graphics.setUndecorated(true)
                Gdx.graphics.setWindowedMode(displayMode.width, displayMode.height)
            }
        }

        data object Fullscreen : WindowMode() {
            override fun setScreenToOption() {
                lastFullScreenAsBorderless = false
                Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
            }
        }

        abstract fun setScreenToOption()

        companion object {
            fun valueOf(s: String): WindowMode {
                return when (s) {
                    "window" -> Window
                    "borderlesswindow" -> BorderlessWindow
                    "fullscreen" -> Fullscreen
                    else -> throw RuntimeException("unknown windowMode modifier: $s")
                }
            }
        }
    }
}
