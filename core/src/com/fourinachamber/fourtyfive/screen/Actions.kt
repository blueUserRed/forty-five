package com.fourinachamber.fourtyfive.screen

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import kotlin.math.cos
import kotlin.math.sin

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
