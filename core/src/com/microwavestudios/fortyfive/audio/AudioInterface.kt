package com.microwavestudios.fortyfive.audio

import java.io.File

interface AudioInterface {

    fun createContext()

    fun createSoundData(file: File): SoundData

    fun preloadSoundData(file: File): Any

    fun finishLoadingSoundData(preloaded: Any): SoundData

    fun createStreamableAudio(file: File): StreamableAudio

}
