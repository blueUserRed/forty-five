package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Lifetime
import com.fourinachamber.fortyfive.utils.Promise

interface DropShadowActor {

    var dropShadow: DropShadow?

}

data class DropShadow(
    var color: Color,
    val multiplier: Float = 0.3f, // zwischen 0.2f und 0.6f ist es okay, kommt halt auf den zweck an, für den rest muss man die anderen werte auch setzten
    val offX: Float = 0f,
    val offY: Float = 0f,
    var scaleX: Float = 0f,
    var scaleY: Float = 0f,
    var maxOpacity: Float = 0.2f,
    var showDropShadow: Boolean = true
) {

    init {
        if (scaleX == 0f) scaleX = 1 + multiplier / 2
        if (scaleY == 0f) scaleY = 1 + multiplier / 2
    }

    fun doDropShadow(batch: Batch?, screen: OnjScreen, drawable: Drawable, actor: Actor) {
        if (!showDropShadow) return
        val scaleX2 = getScale(scaleX)
        val scaleY2 = getScale(scaleY)
        val (x, y) = getXY(actor, scaleX2, scaleY2)
        val (sWidth, sHeight) = getWidthHeight(actor, scaleX2, scaleY2)
        doDropShadow(batch, screen, drawer = { drawable.draw(batch, x, y, sWidth, sHeight) })
    }

    private fun getWidthHeight(
        actor: Actor,
        scaleX2: Float,
        scaleY2: Float
    ): Pair<Float, Float> {
        val sWidth = actor.width * scaleX2
        val sHeight = actor.height * scaleY2
        return Pair(sWidth, sHeight)
    }

    private inline fun doDropShadow(batch: Batch?, screen: OnjScreen, drawer: () -> Unit) {
        batch ?: return
        val shader = dropShadowShader.getOrNull() ?: return
        shader.prepare(screen)
        batch.shader = shader.shader
        shader.shader.setUniformf("u_multiplier", multiplier)
        shader.shader.setUniformf("u_maxOpacity", maxOpacity)
        shader.shader.setUniformf("u_color", color)
        drawer()
        batch.flush()
        batch.shader = null
    }

    /**
     * this is untested and might not work
     */
    fun doDropShadowRotated(batch: Batch, screen: OnjScreen, drawable: TransformDrawable, actor: CustomImageActor) {
        if (!showDropShadow) return
        val scaleX2 = getScale(scaleX)
        val scaleY2 = getScale(scaleY)
        val (x, y) = getXY(actor, scaleX2, scaleY2)
        val (sWidth, sHeight) = getWidthHeight(actor, scaleX2, scaleY2)
        doDropShadow(batch, screen, drawer = {
            drawable.draw(batch, x, y, sWidth / 2, sHeight / 2, sWidth, sHeight, 1f, 1f, actor.rotation)
        })
    }

    private fun getXY(
        actor: Actor,
        scaleX2: Float,
        scaleY2: Float
    ): Pair<Float, Float> {
        val x = actor.x - actor.width * (scaleX2 - 1) / 2 + offX
        val y = actor.y - actor.height * (scaleY2 - 1) / 2 + offY
        return Pair(x, y)
    }

    private fun getScale(scaleX: Float): Float = scaleX * (1 + multiplier)

    companion object : ResourceBorrower {

        val dropShadowShader: Promise<BetterShader> by lazy {
            ResourceManager.request(this, Lifetime.endless, "drop_shadow_shader")
        }

        fun dropShadowDefaults(
            color: Color, multiplier: Float = 0.02f, offX: Float = 0f, offY: Float = 0f,
            scaleX: Float = -1f, scaleY: Float = -1f, maxOpacity: Float = 0.6f
        ): DropShadow {
            return DropShadow(color, multiplier, offX, offY, scaleX, scaleY, maxOpacity)
        }
    }

}
