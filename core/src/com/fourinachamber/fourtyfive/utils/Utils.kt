package com.fourinachamber.fourtyfive.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.ui.ParticleEffectActor
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * represents a value that can be of type [T] or of type [U]. Check which type it is using `is Either.Left` or
 * `is Either.Right`. Can be created using the constructor of the subclasses or using the [eitherLeft] or [eitherRight]
 * extension functions.
 */
sealed class Either<out T, out U>  {

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

/**
 * gets the magnitude of the Vector
 */
val <T : Vector<T>> Vector<T>.mag: Float
    get() = this.len()

operator fun Vector2.minus(other: Vector2) = Vector2(x - other.x, y - other.y)
operator fun Vector2.plus(other: Vector2) = Vector2(x + other.x, y + other.y)
infix fun Vector2.dot(other: Vector2) = this.dot(other)
fun Vector2.multIndividual(other: Vector2) = Vector2(x * other.x, y * other.y)

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
 * rotates an array by [by]. Can be negative
 */
inline fun <reified T> Array<T>.rotate(by: Int): Array<T> {
    return Array(this.size) {
        var newIndex = it + by
        if (newIndex > this.size) newIndex %= this.size
        if (newIndex < 0) newIndex += this.size
        this[newIndex]
    }
}

object Utils {

    /**
     * sets the currently active cursor
     */
    fun setCursor(cursor: Either<Cursor, SystemCursor>) = when (cursor) {
        is Either.Left -> Gdx.graphics.setCursor(cursor.value)
        is Either.Right -> Gdx.graphics.setSystemCursor(cursor.value)
    }

    /**
     * gets the current cursor pos and unprojects it using [viewport]
     */
    fun getCursorPos(viewport: Viewport): Vector2 {
        return viewport.camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)).xy
    }

    /**
     * loads either a custom cursor or a system cursor
     * @throws RuntimeException when [cursorName] is not known
     */
    fun loadCursor(
        useSystemCursor: Boolean,
        cursorName: String,
        screenDataProvider: ScreenDataProvider
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
            return (screenDataProvider.cursors[cursorName] ?: run {
                throw RuntimeException("unknown custom cursor: $cursorName")
            }).eitherLeft()
        }
    }

    fun interpolationOrError(name: String): Interpolation = when (name) {

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

        else -> throw RuntimeException("Unknown interpolation: $name")
    }

}
