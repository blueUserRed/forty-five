package com.microwavestudios.fortyfive.testing.mockservices

import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.IScreen
import com.microwavestudios.fortyfive.screen.ISoundPlayer
import com.microwavestudios.fortyfive.screen.SoundPlayer

class MockSoundPlayer : ISoundPlayer {

    override var masterVolume: Float = 1f
    override var musicVolume: Float = 1f
    override var soundEffectVolume: Float = 1f

    override fun init() {
    }

    override fun changeMusicTo(
        theme: SoundPlayer.Theme,
        transitionDuration: Int
    ) {
    }

    override fun situation(name: String, screen: IScreen) {
    }

    override fun playSoundFull(
        soundHandle: ResourceHandle,
        screen: IScreen
    ) {
    }

    override fun update(screen: IScreen, playAmbientSounds: Boolean) {
    }

    override fun playMusicOnce(
        musicHandle: ResourceHandle,
        screen: IScreen
    ) {
    }

    override fun end() {
    }
}