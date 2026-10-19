package com.microwavestudios.fortyfive.testing.mockservices

import com.microwavestudios.fortyfive.plugin.ManagedPlugin
import com.microwavestudios.fortyfive.profile.GlobalSave
import com.microwavestudios.fortyfive.profile.IGlobalSave
import com.microwavestudios.fortyfive.testing.notAvailableInMock

class MockGlobalSave(val data: GlobalSave.GlobalSaveData) : IGlobalSave {

    override var soundEffectVolume: Float
        get() = data.soundEffectVolume
        set(value) { data.soundEffectVolume = value }

    override var musicVolume: Float
        get() = data.musicVolume
        set(value) { data.musicVolume = value }

    override var masterVolume: Float
        get() = data.masterVolume
        set(value) { data.masterVolume = value }

    override var enableScreenShake: Boolean
        get() = data.enableScreenShake
        set(value) { data.enableScreenShake = value }

    override var skipIntroScreen: Boolean
        get() = data.skipIntroScreen
        set(value) { data.skipIntroScreen = value }

    override var lastUsedProfile: String?
        get() = data.lastUsedProfile
        set(value) { data.lastUsedProfile = value }

    override var useBorderlessWindowFullscreen: Boolean
        get() = data.useBorderlessWindowFullscreen
        set(value) { data.useBorderlessWindowFullscreen = value }

    override var fullscreen: Boolean
        get() = data.fullscreen
        set(value) { data.fullscreen = value }

    override var currentControllerUid: String? = null

    override var enableControllerVibration: Boolean
        get() = data.enableControllerVibration
        set(value) { data.enableControllerVibration = value }

    override fun readFromDisk() {
    }

    override fun write() {
    }

    override fun verifyPluginSaveData(plugin: ManagedPlugin)  {
    }

    override fun getPluginSaveData(name: String): GlobalSave.PluginSaveData {
        return GlobalSave.PluginSaveData(name, true, false, null)
    }

    override fun setPluginActivation(name: String, active: Boolean) {
    }

    override fun setPluginAgreement(name: String, agreed: Boolean) {
    }

    override fun setToCorrectWindowMode() {
    }
}