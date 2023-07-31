package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import kotlin.math.max


class CustomRectangle() :
    Actor() {
    private var texture: Texture? = null

    override fun setColor(color: Color) {
        super.setColor(color)
        updateTexture(1, 1, color)
    }

    init {
        x = 1F
        y = 1F
        width = 1F
        height = 1F
        if (color != null) updateTexture(width.toInt(), height.toInt(), color)
    }

    constructor(color: Color) : this() {
        setColor(color)
    }

    fun updateTexture(width: Int, height: Int, color: Color) {
        val pixmap = Pixmap(max(width, 1), max(height, 1), Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fillRectangle(0, 0, width, height)
        texture = Texture(pixmap)
        pixmap.dispose()
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.draw(texture, x, y, width, height)
    }
}