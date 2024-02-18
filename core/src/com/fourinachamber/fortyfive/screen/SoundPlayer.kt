package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.value.OnjArray
import onj.value.OnjObject
import onj.value.OnjValue

object SoundPlayer {

    const val soundsFile: String = "config/sounds.onj"
    const val soundsSchemaFile: String = "onjschemas/sounds.onjschema"

    private lateinit var situations: List<Situation>
    private lateinit var ambientSounds: MutableMap<AmbientSound, Long>
    private lateinit var biomeAmbience: Map<String, List<String>>

    private var currentMusicHandle: ResourceHandle? = null
    private var currentMusic: Music? = null

//    var musicVolume: Float = 0f
    var musicVolume: Float = 1f
    var soundEffectVolume: Float = 1.0f

    fun init() {
        val onj = OnjParser.parseFile(Gdx.files.internal(soundsFile).file())
        val schema = OnjSchemaParser.parseFile(Gdx.files.internal(soundsSchemaFile).file())
        schema.assertMatches(onj)
        onj as OnjObject
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
        if (currentMusicHandle == musicHandle) return
        currentMusic?.stop()
        if (musicHandle == null) {
            currentMusic = null
            currentMusicHandle = null
            return
        }
        val music = ResourceManager.get<Music>(screen, musicHandle)
        currentMusic = music
        currentMusicHandle = musicHandle
        music.isLooping = true
        music.play()
        music.volume = musicVolume
    }

    fun situation(name: String, screen: OnjScreen) {
        val situation = situations.find { it.name == name } ?: run {
            FortyFiveLogger.warn(logTag, "No sound config for situation $name")
            return
        }
        val sound = ResourceManager.get<Sound>(screen, situation.sound ?: return)
        sound.play(situation.volume * soundEffectVolume)
    }

    fun playSoundFull(soundHandle: ResourceHandle, screen: OnjScreen) {
        val sound = ResourceManager.get<Sound>(screen, soundHandle)
        sound.play(soundEffectVolume)
    }

    fun updateAmbientSounds(screen: OnjScreen) {
        val now = TimeUtils.millis()
        val biome = MapManager.currentDetailMap.biome
        val sounds = biomeAmbience[biome] ?: run {
            FortyFiveLogger.warn(logTag, "No ambience defined for biome $biome")
            return
        }
        ambientSounds.filter { it.key.name in sounds }.forEach { (ambient, nextPlayTime) ->
            if (nextPlayTime > now) return@forEach
            val sound = ResourceManager.get<Sound>(screen, ambient.sound)
            val id = sound.play()
            sound.setPan(id, (-1f..1f).random(), ambient.volume * soundEffectVolume)
            ambientSounds[ambient] = now + ambient.delay.random()
        }
    }

    private data class AmbientSound(
        val name: String,
        val sound: ResourceHandle,
        val volume: Float,
        val delay: IntRange
    )

    private data class Situation(
        val name: String,
        val sound: ResourceHandle?,
        val volume: Float
    )

    const val logTag = "SoundPlayer"

}
