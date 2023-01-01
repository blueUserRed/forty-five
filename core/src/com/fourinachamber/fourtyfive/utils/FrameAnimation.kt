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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameAnimation

        return frames.contentEquals(other.frames) &&
                textures == other.textures &&
                initialFrame == other.initialFrame &&
                frameTime == other.frameTime
    }

    override fun hashCode(): Int {
        var result = frames.contentHashCode()
        result = 31 * result + textures.hashCode()
        result = 31 * result + initialFrame.hashCode()
        result = 31 * result + frameTime.hashCode()
        return result
    }

}