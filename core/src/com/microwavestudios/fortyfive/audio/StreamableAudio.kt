package com.microwavestudios.fortyfive.audio

import com.badlogic.gdx.utils.Disposable

interface StreamableAudio : Disposable {

    var looping: Boolean

    fun play()
    fun pause()
    fun stop()
    fun setVolume(volume: Float)
    fun update()
    fun isPlaying(): Boolean

}
