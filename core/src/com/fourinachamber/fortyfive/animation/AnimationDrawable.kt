package com.fourinachamber.fortyfive.animation

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen

class AnimationDrawable(
    val animations: List<AnimationPart>,
    animationSequence: Sequence<Int>
): BaseDrawable(), Disposable {

    private var startTime: Long = -1

    private val animationIterator = animationSequence.iterator()

    private var currentAnimation: AnimationPart? = null

    val isRunning: Boolean
        get() = startTime != -1L

    init {
        if (animations.isEmpty()) throw RuntimeException("AnimationDrawable needs at least one Animation")
    }

    fun start() {
        startTime = TimeUtils.millis()
    }

    fun end() {
        startTime = -1
        currentAnimation = null
    }

    override fun draw(batch: Batch?, x: Float, y: Float, width: Float, height: Float) {
        if (!isRunning || batch == null) return

        val currentTime = TimeUtils.millis()
        var progress = currentTime - startTime
        var currentAnimation = currentAnimation ?: nextAnimation() ?: return
        if (progress > currentAnimation.duration) {
            currentAnimation = nextAnimation() ?: return
            progress = 0
        }
        val frame = currentAnimation.getFrame(progress.toInt()) ?: return
        frame.draw(batch, x, y, width, height)
    }

    private fun nextAnimation(): AnimationPart? {
        if (!animationIterator.hasNext()) {
            end()
            return null
        }
        val anim = getAnimation(animationIterator.next())
        currentAnimation = anim
        startTime = TimeUtils.millis()
        return anim
    }

    private fun getAnimation(num: Int): AnimationPart = if (num in animations.indices) {
        animations[num]
    } else {
        throw RuntimeException("animationSequence returned out of bounds index $num")
    }

    override fun dispose() {
        animations.forEach(Disposable::dispose)
    }
}

interface AnimationPart : Disposable {

    val duration: Int

    fun getFrame(progress: Int): Drawable?

}

data class StillFrameAnimationPart(
    val frameHandle: ResourceHandle,
    override val duration: Int
) : AnimationPart, ResourceBorrower {

    private val frame: Drawable by lazy {
        ResourceManager.borrow(this, frameHandle)
        ResourceManager.get(this, frameHandle)
    }

    override fun getFrame(progress: Int): Drawable = frame

    override fun dispose() {
        ResourceManager.giveBack(this, frameHandle)
    }
}
