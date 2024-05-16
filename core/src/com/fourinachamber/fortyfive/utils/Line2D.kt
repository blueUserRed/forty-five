package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2

class Line2D(
    val start: Vector2,
    val end: Vector2
) {

    val midPoint: Vector2
        get() = start midPoint end

    fun intersection(line2D: Line2D): Vector2? {
        val r = end - start
        val s = line2D.end - line2D.start
        val t = ((line2D.start - start) cross s) / (r cross s)
        val u = ((start - line2D.start) cross r) / (s cross r)
        if (r cross s == 0f && (line2D.start - start) cross r == 0f) {
            if ((line2D.start - start) dot (r / (r dot r)) in 0.0..1.0) return null
            return line2D.midPoint
        }
        if (r cross s != 0f && t in 0.0..1.0 && u in 0.0..1.0) return start + r * t
        return null
    }

    fun intersects(line2D: Line2D): Boolean = intersection(line2D) != null

    fun intersection(rectangle: Rectangle): Vector2? =
        rectangle.lines().map { intersection(it) }.find { it != null }

    fun intersects(rectangle: Rectangle): Boolean = intersection(rectangle) != null

}

fun Rectangle.lines(): List<Line2D> {
    val x = x
    val y = y
    val xw = x + width
    val yw = y + width
    return listOf(
        Line2D(Vector2(x, y), Vector2(x, yw)),
        Line2D(Vector2(x, yw), Vector2(xw, yw)),
        Line2D(Vector2(xw, yw), Vector2(xw, y)),
        Line2D(Vector2(xw, y), Vector2(x, y)),
    )
}

fun Rectangle.intersection(line2D: Line2D): Vector2? = line2D.intersection(this)
fun Rectangle.intersects(line2D: Line2D): Boolean = line2D.intersects(this)
