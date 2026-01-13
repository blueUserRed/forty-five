package com.microwavestudios.fortyfive.profile

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.FortyFive
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjObject
import java.io.File
import java.io.IOException
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

class GlobalSave {

    private var dirty: Boolean = false

    private var data: GlobalSaveData = GlobalSaveData(
        soundEffectVolume = 1f,
        musicVolume = 1f,
        masterVolume = 0.5f,
        enableScreenShake = true,
        skipIntroScreen = false,
        lastUsedProfile = null,
        useBorderlessWindowFullscreen = true,
        fullscreen = true
    )

    var soundEffectVolume: Float by DataDelegate(
        GlobalSaveData::soundEffectVolume,
        onSet = { FortyFive.soundPlayer.soundEffectVolume = it }
    )

    var musicVolume: Float by DataDelegate(
        GlobalSaveData::musicVolume,
        onSet = { FortyFive.soundPlayer.musicVolume = it }
    )

    var masterVolume: Float by DataDelegate(
        GlobalSaveData::masterVolume,
        onSet = { FortyFive.soundPlayer.masterVolume = it }
    )

    var enableScreenShake: Boolean by DataDelegate(GlobalSaveData::enableScreenShake)
    var skipIntroScreen: Boolean by DataDelegate(GlobalSaveData::skipIntroScreen)
    var lastUsedProfile: String? by DataDelegate(GlobalSaveData::lastUsedProfile)

    var useBorderlessWindowFullscreen: Boolean by DataDelegate(
        GlobalSaveData::useBorderlessWindowFullscreen,
        onSet = { setToCorrectWindowMode() }
    )
    var fullscreen: Boolean by DataDelegate(
        GlobalSaveData::fullscreen,
        onSet = { setToCorrectWindowMode() }
    )

    private fun dirty() {
        dirty = true
    }

    fun readFromDisk() {
        try {
            val file = File(savePath)
            val onj = OnjParser.parseFile(file)
            onj as OnjObject
            val readVersion = onj.getOr<Long?>("version", null)
            if (readVersion == null) {
                FortyFive.logger.warn(logTag, "global save misses version")
                return
            }
            if (readVersion.toInt() != globalSaveVersion) {
                FortyFive.logger.warn(logTag, "global save version mismatch: $readVersion != $globalSaveVersion")
                return
            }
            val result = globalSaveSchema.check(onj)
            if (result != null) {
                FortyFive.logger.warn(logTag, "global save doesn't match schema:\n$result")
            }
            data = GlobalSaveData.fromOnj(onj)
        } catch (e: IOException) {
            FortyFive.logger.warn(logTag, "Read of global save file failed")
            FortyFive.logger.stackTrace(e)
        } catch (e: OnjParserException) {
            FortyFive.logger.warn(logTag, "Couldn't parse global save file")
            FortyFive.logger.stackTrace(e)
        }
        dirty = false
    }

    fun write() {
        if (!dirty) return
        val onj = data.asOnj()
        try {
            val saveFile = File(savePath)
            if (!saveFile.exists()) saveFile.createNewFile()
            saveFile.writeText(onj.toString())
        } catch (e: IOException) {
            FortyFive.logger.warn(logTag, "Write of global save file failed")
            FortyFive.logger.stackTrace(e)
        }
        dirty = false
    }

    fun setToCorrectWindowMode() {
        if (!fullscreen) {
            Gdx.graphics.setUndecorated(false)
            Gdx.graphics.setWindowedMode(1600, 900)
            return
        }
        if (useBorderlessWindowFullscreen) {
            Gdx.graphics.setUndecorated(true)
            val display = Gdx.graphics.displayMode
            Gdx.graphics.setWindowedMode(display.width, display.height)
        } else {
            Gdx.graphics.setUndecorated(false)
            Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
        }
    }

    data class GlobalSaveData(
        var soundEffectVolume: Float,
        var musicVolume: Float,
        var masterVolume: Float,
        var enableScreenShake: Boolean,
        var skipIntroScreen: Boolean,
        var lastUsedProfile: String?,
        var useBorderlessWindowFullscreen: Boolean,
        var fullscreen: Boolean,
    ) {

        fun asOnj(): OnjObject = buildOnjObject {
            "version" with globalSaveVersion
            "soundEffectVolume" with soundEffectVolume
            "musicVolume" with musicVolume
            "masterVolume" with masterVolume
            "enableScreenShake" with enableScreenShake
            "skipIntroScreen" with skipIntroScreen
            "lastUsedProfile" with lastUsedProfile
            "useBorderlessWindowFullscreen" with useBorderlessWindowFullscreen
            "fullscreen" with fullscreen
        }

        companion object {

            fun fromOnj(onj: OnjObject): GlobalSaveData = GlobalSaveData(
                onj.get<Double>("soundEffectVolume").toFloat(),
                onj.get<Double>("musicVolume").toFloat(),
                onj.get<Double>("masterVolume").toFloat(),
                onj.get<Boolean>("enableScreenShake"),
                onj.get<Boolean>("skipIntroScreen"),
                onj.get<String?>("lastUsedProfile"),
                onj.get<Boolean>("useBorderlessWindowFullscreen"),
                onj.get<Boolean>("fullscreen"),
            )
        }
    }

    private inner class DataDelegate<T>(
        val property: KMutableProperty<T>,
        val onGet: ((T) -> Unit)? = null,
        val onSet: ((T) -> Unit)? = null
    ) {

        operator fun getValue(thisRef: Any?, p: KProperty<*>): T {
            return property.getter.call(data).also { onGet?.invoke(it) }
        }

        operator fun setValue(thisRef: Any?, p: KProperty<*>, value: T) {
            val old = getValue(thisRef, p)
            if (old === value) return
            property.setter.call(data, value)
            onSet?.invoke(value)
            dirty()
        }
    }

    companion object {
        const val globalSaveVersion: Int = 0
        const val savePath: String = "profiles/global_save.onj"
        private const val logTag = "GlobalSave"

        private val globalSaveSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/global_save.onjschema").file())
        }
    }
}
