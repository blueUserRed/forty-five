package com.blueuserred.testgame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.Viewport

sealed class Either<out T, out U>  {

    class Left<out T>(val value: T) : Either<T, Nothing>()
    class Right<out U>(val value: U) : Either<Nothing, U>()

}

fun <T> T.eitherLeft(): Either<T, Nothing> = Either.Left(this)
fun <T> T.eitherRight(): Either<Nothing, T> = Either.Right(this)

val Vector3.xy: Vector2
    get() = Vector2(x, y)

val Vector2.mag: Float
    get() = this.len()

operator fun Vector2.minus(other: Vector2) = Vector2(x - other.x, y - other.y)
operator fun Vector2.plus(other: Vector2) = Vector2(x + other.x, y + other.y)
infix fun Vector2.dot(other: Vector2) = this.dot(other)
fun Vector2.multIndividual(other: Vector2) = Vector2(x * other.x, y * other.y)

object Utils {

    fun setCursor(cursor: Either<Cursor, SystemCursor>) = when (cursor) {
        is Either.Left -> Gdx.graphics.setCursor(cursor.value)
        is Either.Right -> Gdx.graphics.setSystemCursor(cursor.value)
    }

    fun getCursorPos(viewport: Viewport): Vector2 {
        return viewport.camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)).xy
    }

}
