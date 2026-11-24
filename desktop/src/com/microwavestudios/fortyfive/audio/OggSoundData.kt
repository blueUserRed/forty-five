package com.microwavestudios.fortyfive.audio

import com.badlogic.gdx.utils.Disposable
import com.microwavestudios.fortyfive.utils.illegalState
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL10
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.IntBuffer
import java.nio.ShortBuffer

class OggSoundData : Disposable {

    private val buffer: IntBuffer = BufferUtils.createIntBuffer(1)
    private val pcm: ShortBuffer

    init {
        AL10.alGenBuffers(buffer)
        val info = STBVorbisInfo.malloc()
        val mem = MemoryStack.stackPush()
        try {
            val error = mem.callocInt(1)
            val decoder = STBVorbis.stb_vorbis_open_filename(
                "C:\\Users\\marvi\\files\\Forty-Five\\forty-five\\assets\\blobs\\sounds\\card_destroy.ogg",
                error, null
            )
            if (decoder == MemoryUtil.NULL) {
                println(error[0])
                illegalState("Failed to open") // TODO: sdf
            }
            STBVorbis.stb_vorbis_get_info(decoder, info)
            val length = STBVorbis.stb_vorbis_stream_length_in_samples(decoder)
            val result = MemoryUtil.memAllocShort(length * info.channels())
            result.limit(
                STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, info.channels(), result) * info.channels()
            )
            pcm = result
            AL10.alBufferData(
                buffer.get(0),
                if (info.channels() == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16,
                pcm,
                info.sample_rate()
            )
        } finally {
            mem.close()
            info.close()
        }
    }

    fun createInstance(): AlSoundInstance = AlSoundInstance(buffer.get(0))

    override fun dispose() {
        AL10.alDeleteBuffers(buffer)
        MemoryUtil.memFree(pcm)
    }
}
