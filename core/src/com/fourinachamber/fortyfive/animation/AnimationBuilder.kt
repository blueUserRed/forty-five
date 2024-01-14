package com.fourinachamber.fortyfive.animation

import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager

class AnimationBuilderDSL(private val borrower: ResourceBorrower) {

    private var sequenceBuilder: (suspend SequenceScope<Int>.() -> Unit)? = null
    private val animations: MutableList<AnimationPart> = mutableListOf()

    lateinit var animationDrawable: AnimationDrawable

    fun deferredAnimation(name: String, frameOffset: Int = 0): Int {
        ResourceManager.borrow(borrower, name)
        val anim = ResourceManager.get<DeferredFrameAnimation>(borrower, name)
        anim.frameOffset = frameOffset
        animations.add(anim)
        return animations.size - 1
    }

    fun stillFrame(name: String, duration: Int): Int {
        animations.add(StillFrameAnimationPart(name, duration))
        return animations.size - 1
    }

    suspend fun SequenceScope<Int>.loop(animation: Int, timeOffset: Int = 0) {
        if (timeOffset != 0) {
            yield(animation)
            animationDrawable.skipInCurrentAnimation(timeOffset)
        }
        while (true) yield(animation)
    }

    fun order(sequence: suspend SequenceScope<Int>.() -> Unit) {
        this.sequenceBuilder = sequence
    }

    fun finish(): AnimationDrawable = AnimationDrawable(animations, sequence(sequenceBuilder!!))
}

fun createAnimation(borrower: ResourceBorrower, builder: AnimationBuilderDSL.() -> Unit): AnimationDrawable {
    val builderDSL = AnimationBuilderDSL(borrower)
    builder(builderDSL)
    val drawable = builderDSL.finish()
    builderDSL.animationDrawable = drawable
    return drawable
}
