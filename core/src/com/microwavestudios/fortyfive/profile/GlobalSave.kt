package com.microwavestudios.fortyfive.profile

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.plugin.ManagedPlugin
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
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
        fullscreen = true,
        pluginConfig = mutableListOf()
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

    // not stored in the savefile but still important to persist between screens
    var currentControllerUid: String? = null

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
            FortyFive.soundPlayer.soundEffectVolume = soundEffectVolume
            FortyFive.soundPlayer.musicVolume = musicVolume
            FortyFive.soundPlayer.masterVolume = masterVolume
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

    fun verifyPluginSaveData(plugin: ManagedPlugin) {
        val data = getPluginSaveData(plugin.name)
        if (data.jarFileHash == plugin.jarFileHash) return
        val newData = PluginSaveData(plugin.name, true, false, plugin.jarFileHash)
        this.data.pluginConfig.remove(data)
        this.data.pluginConfig.add(newData)
        dirty()
    }

    fun getPluginSaveData(name: String): PluginSaveData {
        data.pluginConfig.find { it.name == name }?.let { return it }
        val newData = PluginSaveData(name, false, false, null)
        data.pluginConfig.add(newData)
        dirty()
        return newData
    }

    fun setPluginActivation(name: String, active: Boolean) {
        val pluginData = getPluginSaveData(name)
        val newPluginData = pluginData.copy(isDisabled = !active)
        data.pluginConfig.remove(pluginData)
        data.pluginConfig.add(newPluginData)
        dirty()
    }

    fun setPluginAgreement(name: String, agreed: Boolean) {
        val pluginData = getPluginSaveData(name)
        val newPluginData = pluginData.copy(agreedToRisk = agreed)
        data.pluginConfig.remove(pluginData)
        data.pluginConfig.add(newPluginData)
        dirty()
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
        var pluginConfig: MutableList<PluginSaveData>
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
            "pluginConfig" with pluginConfig.map { it.asOnj() }
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
                onj.get<OnjArray>("pluginConfig").value.map { PluginSaveData.fromOnj(it as OnjObject) }.toMutableList(),
            )
        }
    }

    data class PluginSaveData(
        val name: String,
        val isDisabled: Boolean,
        val agreedToRisk: Boolean,
        val jarFileHash: String?
    ) {
        fun asOnj(): OnjObject = buildOnjObject {
            "name" with name
            "isDisabled" with isDisabled
            "agreedToRisk" with agreedToRisk
            "jarFileHash" with jarFileHash
        }

        companion object {
            fun fromOnj(onj: OnjObject): PluginSaveData = PluginSaveData(
                onj.get<String>("name"),
                onj.get<Boolean>("isDisabled"),
                onj.get<Boolean>("agreedToRisk"),
                onj.get<String?>("jarFileHash"),
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
