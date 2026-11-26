package com.microwavestudios.fortyfive.audio

import com.microwavestudios.fortyfive.utils.illegalState
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL11
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.ALC11
import org.lwjgl.openal.ALCdevice
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.IntBuffer

class AlAudioInterface : AudioInterface {

    override fun createContext() {
        val device = ALC10.alcOpenDevice(null as String?)
        if (device == MemoryUtil.NULL) {
            illegalState("Could not open OpenAL device")
        }
        val context = ALC10.alcCreateContext(device, null as IntBuffer?)
        if (context == MemoryUtil.NULL) {
            illegalState("Could not create OpenAL context")
        }
        require(ALC10.alcMakeContextCurrent(context)) {
            "Could not make OpenAL context current"
        }
        val caps = ALC.createCapabilities(device)
        ALC.setCapabilities(caps)
        AL.setCurrentThread(AL.createCapabilities(caps))
        AL11.alListener3f(AL11.AL_POSITION, 0f, 0f, 0f)
        AL11.alDistanceModel(AL11.AL_NONE)
        AL11.alDopplerFactor(0f)
    }

    override fun createSoundData(file: File): SoundData {
        checkFileExtension(file)
        return AlWavSoundData.createFromFile(file)
    }

    override fun preloadSoundData(file: File): Any {
        checkFileExtension(file)
        return AlWavSoundData.loadConstructionData(file)
    }

    override fun finishLoadingSoundData(preloaded: Any): SoundData {
        require(preloaded is AlWavSoundData.ConstructionData) {
            "invalid preloaded object"
        }
        return AlWavSoundData.createFromConstructionData(preloaded)
    }

    override fun createStreamableAudio(file: File): LoopableStreamableAudio {
        checkFileExtension(file)
        return AlStreamableAudio(file)
    }

    override fun createMultipartStreamableAudio(files: Sequence<File>): StreamableAudio =
        AlMultipartStreamableAudio(files)

    override fun createSynchronizedAudioStream(tracks: List<File>): LoopableStreamableAudio =
        AlSynchronizedAudioStream(tracks)

    private fun checkFileExtension(file: File) {
        require(file.extension == "wav") { "Unsupported audio format for file '$file'" }
    }

}
