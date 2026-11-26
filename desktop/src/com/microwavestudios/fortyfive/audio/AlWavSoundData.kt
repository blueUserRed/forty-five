package com.microwavestudios.fortyfive.audio

import com.microwavestudios.fortyfive.FortyFive
import org.lwjgl.openal.AL11
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioSystem


class AlWavSoundData private constructor(
    dataBuffer: ByteBuffer,
    private val alFormat: Int,
    sampleRate: Int,
    private val file: File
) : SoundData {

    private val buffer: Int = AL11.alGenBuffers()

    init {
        AL11.alBufferData(
            buffer,
            alFormat,
            dataBuffer,
            sampleRate
        )
    }

    override fun createInstance(): AlSoundInstance = AlSoundInstance(
        buffer,
        AlUtils.isStereo(alFormat),
        file
    )

    override fun dispose() {
        AL11.alDeleteBuffers(buffer)
    }

    data class ConstructionData(
        internal val dataBuffer: ByteBuffer,
        internal val alFormat: Int,
        internal val sampleRate: Int,
        internal val file: File,
    )

    companion object {

        private const val logTag = "AlSoundData"

        /**
         * loads all data necessary for creating a sound into memory and returns it.
         * Save for calling from all threads
         */
        fun loadConstructionData(file: File): ConstructionData {
            val stream = AudioSystem.getAudioInputStream(file)
            val format = stream.format
            val channels = format.channels
            val sampleRate = format.sampleRate.toInt()
            val sampleSize = format.sampleSizeInBits
            val alFormat = AlUtils.getAlFormat(channels, sampleSize, file)
            val data = stream.readAllBytes()
            stream.close()

            if (data.size > AlStreamableAudio.approxMemUsage) {
                FortyFive.logger.warn(
                    logTag,
                    "Audio '$file' takes ${data.size} bytes in memory. Consider streaming it instead"
                )
            }

            val srcBuffer = ByteBuffer.wrap(data)
            srcBuffer.order(ByteOrder.LITTLE_ENDIAN)
            val dataBuffer = AlUtils.fixByteOrder(srcBuffer, sampleSize)
            return ConstructionData(dataBuffer, alFormat, sampleRate, file)
        }

        fun createFromConstructionData(constructionData: ConstructionData): AlWavSoundData {
            val (dataBuffer, alFormat, sampleRate, file) = constructionData
            return AlWavSoundData(dataBuffer, alFormat, sampleRate, file)
        }

        fun createFromFile(file: File): AlWavSoundData = createFromConstructionData(loadConstructionData(file))

    }

}
