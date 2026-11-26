package com.microwavestudios.fortyfive.audio

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.audio.AlUtils.alBool
import com.microwavestudios.fortyfive.utils.EndableLifetime
import com.microwavestudios.fortyfive.utils.Lifetime
import org.lwjgl.openal.AL11
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

class AlMultipartStreamableAudio(
    private val files: Sequence<File>
) : StreamableAudio {

    private val buffers: IntArray = IntArray(numBuffers) {
        AL11.alGenBuffers()
    }

    private val fileIt = files.iterator()
    private val streams: Sequence<AudioInputStream> = files.map { AudioSystem.getAudioInputStream(it) }
    private val streamIt = streams.iterator()

    private var currentStream: AudioInputStream
    private var currentFile: File

    private val format: AudioFormat
    private val channels: Int
    private val sampleRate: Int
    private val sampleSize: Int
    private val alFormat: Int
    private val bufferSizeFrames: Int
    private val realBufferSize: Int
    private val frameSize: Int

    private val tmpArr: ByteArray

    private val sourceId = AL11.alGenSources()

    private var shouldBePlaying: Boolean = false

    private var endReached: Boolean = false

    override val playingLifetime: Lifetime
        field = EndableLifetime()

    init {
        AL11.alSourcef(sourceId, AL11.AL_ROLLOFF_FACTOR, 0f)
        require(streamIt.hasNext()) { "Empty sequence passed to AlMultipartStreamableAudio" }
        currentStream = streamIt.next()
        currentFile = fileIt.next()
        format = currentStream.format
        channels = format.channels
        sampleRate = format.sampleRate.toInt()
        sampleSize = format.sampleSizeInBits
        alFormat = AlUtils.getAlFormat(channels, sampleSize, currentFile)
        frameSize = format.frameSize
        bufferSizeFrames = targetBufferSize / frameSize
        realBufferSize = bufferSizeFrames * frameSize // <- not the same because of integer division
        tmpArr = ByteArray(realBufferSize)

        if (channels == 2) AL11.alSourcei(sourceId, AL11.AL_SOURCE_RELATIVE, alBool(false))

        fillBuffers(buffers)
        buffers.forEach { buffer ->
            AL11.alSourceQueueBuffers(sourceId, buffer)
        }
    }

    override fun setPan(pan: Float) {
        if (channels == 2 && pan != 0f) {
            FortyFive.logger.warn("SoundInstance", "Cant set pan on MultiPartAudio '$currentFile', is stereo")
            return
        }
        AL11.alSource3f(sourceId, AL11.AL_POSITION, pan, 0f, 0f)
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

    fun isStarved(): Boolean {
        val rawIsPlaying = AL11.alGetSourcei(sourceId, AL11.AL_SOURCE_STATE) == AL11.AL_PLAYING
        return shouldBePlaying && !rawIsPlaying
    }

    override fun update() {
        if (!shouldBePlaying) return
        if (endReached && isStarved()) {
            stop()
            return
        }
        val processedBuffers = AL11.alGetSourcei(sourceId, AL11.AL_BUFFERS_PROCESSED)
        repeat(processedBuffers) {
            val bufferId = AL11.alSourceUnqueueBuffers(sourceId)
            fillBuffer(bufferId, tmpArr)
            AL11.alSourceQueueBuffers(sourceId, bufferId)
        }
        if (isStarved()) play()
    }

    private fun fillBuffers(buffers: IntArray) {
        buffers.forEach { buffer ->
            fillBuffer(buffer, tmpArr)
        }
    }

    private fun nextStream(): Boolean {
        if (!streamIt.hasNext()) return false
        currentStream.close()
        currentStream = streamIt.next()
        currentFile = fileIt.next()
        val newFormat = currentStream.format
        require(newFormat.channels == channels) { "Property 'channels' doesn't match between files in MultipartAudio" }
        require(newFormat.sampleRate.toInt() == sampleRate) { "Property 'sampleRate' doesn't match between files in MultipartAudio" }
        require(newFormat.sampleSizeInBits == sampleSize) { "Property 'sampleSize' doesn't match between files in MultipartAudio" }
        require(newFormat.frameSize == frameSize) { "Property 'frameSize' doesn't match between files in MultipartAudio" }
        return true
    }

    private fun fillBuffer(buffer: Int, tmpArr: ByteArray) {
        val targetSize = realBufferSize
        var filled = 0
        while (filled < targetSize) {
            val n = currentStream.read(tmpArr, filled, targetSize - filled)
            val finished = n == -1
            if (finished) {
                val hasNewStream = nextStream()
                if (hasNewStream) continue
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
        AL11.alBufferData(
            buffer,
            alFormat,
            dataBuffer,
            sampleRate
        )
    }

    override fun dispose() {
        buffers.forEach { buffer ->
            AL11.alDeleteBuffers(buffer)
        }
        AL11.alDeleteSources(sourceId)
        currentStream.close()
    }

    companion object {
        const val numBuffers = 4
        const val targetBufferSize = 10_000
    }

}
