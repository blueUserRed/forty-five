package com.fourinachamber.fourtyfive.utils

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable

data class FrameAnimation(
    val frames: Array<TextureRegion>,
    val textures: Iterable<Texture>,
    val initialFrame: Int,
    val frameTime: Int
) : Disposable {

    init {
        if (initialFrame !in frames.indices) throw RuntimeException("frameOffset must be a valid index into frames")
    }

    override fun dispose() {
        textures.forEach(Disposable::dispose)
    }

}