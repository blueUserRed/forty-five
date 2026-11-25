package com.microwavestudios.fortyfive.audio

import com.badlogic.gdx.utils.Disposable
import com.microwavestudios.fortyfive.utils.Lifetime

interface SoundInstance : Disposable {

    val playingLifetime: Lifetime

    fun setLooping(looping: Boolean)
    fun play()
    fun pause()
    fun stop()
    fun setVolume(volume: Float)
    fun isPlaying(): Boolean
    fun update()
    fun setPan(pan: Float)

}
