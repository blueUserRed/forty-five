package com.microwavestudios.fortyfive.screen.actors

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.animation.DefaultInterpolators
import com.microwavestudios.fortyfive.utils.plus
import com.microwavestudios.fortyfive.utils.times
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty

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
        target.drawOffsetX += curDist * relX
        target.drawOffsetY += curDist * relY
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

class PropertyAction<T>(
    val typeClass: KClass<T>,
    val obj: Any,
    val getter: () -> T,
    val setter: (T) -> Unit,
    val end: T,
    val invalidateHierarchyOf: Layout? = null
): TemporalAction() where T : Any {

    constructor(
        typeClass: KClass<T>,
        obj: Any,
        property: KMutableProperty<T>,
        end: T,
        invalidateHierarchyOf: Layout? = null
    ) : this(
        typeClass,
        obj,
        { property.getter.call() },
        { value -> property.setter.call(value) },
        end,
        invalidateHierarchyOf
    )

    private var initialValue: T? = null

    override fun begin() {
        initialValue = getter()
        super.begin()
    }

    override fun update(percent: Float) {
        val interpolator = DefaultInterpolators.getDefaultInterpolator(typeClass)
        requireNotNull(interpolator) { "no interpolator found for $typeClass" }
        val result = interpolator.interpolate(initialValue!!, end, percent)
        setter(result)
        invalidateHierarchyOf?.invalidateHierarchy()
    }

}
