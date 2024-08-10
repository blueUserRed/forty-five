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
import com.fourinachamber.fortyfive.utils.Either
import com.fourinachamber.fortyfive.utils.Lifetime
import com.fourinachamber.fortyfive.utils.Promise

class AnimationDrawable(
    val animations: List<AnimationPart>,
    animationSequence: Sequence<Int>,
): BaseDrawable() {

    private var startTime: Long = -1

    private val animationIterator = animationSequence.iterator()

    private var currentAnimation: AnimationPart? = null

    val isRunning: Boolean
        get() = startTime != -1L

    var frameOffset: Int = 0

    var flipX: Boolean = false
    var flipY: Boolean = false

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
        val frame = currentAnimation.getFrame(progress.toInt(), frameOffset) ?: return
        frame.draw(
            batch,
            if (flipX) x + width else x,
            if (flipY) y + height else y,
            if (flipX) -width else width,
            if (flipY) -height else height
        )
    }

    fun update() {
        animations.forEach { it.update() }
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

    override fun getMinWidth(): Float = currentAnimation?.width() ?: 0f

    override fun getMinHeight(): Float = currentAnimation?.height() ?: 0f

    private fun getAnimation(num: Int): AnimationPart = if (num in animations.indices) {
        animations[num]
    } else {
        throw RuntimeException("animationSequence returned out of bounds index $num")
    }
}

interface AnimationPart : Disposable {

    val duration: Int

    fun getFrame(progress: Int, frameOffset: Int = 0): Drawable?

    fun update() {}

    fun width(): Float
    fun height(): Float

}

data class StillFrameAnimationPart(
    val frameHandle: ResourceHandle,
    val borrower: ResourceBorrower,
    val lifetime: Lifetime,
    override val duration: Int
) : AnimationPart, ResourceBorrower {

    private val frame: Promise<Drawable> = ResourceManager.request(borrower, lifetime, frameHandle)

    override fun getFrame(progress: Int, frameOffset: Int): Drawable? = frame.getOrNull()

    override fun dispose() {
        ResourceManager.giveBack(this, frameHandle)
    }

    override fun width(): Float = frame.getOrNull()?.minWidth ?: 0f
    override fun height(): Float = frame.getOrNull()?.minHeight ?: 0f
}
