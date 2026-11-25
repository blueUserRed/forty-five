package com.microwavestudios.fortyfive.audio

import com.microwavestudios.fortyfive.FortyFive
import org.lwjgl.openal.AL10
import com.microwavestudios.fortyfive.audio.AlUtils.alBool
import com.microwavestudios.fortyfive.utils.EndableLifetime
import com.microwavestudios.fortyfive.utils.Lifetime
import java.io.File

class AlSoundInstance(
    private val bufferId: Int,
    private val isStereo: Boolean,
    private val file: File
) : SoundInstance {

    private val sourceId: Int = AL10.alGenSources()

    override val playingLifetime: Lifetime
        field = EndableLifetime()

    private var shouldBePlaying: Boolean = false

    init {
        AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId)
        AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 0f)
        if (isStereo) AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, alBool(false))
    }

    override fun setPan(pan: Float) {
        if (isStereo && pan != 0f) {
            FortyFive.logger.warn("SoundInstance", "Cant set pan on sound '$file', is stereo")
            return
        }
        AL10.alSource3f(sourceId, AL10.AL_POSITION, pan, 0f, 0f)
    }

    override fun setLooping(looping: Boolean) {
        AL10.alSourcei(sourceId, AL10.AL_LOOPING, alBool(looping))
    }

    override fun play() {
        shouldBePlaying = true
        AL10.alSourcePlay(sourceId)
    }

    override fun pause() {
        shouldBePlaying = false
        AL10.alSourcePause(sourceId)
    }

    override fun stop() {
        shouldBePlaying = false
        playingLifetime.die()
        AL10.alSourceStop(sourceId)
    }

    override fun isPlaying(): Boolean {
        return shouldBePlaying
    }

    override fun setVolume(volume: Float) {
        AL10.alSourcef(sourceId, AL10.AL_GAIN, volume)
    }

    override fun dispose() {
        AL10.alDeleteSources(sourceId)
    }

    override fun update() {
        val rawIsPlaying = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING
        if (shouldBePlaying && !rawIsPlaying) {
            stop()
        }
    }

}
