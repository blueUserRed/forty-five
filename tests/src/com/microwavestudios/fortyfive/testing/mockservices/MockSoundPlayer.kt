package com.microwavestudios.fortyfive.testing.mockservices

import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.IScreen
import com.microwavestudios.fortyfive.audio.ISoundPlayer
import com.microwavestudios.fortyfive.audio.SoundPlayer

class MockSoundPlayer : ISoundPlayer {

    override var masterVolume: Float = 1f
    override var musicVolume: Float = 1f
    override var soundEffectVolume: Float = 1f

    override fun init() {
    }

    override fun changeTheme(
        theme: SoundPlayer.Theme,
        transitionDuration: Int
    ) {
    }

    override fun situation(name: String, screen: IScreen) {
    }

    override fun playSoundOnce(
        soundHandle: ResourceHandle,
        screen: IScreen
    ) {
    }

    override fun update(screen: IScreen, playAmbientSounds: Boolean) {
    }

    override fun streamAudioOnce(
        handle: ResourceHandle,
        screen: IScreen
    ) {
    }

    override fun end() {
    }
}