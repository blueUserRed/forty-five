package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.MoveByAction
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.fourinachamber.fortyfive.screen.general.customActor.AnimationActor
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shakes an actor
 * @param xShake controls the amplitude in the x-direction
 * @param yShake controls the amplitude in the y-direction
 * @param xSpeedMultiplier controls the shake speed in the x-direction
 * @param ySpeedMultiplier controls the shake speed in the y-direction
 */
class ShakeActorAction(
    private val xShake: Float,
    private val yShake: Float,
    private val xSpeedMultiplier: Float,
    private val ySpeedMultiplier: Float
) : TemporalAction() {

    private var startPos: Vector2 = Vector2()

    override fun begin() {
        super.begin()
        val target = target
        if (target is AnimationActor) target.inAnimation = true
        startPos = Vector2(target.x, target.y)
    }

    override fun end() {
        super.end()
        val target = target
        if (target is AnimationActor) target.inAnimation = false
        target.x = startPos.x
        target.y = startPos.y
    }

    override fun update(percent: Float) {
        target.setPosition(
            startPos.x + sin(percent * 100.0 * xSpeedMultiplier).toFloat() * xShake,
            startPos.y + cos(percent * 100.0 * ySpeedMultiplier).toFloat() * yShake
        )
    }

}

/**
 * works like [MoveByAction], buts sets the inAnimation property if the actor is an [AnimationActor]
 */
class CustomMoveByAction : MoveByAction() {

    override fun begin() {
        super.begin()
        if (target is AnimationActor) (target as AnimationActor).inAnimation = true
    }

    override fun end() {
        super.end()
        if (target is AnimationActor) (target as AnimationActor).inAnimation = false
    }
}
