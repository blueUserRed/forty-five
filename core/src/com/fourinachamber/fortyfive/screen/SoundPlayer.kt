package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjObject
import onj.value.OnjValue

object SoundPlayer : ResourceBorrower {

    private lateinit var situations: List<Situation>
    private lateinit var ambientSounds: MutableMap<AmbientSound, Long>
    private lateinit var biomeAmbience: Map<String, List<String>>

    private var currentMusicHandle: ResourceHandle? = null
    private var currentMusic: Promise<Music>? = null

    private var transitionToMusic: Promise<Music>? = null
    private var transitionToMusicHandle: ResourceHandle? = null
    private var transitionProgress: Float = -1f
    private var transitionStartTime: Long = -1L
    private var transitionDuration: Int = 0

    var masterVolume: Float = 1f
        set(value) {
            field = value
            currentMusic?.getOrNull()?.volume = masterVolume * musicVolume
        }

    var musicVolume: Float = 1f
        set(value) {
            field = value
            currentMusic?.getOrNull()?.volume = masterVolume * musicVolume
        }

    var soundEffectVolume: Float = 1f

    fun init() {
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
                    it.get<OnjArray>("delay").toIntRange()
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

    fun currentMusic(musicHandle: ResourceHandle?, screen: OnjScreen) {
        if (transitionProgress != -1f) {
            transitionProgress = -1f
            transitionToMusic?.getOrNull()?.stop()
            transitionToMusic = null
        }
        if (currentMusicHandle == musicHandle) return
        currentMusic?.getOrNull()?.stop()
        if (musicHandle == null) {
            currentMusic = null
            currentMusicHandle = null
            return
        }
        val musicPromise = ResourceManager.request<Music>(screen, screen, musicHandle)
        currentMusic = musicPromise
        currentMusicHandle = musicHandle
        musicPromise.then { music ->
            music.isLooping = true
            music.play()
            music.volume = musicVolume * masterVolume
        }
    }

    fun transitionToMusic(musicHandle: ResourceHandle?, duration: Int, screen: OnjScreen) {
        if (musicHandle == currentMusicHandle) return
        val musicPromise = musicHandle?.let { ResourceManager.request<Music>(this, screen, it) }
        transitionProgress = 1f
        transitionToMusic = musicPromise
        transitionStartTime = TimeUtils.millis()
        transitionToMusicHandle = musicHandle
        transitionDuration = duration
        musicPromise?.then { music ->
            music.play()
            music.isLooping = true
            music.volume = 0f
        }
    }

    private fun finishTransition() {
        currentMusic?.getOrNull()?.stop()
        currentMusic = transitionToMusic
        currentMusicHandle = transitionToMusicHandle
        currentMusic?.getOrNull()?.play()
        currentMusic?.getOrNull()?.volume = musicVolume * masterVolume
        transitionProgress = -1f
    }

    fun situation(name: String, screen: OnjScreen) {
        val situation = situations.find { it.name == name } ?: run {
            FortyFiveLogger.warn(logTag, "No sound config for situation $name")
            return
        }
        val soundPromise = ResourceManager.request<Sound>(this, screen, situation.sound ?: return)
        soundPromise.then { sound ->
            sound.play(situation.volume * soundEffectVolume * masterVolume)
        }
    }

    fun playSoundFull(soundHandle: ResourceHandle, screen: OnjScreen) {
        val soundPromise = ResourceManager.request<Sound>(this, screen, soundHandle)
        soundPromise.then { sound ->
            sound.play(soundEffectVolume * masterVolume)
        }
    }

    fun update(screen: OnjScreen, playAmbientSounds: Boolean) {
        if (playAmbientSounds) updateAmbientSounds(screen)
        if (transitionProgress == -1f) return
        val now = TimeUtils.millis()
        val finishTime = transitionStartTime + transitionDuration
        if (now >= finishTime) {
            finishTransition()
            return
        }
        transitionProgress = (finishTime - now).toFloat() / transitionDuration.toFloat()
        currentMusic?.getOrNull()?.volume = transitionProgress * musicVolume * masterVolume
        transitionToMusic?.getOrNull()?.volume = (1f - transitionProgress) * musicVolume * masterVolume
    }

    private fun updateAmbientSounds(screen: OnjScreen) {
        val now = TimeUtils.millis()
        val biome = MapManager.currentDetailMap.biome
        val sounds = biomeAmbience[biome] ?: run {
            FortyFiveLogger.warn(logTag, "No ambience defined for biome $biome")
            return
        }
        ambientSounds.filter { it.key.name in sounds }.forEach { (ambient, nextPlayTime) ->
            if (nextPlayTime > now) return@forEach
            val sound = ambient.getSoundPromise(screen).getOrNull() ?: return@forEach
            val id = sound.play()
            sound.setPan(id, (-1f..1f).random(), ambient.volume * soundEffectVolume * masterVolume)
            ambientSounds[ambient] = now + ambient.delay.random()
        }
    }

    fun skipMusicTo(amount: Float) {
        currentMusic?.then { it.position = amount }
    }

    fun playMusicOnce(musicHandle: ResourceHandle, screen: OnjScreen) {
        val musicPromise = ResourceManager.request<Music>(this, screen, musicHandle)
        musicPromise.then { music ->
            music.play()
            music.volume = musicVolume * masterVolume
        }
    }

    private data class AmbientSound(
        val name: String,
        val sound: ResourceHandle,
        val volume: Float,
        val delay: IntRange
    ) {
        private var soundPromise: Promise<Sound>? = null

        fun getSoundPromise(lifetime: Lifetime): Promise<Sound> {
            if (soundPromise == null) {
                soundPromise = ResourceManager.request(SoundPlayer, lifetime, sound)
            }
            return soundPromise!!
        }
    }

    private data class Situation(
        val name: String,
        val sound: ResourceHandle?,
        val volume: Float
    )

    const val logTag = "SoundPlayer"

}
