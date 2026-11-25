package com.microwavestudios.fortyfive.audio

import com.microwavestudios.fortyfive.FortyFive
import org.lwjgl.openal.AL10
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import com.microwavestudios.fortyfive.audio.AlUtils.alBool
import com.microwavestudios.fortyfive.utils.EndableLifetime
import com.microwavestudios.fortyfive.utils.Lifetime

class AlStreamableAudio(
    private val file: File
) : StreamableAudio {

    private val buffers: IntArray = IntArray(numBuffers) {
        AL10.alGenBuffers()
    }

    private var stream: AudioInputStream
    private val format: AudioFormat
    private val channels: Int
    private val sampleRate: Int
    private val sampleSize: Int
    private val alFormat: Int
    private val bufferSizeFrames: Int
    private val realBufferSize: Int

    private val tmpArr: ByteArray

    private val sourceId = AL10.alGenSources()

    private var shouldBePlaying: Boolean = false

    private var endReached: Boolean = false
    override var looping: Boolean = false

    override val playingLifetime: Lifetime
        field = EndableLifetime()

    init {
        AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 0f)
        stream = AudioSystem.getAudioInputStream(file)
        format = stream.format
        channels = format.channels
        sampleRate = format.sampleRate.toInt()
        sampleSize = format.sampleSizeInBits
        alFormat = AlUtils.getAlFormat(channels, sampleSize, file)
        bufferSizeFrames = targetBufferSize / format.frameSize
        realBufferSize = bufferSizeFrames * format.frameSize // <- not the same because of integer division
        tmpArr = ByteArray(realBufferSize)

        if (channels == 2) AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, alBool(false))

        fillBuffers(buffers)
        buffers.forEach { buffer ->
            AL10.alSourceQueueBuffers(sourceId, buffer)
        }
    }

    override fun setPan(pan: Float) {
        if (channels == 2 && pan != 0f) {
            FortyFive.logger.warn("SoundInstance", "Cant set pan on sound '$file', is stereo")
            return
        }
        AL10.alSource3f(sourceId, AL10.AL_POSITION, pan, 0f, 0f)
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

    fun isStarved(): Boolean {
        val rawIsPlaying = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING
        return shouldBePlaying && !rawIsPlaying
    }

    override fun update() {
        if (!shouldBePlaying) return
        if (endReached && isStarved()) {
            stop()
            return
        }
        val processedBuffers = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED)
        repeat(processedBuffers) {
            val bufferId = AL10.alSourceUnqueueBuffers(sourceId)
            fillBuffer(bufferId, tmpArr)
            AL10.alSourceQueueBuffers(sourceId, bufferId)
        }
        if (isStarved()) play()
    }

    private fun resetStream() {
        stream.close()
        stream = AudioSystem.getAudioInputStream(file)
    }

    private fun fillBuffers(buffers: IntArray) {
        buffers.forEach { buffer ->
            fillBuffer(buffer, tmpArr)
        }
    }

    private fun fillBuffer(buffer: Int, tmpArr: ByteArray) {
        val targetSize = realBufferSize
        var filled = 0
        while (filled < targetSize) {
            val n = stream.read(tmpArr, filled, targetSize - filled)
            val finished = n == -1
            if (finished && looping) {
                resetStream()
                continue
            }
            if (finished && !looping) {
                endReached = true
                break
            }
            filled += n
        }
        if (filled == 0) return
        var srcBuffer = ByteBuffer.wrap(tmpArr)
        srcBuffer = srcBuffer.slice(0, filled)
        srcBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val dataBuffer = AlUtils.fixByteOrder(srcBuffer, sampleSize)
        AL10.alBufferData(
            buffer,
            alFormat,
            dataBuffer,
            sampleRate
        )
    }

    override fun dispose() {
        buffers.forEach { buffer ->
            AL10.alDeleteBuffers(buffer)
        }
        AL10.alDeleteSources(sourceId)
        stream.close()
    }

    companion object {
        const val numBuffers = 4
        const val targetBufferSize = 10_000

        const val approxMemUsage = (numBuffers + 1) * targetBufferSize
    }

}
