package com.microwavestudios.fortyfive.audio

import com.badlogic.gdx.utils.Disposable

interface SoundData : Disposable {

    fun createInstance(): SoundInstance

}
