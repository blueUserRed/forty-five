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

    var masterVolume: Float = 1f
        set(value) {
            field = value
            currentMusic?.volume = masterVolume * musicVolume
        }

    var musicVolume: Float = 1f
        set(value) {
            field = value
            currentMusic?.volume = masterVolume * musicVolume
        }

    private val musicTimeline = Timeline().also { it.startTimeline() }

    var soundEffectVolume: Float = 1f

    private var currentMusic: Music? = null
    private var currentMusicLifetime: EndableLifetime? = null
    private var currentMusicTheme: Theme? = null

    private var nextMusic: Music? = null
    private var transitionStartTime: Long = 0
    private var transitionDuration: Int = 0

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

    fun changeMusicTo(theme: Theme, transitionDuration: Int = 3_000) = Timeline.timeline {
        if (theme == currentMusicTheme) return@timeline

        val nextMusicLifetime = EndableLifetime()
        val nextMusic: Promise<Music> = ResourceManager.request(
            this@SoundPlayer,
            nextMusicLifetime,
            theme.resourceHandle
        )

        delayUntilPromiseResolves(nextMusic)

        action {
            val music = nextMusic.getOrError()
            music.volume = 0f
            music.isLooping = true
            music.play()
            this@SoundPlayer.nextMusic = music
            this@SoundPlayer.transitionStartTime = TimeUtils.millis()
            this@SoundPlayer.transitionDuration = transitionDuration
        }

        delay(transitionDuration)

        action {
            currentMusic?.stop()
            currentMusicLifetime?.die()
            currentMusic = nextMusic.getOrError()
            currentMusicLifetime = nextMusicLifetime
            this@SoundPlayer.nextMusic = null
        }

    }.let { musicTimeline.appendAction(it.asAction()) }

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
        musicTimeline.updateTimeline()

        if (nextMusic == null) return
        val timeInTransition = TimeUtils.millis() - transitionStartTime
        val transitionProgress = (timeInTransition.toFloat() / transitionDuration.toFloat()).coerceAtMost(1f)
        currentMusic?.volume = musicVolume * masterVolume * (1 - transitionProgress)
        nextMusic?.volume = musicVolume * masterVolume * transitionProgress
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

    enum class Theme(val resourceHandle: ResourceHandle) {
        TITLE("main_theme"),
        MAIN("map_theme"),
        BATTLE("encounter_theme")
    }

    const val logTag = "SoundPlayer"

}
