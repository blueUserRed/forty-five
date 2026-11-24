package com.microwavestudios.fortyfive.audio

import com.badlogic.gdx.audio.Sound
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.IScreen
import com.microwavestudios.fortyfive.screen.ISoundPlayer
import com.microwavestudios.fortyfive.screen.SoundPlayer
import com.microwavestudios.fortyfive.utils.Lifetime
import com.microwavestudios.fortyfive.utils.Promise
import com.microwavestudios.fortyfive.utils.mapFirst
import com.microwavestudios.fortyfive.utils.toIntRange
import com.microwavestudios.fortyfive.utils.zip
import onj.value.OnjArray
import onj.value.OnjObject
import onj.value.OnjValue
import kotlin.collections.map

class NewSoundPlayer : ISoundPlayer, ResourceBorrower {

    private lateinit var situations: List<Situation>
    private lateinit var ambientSounds: MutableMap<AmbientSound, Long>
    private lateinit var biomeAmbience: Map<String, List<String>>

    override var masterVolume: Float = 1f
        set(value) {
            field = value
//            currentMusic?.volume = masterVolume * musicVolume
        }

    override var musicVolume: Float = 1f
        set(value) {
            field = value
//            currentMusic?.volume = masterVolume * musicVolume
        }

    override var soundEffectVolume: Float = 1f


    override fun init() {
        val onj = ConfigFileManager.getConfigFile("soundConfig")
        situations = onj
            .get<OnjArray>("situations")
            .value
            .map {
                it as OnjObject
                Situation(
                    it.get<String>("name"),
                    if (it.get<OnjValue>("sound").isNull()) {
                        null
                    } else {
                        it.get<String>("sound")
                    },
                    it.getOr("volume", 1.0).toFloat()
                )
            }
        ambientSounds = onj
            .get<OnjArray>("ambientSounds")
            .value
            .map {
                it as OnjObject
                AmbientSound(
                    it.get<String>("name"),
                    it.get<String>("sound"),
                    it.getOr("volume", 1.0).toFloat(),
                    it.get<OnjArray>("delay").toIntRange(),
                    this
                )
            }
            .associateWith { 0L }
            .toMutableMap()
        biomeAmbience = onj
            .get<OnjArray>("biomeAmbience")
            .value
            .map { it as OnjObject }
            .zip { it.get<String>("biomeName") }
            .mapFirst { it.get<OnjArray>("sounds").value.map { it.value as String } }
            .associate { it.second to it.first }
    }

    override fun changeMusicTo(
        theme: SoundPlayer.Theme,
        transitionDuration: Int
    ) {
        TODO("Not yet implemented")
    }

    override fun situation(name: String, screen: IScreen) {
        TODO("Not yet implemented")
    }

    override fun playSoundFull(
        soundHandle: ResourceHandle,
        screen: IScreen
    ) {
        TODO("Not yet implemented")
    }

    override fun update(screen: IScreen, playAmbientSounds: Boolean) {
        TODO("Not yet implemented")
    }

    override fun playMusicOnce(
        musicHandle: ResourceHandle,
        screen: IScreen
    ) {
        TODO("Not yet implemented")
    }

    override fun end() {
        TODO("Not yet implemented")
    }

    private data class AmbientSound(
        val name: String,
        val sound: ResourceHandle,
        val volume: Float,
        val delay: IntRange,
        val soundPlayer: NewSoundPlayer
    ) {
        private var soundPromise: Promise<Sound>? = null

        fun getSoundPromise(lifetime: Lifetime): Promise<Sound> {
            if (soundPromise == null) {
                soundPromise = FortyFive.resourceManager.request(soundPlayer, lifetime, sound)
            }
            return soundPromise!!
        }
    }

    private data class Situation(
        val name: String,
        val sound: ResourceHandle?,
        val volume: Float
    )
}
