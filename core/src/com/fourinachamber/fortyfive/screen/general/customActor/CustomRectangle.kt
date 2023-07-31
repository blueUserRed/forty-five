package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import kotlin.math.max


class CustomRectangle(x: Float, y: Float, width: Float, height: Float, color: Color) :
    Actor() {
    private var texture: Texture? = null

    init {
        println(color)
        updateTexture(width.toInt(), height.toInt(), color)
        setX(x)
        setY(y)
        setWidth(width)
        setHeight(height)
    }

    fun updateTexture(width: Int, height: Int, color: Color) {
        val pixmap = Pixmap(max(width, 1), max(height, 1), Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fillRectangle(0, 0, width, height)
        texture = Texture(pixmap)
        pixmap.dispose()
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val color: Color = color
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
        batch.draw(texture, x, y, width, height)
    }
}