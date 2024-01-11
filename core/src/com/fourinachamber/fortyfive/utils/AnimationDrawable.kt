package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils

class AnimationDrawable(
    val animations: List<FrameAnimation>,
    val animationSelector: (AnimationDrawable) -> Int?
): BaseDrawable(), Disposable {

    private var startTime: Long = -1

    init {
        if (animations.isEmpty()) throw RuntimeException("AnimationDrawable needs at least one Animation")
    }

    private var currentAnimation: FrameAnimation = animations[0]

    val isRunning: Boolean
        get() = startTime != -1L

    fun start() {
        startTime = TimeUtils.millis()
    }

    fun end() {
        startTime = -1
    }

    override fun draw(batch: Batch?, x: Float, y: Float, width: Float, height: Float) {
        if (!isRunning || batch == null) return
        val currentTime = TimeUtils.millis()
        var progress = currentTime - startTime
        if (progress > currentAnimation.duration) {
            switchAnimation()
            if (!isRunning) return
            progress = currentTime - startTime
        }
        val frame = currentAnimation.getFrame(progress)
        frame.draw(batch, x, y, width, height)
    }

    private fun switchAnimation() {
        val newAnimation = animationSelector(this) ?: run {
            end()
            return
        }
        if (newAnimation !in animations.indices) {
            throw RuntimeException("animationSelector returned an out of bounds index")
        }
        currentAnimation = animations[newAnimation]
        startTime = TimeUtils.millis()
    }

    override fun dispose() {
        animations.forEach(Disposable::dispose)
    }
}
