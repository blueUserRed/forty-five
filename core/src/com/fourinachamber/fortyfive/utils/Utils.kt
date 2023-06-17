package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.random.Random

/**
 * represents a value that can be of type [T] or of type [U]. Check which type it is using `is Either.Left` or
 * `is Either.Right`. Can be created using the constructor of the subclasses or using the [eitherLeft] or [eitherRight]
 * extension functions.
 */
sealed class Either<out T, out U> {

    class Left<out T>(val value: T) : Either<T, Nothing>()
    class Right<out U>(val value: U) : Either<Nothing, U>()

}

/**
 * redirects to [Payload.object] because `object` is a keyword in kotlin
 */
var Payload.obj: Any?
    get() = this.`object`
    set(value) {
        this.`object` = value
    }

/**
 * @see Either
 */
fun <T> T.eitherLeft(): Either<T, Nothing> = Either.Left(this)

/**
 * @see Either
 */
fun <T> T.eitherRight(): Either<Nothing, T> = Either.Right(this)

/**
 * returns the x and y components of a vector3 as a Vector2
 */
val Vector3.xy: Vector2
    get() = Vector2(x, y)

val Vector2.unit: Vector2
    get() {
        val len = this.len()
        if (len == 0f) return Vector2(0f, 0f)
        return this / len
    }

operator fun Vector2.minus(other: Vector2) = Vector2(x - other.x, y - other.y)
operator fun Vector2.plus(other: Vector2) = Vector2(x + other.x, y + other.y)
operator fun Vector2.times(other: Float): Vector2 = Vector2(this.x * other, this.y * other)
operator fun Vector2.div(other: Float): Vector2 = Vector2(this.x / other, this.y / other)
infix fun Vector2.dot(other: Vector2) = this.dot(other)
operator fun Vector2.unaryMinus(): Vector2 = Vector2(-this.x, -this.y)
fun Vector2.multIndividual(other: Vector2) = Vector2(x * other.x, y * other.y)
fun Vector2.withMag(mag: Float): Vector2 = this.unit * mag
fun Vector2.compare(other: Vector2, epsilon: Float = 0.01f): Boolean =
    other.x in (this.x - epsilon)..(this.x + epsilon) &&
    other.y in (this.y - epsilon)..(this.y + epsilon)

operator fun Vector2.component1(): Float = this.x
operator fun Vector2.component2(): Float = this.y

/**
 * makes sure that [this] is between [min] and [max] (inclusive)
 */
fun Float.between(min: Float, max: Float): Float {
    if (this < min) return min
    if (this > max) return max
    return this
}

/**
 * makes sure that [this] is between [min] and [max] (inclusive)
 */
fun Double.between(min: Double, max: Double): Double {
    if (this < min) return min
    if (this > max) return max
    return this
}

fun Vector2(x: Int, y: Int): Vector2 {
    return Vector2(x.toFloat(), y.toFloat())
}

/**
 * compares two floats using an epsilon to make sure rounding errors don't break anything
 */
fun Float.epsilonEquals(other: Float, epsilon: Float = 0.0005f): Boolean {
    return this in (other - epsilon)..(other + epsilon)
}

val Float.radians: Float
    get() = Math.toRadians(this.toDouble()).toFloat()

val Float.degrees: Float
    get() = Math.toDegrees(this.toDouble()).toFloat()

fun ClosedFloatingPointRange<Float>.random(random: Random): Float {
    return random.nextFloat() * (endInclusive - start) + start
}

public fun <E> List<E>.subListTillMax(toIndex: Int): List<E> {
    return subList(0, min(size, toIndex))
}
fun Vector2.clone(): Vector2 {
    return Vector2(x,y)
}

operator fun AtomicInteger.inc(): AtomicInteger {
    this.incrementAndGet()
    return this
}

val AtomicInteger.get: Int
    get() = this.get()

object Utils {

    /**
     * sets the currently active cursor
     */
    @MainThreadOnly
    fun setCursor(cursor: Either<Cursor, SystemCursor>) = when (cursor) {
        is Either.Left -> Gdx.graphics.setCursor(cursor.value)
        is Either.Right -> Gdx.graphics.setSystemCursor(cursor.value)
    }

    /**
     * gets the current cursor pos and unprojects it using [viewport]
     */
    @AllThreadsAllowed
    fun getCursorPos(viewport: Viewport): Vector2 {
        return viewport.camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)).xy
    }

    /**
     * loads either a custom cursor or a system cursor
     * @throws RuntimeException when [cursorName] is not known
     */
    @MainThreadOnly
    fun loadCursor(
        useSystemCursor: Boolean,
        cursorName: String,
        onjScreen: OnjScreen
    ): Either<Cursor, SystemCursor> {

        if (useSystemCursor) {

            return when (cursorName) {

                "hand" -> SystemCursor.Hand
                "arrow" -> SystemCursor.Arrow
                "ibeam" -> SystemCursor.Ibeam
                "crosshair" -> SystemCursor.Crosshair
                "horizontal resize" -> SystemCursor.HorizontalResize
                "vertical resize" -> SystemCursor.VerticalResize
                "nw se resize" -> SystemCursor.NWSEResize
                "ne sw resize" -> SystemCursor.NESWResize
                "all resize" -> SystemCursor.AllResize
                "not allowed" -> SystemCursor.NotAllowed
                "none" -> SystemCursor.None
                else -> throw RuntimeException("unknown system cursor: $cursorName")

            }.eitherRight()

        } else {
            return ResourceManager.get<Cursor>(onjScreen, cursorName).eitherLeft()
        }
    }

    @AllThreadsAllowed
    fun interpolationOrError(name: String): Interpolation = when (name) {

        "linear" -> Interpolation.linear
        "swing" -> Interpolation.swing
        "swing in" -> Interpolation.swingIn
        "swing out" -> Interpolation.swingOut
        "bounce" -> Interpolation.bounce
        "bounce in" -> Interpolation.bounceIn
        "bounce out" -> Interpolation.bounceOut
        "elastic" -> Interpolation.elastic
        "elastic in" -> Interpolation.elasticIn
        "elastic out" -> Interpolation.elasticOut
        "circle" -> Interpolation.circle
        "circle in" -> Interpolation.circleIn
        "circle out" -> Interpolation.circleOut
        "smooth" -> Interpolation.smooth

        else -> throw RuntimeException("Unknown interpolation: $name")
    }

}
