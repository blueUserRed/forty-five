package com.microwavestudios.fortyfive.audio

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.audio.AlUtils.alBool
import com.microwavestudios.fortyfive.utils.EndableLifetime
import com.microwavestudios.fortyfive.utils.Lifetime
import com.microwavestudios.fortyfive.utils.Utils
import org.lwjgl.openal.AL11
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.abs

@OptIn(ExperimentalStdlibApi::class)
class AlSynchronizedAudioStream(
    trackFiles: List<File>
) : LoopableStreamableAudio {

    private val tracks: List<Track> = trackFiles.map { Track(it) }
    private val sources: IntArray = tracks.map { it.sourceId }.toIntArray()

    override var looping: Boolean = false
        set(value) {
            field = value
            tracks.forEach { it.looping = value }
        }

    override val playingLifetime: Lifetime
        field = EndableLifetime()

    private var shouldBePlaying: Boolean = false

    init {
        require(tracks.isNotEmpty()) { "AlSynchronizedAudioStream constructed without tracks" }
        require(tracks.allEqualBy { it.sampleSize })
        require(tracks.allEqualBy { it.alFormat })
        require(tracks.allEqualBy { it.frameSize })
    }

    override fun play() {
        shouldBePlaying = true
        AL11.alSourcePlayv(sources)
    }

    override fun pause() {
        shouldBePlaying = false
        AL11.alSourcePausev(sources)
    }

    override fun stop() {
        shouldBePlaying = false
        playingLifetime.die()
        AL11.alSourceStopv(sources)
    }

    override fun isPlaying(): Boolean {
        return shouldBePlaying
    }

    override fun setVolume(volume: Float) {
        tracks.forEach { it.setVolume(volume) }
    }

    override fun update() {
        if (!shouldBePlaying) return
        if (tracks.any { it.endReached }) {
            stop()
            return
        }

        val unqueueableBuffers = tracks.minOf { it.numProcessedBuffers() }
        if (unqueueableBuffers != 0) tracks.forEach { it.unqueueBuffers(unqueueableBuffers) }
        val buffersToRefill = tracks.first().numUsableBuffers() // should always be synced
        tracks.forEach { it.fillBuffers(buffersToRefill) }

        if (tracks.all { !it.rawIsPlaying() }) { // all starved
            play()
        } else {
            tracks.forEach {
                if (!it.rawIsPlaying()) AL11.alSourcePlay(it.sourceId)
            }
        }
        val sampleOffset = AL11.alGetSourcei(tracks.first().sourceId, AL11.AL_SAMPLE_OFFSET)
        val bufferNum = tracks.first().curBufferNum()
        tracks.drop(1).forEach {
            if (it.curBufferNum() != bufferNum) return@forEach
            val otherOffset = AL11.alGetSourcei(it.sourceId, AL11.AL_SAMPLE_OFFSET)
            val offsetDiff = abs(sampleOffset - otherOffset)
            if (offsetDiff != 0) println(offsetDiff)
            if (offsetDiff < 20) return@forEach
            AL11.alSourcei(it.sourceId, AL11.AL_SAMPLE_OFFSET, sampleOffset)
        }
    }

    override fun setPan(pan: Float) {
        tracks.forEach { it.setPan(pan) }
    }

    override fun dispose() {
        stop()
        tracks.forEach { it.dispose() }
    }


    private class Track(private val file: File) {

        private val buffers: IntArray = IntArray(numBuffers) {
            AL11.alGenBuffers()
        }

        private val usableBuffers: MutableList<Int> = mutableListOf()

        var stream: AudioInputStream
        val format: AudioFormat
        val channels: Int
        val sampleRate: Int
        val sampleSize: Int
        val alFormat: Int
        val bufferSizeFrames: Int
        val realBufferSize: Int
        val frameSize: Int

        private val tmpArr: ByteArray

        val sourceId = AL11.alGenSources()

        var endReached: Boolean = false
        var looping: Boolean = false

        init {
            AL11.alSourcef(sourceId, AL11.AL_ROLLOFF_FACTOR, 0f)
            stream = AudioSystem.getAudioInputStream(file)
            format = stream.format
            channels = format.channels
            sampleRate = format.sampleRate.toInt()
            sampleSize = format.sampleSizeInBits
            alFormat = AlUtils.getAlFormat(channels, sampleSize, file)
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

        fun numUsableBuffers(): Int = usableBuffers.size

        fun curBufferNum(): Int {
            val curBuffer = AL11.alGetSourcei(sourceId, AL11.AL_BUFFER)
            return buffers.indexOf(curBuffer)
        }

        fun setPan(pan: Float) {
            if (channels == 2 && pan != 0f) {
                FortyFive.logger.warn("SoundInstance", "Cant set pan on sound '$file', is stereo")
                return
            }
            AL11.alSource3f(sourceId, AL11.AL_POSITION, pan, 0f, 0f)
        }

        fun setVolume(volume: Float) {
            AL11.alSourcef(sourceId, AL11.AL_GAIN, volume)
        }

        fun rawIsPlaying(): Boolean {
            return AL11.alGetSourcei(sourceId, AL11.AL_SOURCE_STATE) == AL11.AL_PLAYING
        }

        fun numProcessedBuffers(): Int =
            AL11.alGetSourcei(sourceId, AL11.AL_BUFFERS_PROCESSED)

        fun unqueueBuffers(num: Int) {
            repeat(num) {
                val bufferId = AL11.alSourceUnqueueBuffers(sourceId)
                usableBuffers.add(bufferId)
            }
        }

        fun fillBuffers(numBuffers: Int) {
            repeat(numBuffers) {
                val buffer = usableBuffers.removeLast()
                fillBuffer(buffer, tmpArr)
                AL11.alSourceQueueBuffers(sourceId, buffer)
            }
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
            AL11.alBufferData(
                buffer,
                alFormat,
                dataBuffer,
                sampleRate
            )
        }

        fun dispose() {
            buffers.forEach { buffer ->
                AL11.alDeleteBuffers(buffer)
            }
            AL11.alDeleteSources(sourceId)
            stream.close()
        }
    }

    companion object {
        const val numBuffers = 4
        const val targetBufferSize = 10_000
    }
}
