package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.utils.plus
import com.fourinachamber.fortyfive.utils.times

class CustomMoveByAction(
    var target: OffSettable? = null,
    interpolation: Interpolation = Interpolation.linear,
    var relX: Float = 0F,
    var relY: Float = 0F,
    duration: Float = 1000F,
) : RelativeTemporalAction() {
    init {
        super.setInterpolation(interpolation)
        super.setDuration(duration / 1000)
    }

    private var lastDist = 0F

    override fun updateRelative(percentDelta: Float) {
    }

    override fun update(interpol: Float) {
        super.update(interpol)
        val target = this.target ?: return
        target as OffSettable
        val curDist = interpol - lastDist
        target.offsetX += curDist * relX
        target.offsetY += curDist * relY
        lastDist = interpol
    }
}

class BounceOutAction(
    private val initialVelocity: Vector2,
    private val initialAngularVelocity: Float,
    private val force: Vector2,
    private val angularForce: Float,
    private val duration: Int
) : Action() {

    private var startTime: Long? = null

    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var initialRotation: Float = 0f

    private var velocity: Vector2 = initialVelocity
    private var angularVelocity: Float = initialAngularVelocity

    var isComplete: Boolean = false
        private set

    override fun act(delta: Float): Boolean {
        if (isComplete) return true
        if (startTime == null) {
            startTime = TimeUtils.millis()
            start()
        }
        val startTime = startTime!!
        update()
        if (startTime + duration < TimeUtils.millis()) {
            end()
            isComplete = true
        }
        return isComplete
    }

    private fun update() {
        val delta = Gdx.graphics.deltaTime
        actor.x += velocity.x * delta
        actor.y += velocity.y * delta
        velocity += force * delta
        actor.rotation += angularVelocity * delta
        angularVelocity += angularForce * delta
    }

    private fun start() {
        initialX = actor.x
        initialY = actor.y
        initialRotation = actor.rotation
    }

    private fun end() {
        actor.x = initialX
        actor.y = initialY
        actor.rotation = initialRotation
    }

}
