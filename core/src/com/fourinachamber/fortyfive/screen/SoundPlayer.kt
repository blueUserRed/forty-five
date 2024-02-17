package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.value.OnjArray
import onj.value.OnjObject
import onj.value.OnjValue

object SoundPlayer {

    const val soundsFile: String = "config/sounds.onj"
    const val soundsSchemaFile: String = "onjschemas/sounds.onjschema"

    private lateinit var situations: List<Situation>

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
    }

    fun situation(name: String, screen: OnjScreen) {
        val situation = situations.find { it.name == name } ?: run {
            FortyFiveLogger.warn(logTag, "No sound config for situation $name")
            return
        }
        val sound = ResourceManager.get<Sound>(screen, situation.sound ?: return)
        sound.play(situation.volume)
    }

    fun playSoundFull(soundHandle: ResourceHandle, screen: OnjScreen) {
        val sound = ResourceManager.get<Sound>(screen, soundHandle)
        sound.play(1f)
    }

    private data class Situation(
        val name: String,
        val sound: ResourceHandle?,
        val volume: Float
    )

    const val logTag = "SoundPlayer"

}
