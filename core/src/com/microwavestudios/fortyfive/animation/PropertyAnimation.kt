package com.microwavestudios.fortyfive.animation

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.utils.Layout
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

fun Actor.yPositionAbstractProperty(): AbstractProperty<Float> = AbstractProperty.fromLambdas(
    { getY() },
    { setY(it) }
)

class PropertyAnimation<T : Any>(
    private val actor: Actor,
    private val property: AbstractProperty<T>,
    private val propertyClass: KClass<T>,
    defaultTime: Int,
    defaultInterpolation: Interpolation,
    initialState: String,
    // only slightly confusing naming
    interpolator: Interpolator<T>? = DefaultInterpolators.getDefaultInterpolator(propertyClass),
    private val invalidate: Boolean = false,
    private val invalidateHierarchy: Boolean = false,
    private val invalidateParent: Boolean = false,
    vararg states: AnimState<T>,
) {

    private val interpolator: Interpolator<T> = interpolator
        ?: throw RuntimeException("no interpolator specified and no default interpolator found")

    private val transitions: MutableList<AnimTransition> = mutableListOf()

    private var currentState: String = initialState

    private val defaultTransition = AnimTransition("", "", defaultTime, defaultInterpolation)

    private val states: MutableMap<String, AnimState<T>> = states.associateBy { it.name }.toMutableMap()

    init {
        property.set(findState(initialState).value)
        doInvalidate(actor)
    }

    fun transition(from: String, to: String, time: Int, interpolation: Interpolation) {
        val transition = AnimTransition(from, to, time, interpolation)
        transitions.add(transition)
    }

    fun state(state: String) {
        val animState = findState(state)
        val animateTo = animState.value

        val initial = property.get()
        val action = object : TemporalAction() {

            override fun update(percent: Float) {
                val value = interpolator.interpolate(initial, animateTo, percent)
                property.set(value)
                val actor = actor
                doInvalidate(actor)
            }
        }
        val transition = transitionFor(currentState, state)
        action.duration = transition.time.toFloat() / 1000f
        action.interpolation = transition.interpolation
        actor.addAction(action)
        currentState = state
    }

    fun replaceState(state: AnimState<T>) {
        if (states[state.name] == null) {
            throw RuntimeException("state with name ${state.name} does not exist")
        }
        states[state.name] = state
    }

    private fun doInvalidate(actor: Actor?) {
        if (actor !is Layout) return
        if (invalidate) actor.invalidate()
        if (invalidateHierarchy) actor.invalidateHierarchy()
        if (invalidateParent) (actor.parent as? Layout)?.invalidate()
    }

    private fun transitionFor(from: String, to: String): AnimTransition {

        fun score(transition: AnimTransition): Int {
            var score = 0
            when {
                transition.from == "*" -> score++
                transition.from != from -> return -1
                else -> score += 2
            }
            when {
                transition.to == "*" -> score++
                transition.to != to -> return -1
                else -> score += 2
            }
            return score
        }

        var bestScore = -1
        var bestTransition: AnimTransition? = null
        transitions.forEach { transition ->
            val score = score(transition)
            if (score <= bestScore) return@forEach
            bestScore = score
            bestTransition = transition
        }
        return bestTransition ?: defaultTransition
    }

    private fun findState(state: String) =
        states[state] ?: throw RuntimeException("Unknown state: $state")

    private data class AnimTransition(
        val from: String,
        val to: String,
        val time: Int,
        val interpolation: Interpolation
    )

}
