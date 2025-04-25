package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Lifetime
import com.fourinachamber.fortyfive.utils.Promise

interface DropShadowActor {

    var dropShadow: DropShadow?

}

data class DropShadow(
    var color: Color,
    val offX: Float = 0f,
    val offY: Float = 0f,
    var scale: Float = 1f,
    var blurFactor: Float = 0.9f,
    var showDropShadow: Boolean = true
) {

    fun doDropShadow(batch: Batch?, screen: OnjScreen, drawable: Drawable, actor: Actor) {
        if (!showDropShadow) return
        val w = actor.width * scale
        val h = actor.height * scale
        val x = actor.x - (w - actor.width) / 2 + offX
        val y = actor.y - (h - actor.height) / 2 + offY
        doDropShadow(batch, screen, drawer = { drawable.draw(batch, x, y, w, h) })
    }

    private inline fun doDropShadow(batch: Batch?, screen: OnjScreen, drawer: () -> Unit) {
        batch ?: return
        val shader = dropShadowShader.getOrNull() ?: return
        batch.flush()
        shader.prepare(screen)
        val prev = batch.shader
        batch.shader = shader.shader
        shader.shader.setUniformf("u_color", color.r, color.g, color.b, color.a)
        shader.shader.setUniformf("u_scale", scale)
        shader.shader.setUniformf("u_blurFactor", blurFactor)
        drawer()
        batch.flush()
        batch.shader = prev
    }

    companion object : ResourceBorrower {

        val dropShadowShader: Promise<BetterShader> by lazy {
            ResourceManager.request(this, Lifetime.endless, "other_drop_shadow_shader")
        }
    }

}
