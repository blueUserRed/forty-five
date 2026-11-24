package com.microwavestudios.fortyfive.audio

import com.microwavestudios.fortyfive.utils.illegalState
import org.lwjgl.openal.AL10
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AlUtils {

    fun fixByteOrder(src: ByteBuffer, sampleSize: Int): ByteBuffer {
        val dest = ByteBuffer.allocateDirect(src.capacity())
        dest.order(ByteOrder.nativeOrder())

        if (sampleSize == 16) {
            val srcBufferShort = src.asShortBuffer()
            val destBufferShort = dest.asShortBuffer()
            while (srcBufferShort.hasRemaining()) destBufferShort.put(srcBufferShort.get())
        } else {
            while (src.hasRemaining()) dest.put(src.get())
        }
        dest.rewind()
        return dest
    }

    fun getAlFormat(channels: Int, sampleSize: Int, file: File): Int = when (channels) {
        1 if sampleSize == 8 -> AL10.AL_FORMAT_MONO8
        2 if sampleSize == 8 -> AL10.AL_FORMAT_STEREO8
        1 if sampleSize == 16 -> AL10.AL_FORMAT_MONO16
        2 if sampleSize == 16 -> AL10.AL_FORMAT_STEREO16
        else -> illegalState(
            "Unsupported wav file format for file: '${file.path}', format: $channels channels, $sampleSize bits"
        )
    }

}
