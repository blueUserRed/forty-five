package com.microwavestudios.fortyfive.audio

import org.lwjgl.openal.AL10
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioSystem


class AlWavSoundData private constructor(
    dataBuffer: ByteBuffer,
    alFormat: Int,
    sampleRate: Int,
) : SoundData {

    private val buffer: Int = AL10.alGenBuffers()

    init {
        AL10.alBufferData(
            buffer,
            alFormat,
            dataBuffer,
            sampleRate
        )
    }

    override fun createInstance(): AlSoundInstance = AlSoundInstance(buffer)

    override fun dispose() {
        AL10.alDeleteBuffers(buffer)
    }

    data class ConstructionData(
        internal val dataBuffer: ByteBuffer,
        internal val alFormat: Int,
        internal val sampleRate: Int,
    )

    companion object {

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

            val srcBuffer = ByteBuffer.wrap(data)
            srcBuffer.order(ByteOrder.LITTLE_ENDIAN)
            val dataBuffer = AlUtils.fixByteOrder(srcBuffer, sampleSize)
            return ConstructionData(dataBuffer, alFormat, sampleRate)
        }

        fun createFromConstructionData(constructionData: ConstructionData): AlWavSoundData {
            val (dataBuffer, alFormat, sampleRate) = constructionData
            return AlWavSoundData(dataBuffer, alFormat, sampleRate)
        }

        fun createFromFile(file: File): AlWavSoundData = createFromConstructionData(loadConstructionData(file))

    }

}
