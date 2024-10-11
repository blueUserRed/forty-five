package com.fourinachamber.fortyfive.animation

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty

fun interface Interpolator<T> {
    fun interpolate(start: T, end: T, percent: Float): T
}

class FloatInterpolator() : Interpolator<Float> {

    override fun interpolate(start: Float, end: Float, percent: Float): Float = start + (end - start) * percent
}

object DefaultInterpolators {

    private val interpolators: Map<KClass<*>, Interpolator<*>> = mapOf(
        Float::class to FloatInterpolator()
    )

    fun <T : Any> getDefaultInterpolator(type: KClass<T>): Interpolator<T>? {
        interpolators.forEach { (interType, interpolator) ->
            @Suppress("UNCHECKED_CAST")
            if (interType.javaObjectType.isAssignableFrom(type.javaObjectType)) return interpolator as Interpolator<T>
        }
        return null
    }

}


data class AnimState<T>(
    val name: String,
    val value: T,
    val time: Int,
    val interpolation: Interpolation = Interpolation.linear
)

interface AbstractProperty<T> {
    fun get(): T
    fun set(value: T)

    companion object {

        fun <T> fromKotlin(property: KMutableProperty<T>): AbstractProperty<T> = object : AbstractProperty<T> {
            override fun get(): T = property.getter.call()
            override fun set(value: T) = property.setter.call(value)
        }

        fun <T> fromLambdas(getter: () -> T, setter: (T) -> Unit): AbstractProperty<T> = object : AbstractProperty<T> {
            override fun get(): T = getter()
            override fun set(value: T) = setter(value)
        }

    }
}

fun Actor.xPositionAbstractProperty(): AbstractProperty<Float> = AbstractProperty.fromLambdas(
    { getX() },
    { setX(it) }
)

class PropertyAnimation<T : Any>(
    private val actor: Actor,
    private val property: AbstractProperty<T>,
    private val propertyClass: KClass<T>,
    interpolator: Interpolator<T>? = DefaultInterpolators.getDefaultInterpolator(propertyClass),
    private vararg val states: AnimState<T>,
) {

    private val interpolator: Interpolator<T> = interpolator
        ?: throw RuntimeException("no interpolator specified and no default interpolator found")

    fun state(state: String) {
        val animState = states.find { it.name == state } ?: throw RuntimeException("Unknown state: $state")
        val animateTo = animState.value

        val initial = property.get()
        val action = object : TemporalAction() {

            override fun update(percent: Float) {
                val value = interpolator.interpolate(initial, animateTo, percent)
                property.set(value)
            }
        }
        action.duration = animState.time.toFloat() / 1000f
        action.interpolation = animState.interpolation
        actor.addAction(action)
    }

}
