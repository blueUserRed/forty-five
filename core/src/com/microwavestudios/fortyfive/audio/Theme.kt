package com.microwavestudios.fortyfive.audio

import com.badlogic.gdx.utils.Disposable
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import java.io.File

object Themes {

    val TITLE = SimpleTheme("main_theme")
    val MAP = SimpleTheme("map_theme")
    val BATTLE = SimpleTheme("main_theme")

}

interface Theme : Disposable {

    fun init(audioInterface: AudioInterface)
    fun start()
    fun update()
    fun end()
    fun setVolume(volume: Float)

}

class SilentTheme : Theme {

    override fun init(audioInterface: AudioInterface) {
    }

    override fun start() {
    }

    override fun update() {
    }

    override fun setVolume(volume: Float) {
    }

    override fun end() {
    }

    override fun dispose() {
    }
}

class SimpleTheme(
    private val musicHandle: String
) : Theme, ResourceBorrower {

    private val file =
        FortyFive.resourceManager.forceGet<File>(this, FortyFive.gameLifetime, musicHandle)

    private lateinit var streamable: StreamableAudio

    override fun init(audioInterface: AudioInterface) {
        streamable = audioInterface.createStreamableAudio(file)
    }

    override fun start() {
        streamable.looping = true
        streamable.play()
    }

    override fun update() {
        streamable.update()
    }

    override fun setVolume(volume: Float) {
        streamable.setVolume(volume)
    }

    override fun end() {
        streamable.stop()
    }

    override fun dispose() {
        streamable.dispose()
    }

}
