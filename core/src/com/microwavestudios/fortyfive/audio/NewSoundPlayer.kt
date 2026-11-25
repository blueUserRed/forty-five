package com.microwavestudios.fortyfive.audio

import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.IScreen
import com.microwavestudios.fortyfive.utils.Lifetime
import com.microwavestudios.fortyfive.utils.mapFirst
import com.microwavestudios.fortyfive.utils.random
import com.microwavestudios.fortyfive.utils.toIntRange
import com.microwavestudios.fortyfive.utils.zip
import onj.value.OnjArray
import onj.value.OnjObject
import onj.value.OnjValue
import kotlin.collections.map

class NewSoundPlayer(private val audioThread: AudioThread) : ISoundPlayer, ResourceBorrower {

    private lateinit var situations: List<Situation>
    private lateinit var ambientSounds: MutableMap<AmbientSound, Long>
    private lateinit var biomeAmbience: Map<String, List<String>>

    override var masterVolume: Float = 1f
        set(value) {
            field = value
            volumeChanged()
        }

    override var musicVolume: Float = 1f
        set(value) {
            field = value
            volumeChanged()
        }

    override var soundEffectVolume: Float = 1f
        set(value) {
            field = value
            volumeChanged()
        }

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

    private fun volumeChanged() {
        val message = AudioThreadMessage.VolumeChanged(masterVolume, musicVolume, soundEffectVolume)
        send(message)
    }

    override fun changeTheme(theme: Theme, transitionDuration: Int) {
        val message = AudioThreadMessage.ChangeTheme(theme)
        send(message)
    }

    override fun situation(name: String, screen: IScreen) {
        val situation = situations.find { it.name == name } ?: run {
            FortyFive.logger.warn(logTag, "No sound config for situation $name")
            return
        }
        situation.sound?.let { sound ->
            val message = AudioThreadMessage.PlaySoundOnce(sound, screen.lifetime, situation.volume)
            send(message)
        }
    }

    override fun playSoundOnce(
        soundHandle: ResourceHandle,
        screen: IScreen,
        keepCached: Lifetime
    ) {
        val message = AudioThreadMessage.PlaySoundOnce(soundHandle, keepCached)
        send(message)
    }

    override fun update(screen: IScreen, playAmbientSounds: Boolean) {
        if (!playAmbientSounds) return
        val now = TimeUtils.millis()
        val biome = FortyFive.profileManager.currentProfile?.currentMapSaver?.currentMap?.biome
            ?: return
        val sounds = biomeAmbience[biome] ?: run {
            FortyFive.logger.warn(SoundPlayer.logTag, "No ambience defined for biome $biome")
            return
        }
        ambientSounds.filter { it.key.name in sounds }.forEach { (ambient, nextPlayTime) ->
            if (nextPlayTime > now) return@forEach
            val message = AudioThreadMessage.PlaySoundOnce(
                ambient.sound,
                screen.lifetime,
                ambient.volume,
                (-1f..1f).random()
            )
            send(message)
            ambientSounds[ambient] = now + ambient.delay.random()
        }
    }

    override fun streamAudioOnce(
        handle: ResourceHandle,
        screen: IScreen
    ) {
        val message = AudioThreadMessage.StreamAudioOnce(handle)
        send(message)
    }

    override fun end() {
    }

    private data class AmbientSound(
        val name: String,
        val sound: ResourceHandle,
        val volume: Float,
        val delay: IntRange,
        val soundPlayer: NewSoundPlayer
    )

    private fun send(message: AudioThreadMessage) {
        audioThread.sendMessage(message)
    }

    private data class Situation(
        val name: String,
        val sound: ResourceHandle?,
        val volume: Float
    )
    
    companion object {
        private const val logTag = "SoundPlayer"
    }
}
