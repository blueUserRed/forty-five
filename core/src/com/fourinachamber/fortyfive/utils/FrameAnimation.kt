package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Disposable

data class FrameAnimation(
    val frames: Array<Drawable>,
    private val disposables: Iterable<Disposable>,
    val initialFrame: Int,
    val frameTime: Int
) : Disposable {

    val duration: Int
        get() = frameTime * frames.size

    init {
        if (initialFrame !in frames.indices) throw RuntimeException("frameOffset must be a valid index into frames")
    }

    fun getFrame(progress: Long): Drawable {
        val frame = (progress / frameTime).toInt().coerceAtLeast(0).coerceAtMost(frames.size)
        return frames[frame]
    }

    override fun dispose() {
        disposables.forEach(Disposable::dispose)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameAnimation

        return frames.contentEquals(other.frames) &&
                disposables == other.disposables &&
                initialFrame == other.initialFrame &&
                frameTime == other.frameTime
    }

    override fun hashCode(): Int {
        var result = frames.contentHashCode()
        result = 31 * result + disposables.hashCode()
        result = 31 * result + initialFrame.hashCode()
        result = 31 * result + frameTime.hashCode()
        return result
    }

}