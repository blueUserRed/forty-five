package com.microwavestudios.fortyfive.audio

import com.badlogic.gdx.utils.Disposable

interface SoundInstance : Disposable {

    fun setLooping(looping: Boolean)
    fun play()
    fun pause()
    fun stop()
    fun setVolume(volume: Float)

}
