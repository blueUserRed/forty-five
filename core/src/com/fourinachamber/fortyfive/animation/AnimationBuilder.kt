package com.fourinachamber.fortyfive.animation

import com.fourinachamber.fortyfive.utils.Either
import com.fourinachamber.fortyfive.utils.eitherLeft
import com.fourinachamber.fortyfive.utils.eitherRight

class AnimationBuilderDSL {

    private var sequenceBuilder: (suspend SequenceScope<Int>.() -> Unit)? = null
    private val animations: MutableList<Either<String, AnimationPart>> = mutableListOf()

    lateinit var animationDrawable: AnimationDrawable

    fun deferredAnimation(name: String): Int {
        animations.add(name.eitherLeft())
        return animations.size - 1
    }

    fun stillFrame(name: String, duration: Int): Int {
        animations.add(StillFrameAnimationPart(name, duration).eitherRight())
        return animations.size - 1
    }

    suspend fun SequenceScope<Int>.loop(animation: Int, frameOffset: Int = 0) {
        animationDrawable.frameOffset = frameOffset
        while (true) yield(animation)
    }

    @Suppress("unused") // unused receiver is there to force the user to call this function in the order block
    fun SequenceScope<Int>.flipX(flipX: Boolean = true) {
        animationDrawable.flipX = flipX
    }

    @Suppress("unused") // unused receiver is there to force the user to call this function in the order block
    fun SequenceScope<Int>.flipY(flipY: Boolean = true) {
        animationDrawable.flipY = flipY
    }

    fun order(sequence: suspend SequenceScope<Int>.() -> Unit) {
        this.sequenceBuilder = sequence
    }

    fun finish(): AnimationDrawable = AnimationDrawable(animations, sequence(sequenceBuilder!!))
}

fun createAnimation(builder: AnimationBuilderDSL.() -> Unit): AnimationDrawable {
    val builderDSL = AnimationBuilderDSL()
    builder(builderDSL)
    val drawable = builderDSL.finish()
    builderDSL.animationDrawable = drawable
    return drawable
}
