package com.fourinachamber.fortyfive.animation

import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Disposable

data class FrameAnimation(
    val frames: Array<out Drawable>,
    private val disposables: Iterable<Disposable>,
    val frameTime: Int,
    var frameOffset: Int,
) : AnimationPart {

    override val duration: Int
        get() = frameTime * frames.size

    override fun getFrame(progress: Int): Drawable {
        val frame = ((progress / frameTime + frameOffset) % frames.size).coerceAtLeast(0).coerceAtMost(frames.size)
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
                frameTime == other.frameTime
    }

    override fun hashCode(): Int {
        var result = frames.contentHashCode()
        result = 31 * result + disposables.hashCode()
        result = 31 * result + frameTime.hashCode()
        return result
    }

}