package com.fourinachamber.fortyfive.animation

import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.utils.Either
import com.fourinachamber.fortyfive.utils.Lifetime
import com.fourinachamber.fortyfive.utils.eitherLeft
import com.fourinachamber.fortyfive.utils.eitherRight
import java.lang.RuntimeException

class AnimationBuilderDSL {

    private var sequenceBuilder: (suspend SequenceScope<Int>.() -> Unit)? = null
    private val animations: MutableList<(ResourceBorrower, Lifetime) -> AnimationPart> = mutableListOf()

    lateinit var animationDrawable: AnimationDrawable

    fun deferredAnimation(name: String): Int {
        animations.add { resourceBorrower, lifetime ->
            // forceGet is not an issue here because the DeferredFrameAnimation doesn't really take any time to load,
            // it just requests other resources
            ResourceManager.forceGet(resourceBorrower, lifetime, name)
        }
        return animations.size - 1
    }

    fun stillFrame(name: String, duration: Int): Int {
        animations.add { resourceBorrower, lifetime ->
            StillFrameAnimationPart(name, resourceBorrower, lifetime, duration)
        }
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

    fun finish(borrower: ResourceBorrower, lifetime: Lifetime): AnimationDrawable {
        val animations = animations.map { it(borrower, lifetime) }
        val sequenceBuilder = sequenceBuilder ?: throw RuntimeException("'order' must be called inside the animation builder")
        return AnimationDrawable(animations, sequence(sequenceBuilder))
    }
}

fun createAnimation(
    borrower: ResourceBorrower,
    lifetime: Lifetime,
    builder: AnimationBuilderDSL.() -> Unit
): AnimationDrawable {
    val builderDSL = AnimationBuilderDSL()
    builder(builderDSL)
    val drawable = builderDSL.finish(borrower, lifetime)
    builderDSL.animationDrawable = drawable
    return drawable
}
