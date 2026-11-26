package com.microwavestudios.fortyfive.audio

import com.microwavestudios.fortyfive.FortyFive
import org.lwjgl.openal.AL11
import com.microwavestudios.fortyfive.audio.AlUtils.alBool
import com.microwavestudios.fortyfive.utils.EndableLifetime
import com.microwavestudios.fortyfive.utils.Lifetime
import java.io.File

class AlSoundInstance(
    private val bufferId: Int,
    private val isStereo: Boolean,
    private val file: File
) : SoundInstance {

    private val sourceId: Int = AL11.alGenSources()

    override val playingLifetime: Lifetime
        field = EndableLifetime()

    private var shouldBePlaying: Boolean = false

    init {
        AL11.alSourcei(sourceId, AL11.AL_BUFFER, bufferId)
        AL11.alSourcef(sourceId, AL11.AL_ROLLOFF_FACTOR, 0f)
        if (isStereo) AL11.alSourcei(sourceId, AL11.AL_SOURCE_RELATIVE, alBool(false))
    }

    override fun setPan(pan: Float) {
        if (isStereo && pan != 0f) {
            FortyFive.logger.warn("SoundInstance", "Cant set pan on sound '$file', is stereo")
            return
        }
        AL11.alSource3f(sourceId, AL11.AL_POSITION, pan, 0f, 0f)
    }

    override fun setLooping(looping: Boolean) {
        AL11.alSourcei(sourceId, AL11.AL_LOOPING, alBool(looping))
    }

    override fun play() {
        shouldBePlaying = true
        AL11.alSourcePlay(sourceId)
    }

    override fun pause() {
        shouldBePlaying = false
        AL11.alSourcePause(sourceId)
    }

    override fun stop() {
        shouldBePlaying = false
        playingLifetime.die()
        AL11.alSourceStop(sourceId)
    }

    override fun isPlaying(): Boolean {
        return shouldBePlaying
    }

    override fun setVolume(volume: Float) {
        AL11.alSourcef(sourceId, AL11.AL_GAIN, volume)
    }

    override fun dispose() {
        AL11.alDeleteSources(sourceId)
    }

    override fun update() {
        val rawIsPlaying = AL11.alGetSourcei(sourceId, AL11.AL_SOURCE_STATE) == AL11.AL_PLAYING
        if (shouldBePlaying && !rawIsPlaying) {
            stop()
        }
    }

}
