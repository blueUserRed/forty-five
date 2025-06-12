package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable
import com.fourinachamber.fortyfive.FortyFive
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
    val offX: Float = 0f,
    val offY: Float = 0f,
    var scale: Float = 1f,
    var blurFactor: Float = 0.9f,
    var showDropShadow: Boolean = true
) {

    fun doDropShadow(batch: Batch?, screen: OnjScreen, drawable: Drawable, actor: Actor) {
        if (!showDropShadow) return
        val scaleX2 = scale
        val scaleY2 = scale
        val (x, y) = getXY(actor, scaleX2, scaleY2)
        val (sWidth, sHeight) = getWidthHeight(actor, scaleX2, scaleY2)
        doDropShadow(batch, screen, drawer = { drawable.draw(batch, x, y, sWidth, sHeight) })
    }

    fun doDropShadow(
        batch: Batch?,
        screen: OnjScreen,
        textureRegion: TextureRegion,
        actor: Actor,
        scaleX: Float,
        scaleY: Float,
        rotation: Float
    ) {
        if (!showDropShadow) return
        val scaleX2 = scale * scaleX
        val scaleY2 = scale * scaleY
        val (x, y) = getXY(actor, scaleX2, scaleY2)
        val (sWidth, sHeight) = getWidthHeight(actor, scaleX2, scaleY2)
        doDropShadow(batch, screen, drawer = {
            batch?.draw(
                textureRegion,
                x, y,
                sWidth / 2, sHeight / 2,
                sWidth, sHeight,
                1f, 1f,
                rotation
            )
        })
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

    /**
     * this is untested and might not work
     */
    fun doDropShadowRotated(batch: Batch, screen: OnjScreen, drawable: TransformDrawable, actor: CustomImageActor) {
        if (!showDropShadow) return
        val scaleX2 = scale
        val scaleY2 = scale
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

    companion object : ResourceBorrower {

        val dropShadowShader: Promise<BetterShader> by lazy {
            FortyFive.resourceManager.request(this, FortyFive.gameLifetime, "other_drop_shadow_shader")
        }
    }

}
