package com.microwavestudios.fortyfive.audio

import org.lwjgl.openal.AL10

class AlSoundInstance(private val bufferId: Int) : SoundInstance {

    private val sourceId: Int = AL10.alGenSources()

    init {
        AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, alBool(false))
        AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId)
    }

    override fun setLooping(looping: Boolean) {
        AL10.alSourcei(sourceId, AL10.AL_LOOPING, alBool(looping))
    }

    override fun play() {
        AL10.alSourcePlay(sourceId)
    }

    override fun pause() {
        AL10.alSourcePause(sourceId)
    }

    override fun stop() {
        AL10.alSourceStop(sourceId)
    }

    override fun setVolume(volume: Float) {
        AL10.alSourcef(sourceId, AL10.AL_GAIN, volume)
    }

    override fun dispose() {
        AL10.alDeleteSources(sourceId)
    }

    private fun alBool(bool: Boolean): Int = if (bool) AL10.AL_TRUE else AL10.AL_FALSE
}
