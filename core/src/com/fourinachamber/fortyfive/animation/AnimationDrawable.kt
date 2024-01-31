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

// TODO: It would be cleaner if this class would be responsible for borrowing the parts it needs
// the current solution can cause problems if two drawables depend on the same animation and one is disposed when
// the other is still used
class AnimationDrawable(
    val animations: List<Either<String, AnimationPart>>,
    animationSequence: Sequence<Int>,
): BaseDrawable(), ResourceBorrower, Disposable {

    private var startTime: Long = -1

    private val animationIterator = animationSequence.iterator()

    private var currentAnimation: AnimationPart? = null

    val isRunning: Boolean
        get() = startTime != -1L

    var frameOffset: Int = 0

    var flipX: Boolean = false
    var flipY: Boolean = false

    private val loadedAnimations: List<AnimationPart> = animations.map {
        if (it is Either.Left) {
            ResourceManager.borrow(this, it.value)
            ResourceManager.get(this, it.value)
        } else {
            (it as Either.Right).value
        }
    }

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
        loadedAnimations.forEach { it.update() }
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
        loadedAnimations[num]
    } else {
        throw RuntimeException("animationSequence returned out of bounds index $num")
    }

    override fun dispose() {
        animations.forEach {
            if (it is Either.Left) {
                ResourceManager.giveBack(this, it.value)
            } else {
                it as Either.Right
                it.value.dispose()
            }
        }
    }
}

interface AnimationPart : Disposable {

    val duration: Int

    fun getFrame(progress: Int, frameOffset: Int = 0): Drawable?

    fun update() {}

}

data class StillFrameAnimationPart(
    val frameHandle: ResourceHandle,
    override val duration: Int
) : AnimationPart, ResourceBorrower {

    private val frame: Drawable by lazy {
        ResourceManager.borrow(this, frameHandle)
        ResourceManager.get(this, frameHandle)
    }

    override fun getFrame(progress: Int, frameOffset: Int): Drawable = frame

    override fun dispose() {
        ResourceManager.giveBack(this, frameHandle)
    }
}
