package com.microwavestudios.fortyfive.audio

import com.badlogic.gdx.utils.Disposable
import com.microwavestudios.fortyfive.utils.Lifetime

interface StreamableAudio : Disposable {

    val playingLifetime: Lifetime

    fun play()
    fun pause()
    fun stop()
    fun setVolume(volume: Float)
    fun update()
    fun isPlaying(): Boolean
    fun setPan(pan: Float)

}

interface LoopableStreamableAudio : StreamableAudio {

    var looping: Boolean

}
