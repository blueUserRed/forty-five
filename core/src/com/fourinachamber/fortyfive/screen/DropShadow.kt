package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Lifetime
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.SubscribeableObserver
import com.fourinachamber.fortyfive.utils.automaticResourceGetter
import kotlin.properties.ObservableProperty

interface DropShadowActor {

    var dropShadow: DropShadow?
}

interface DropShadow {

    var showDropShadow: Boolean

    fun doDropShadow(batch: Batch?, screen: OnjScreen, drawable: Drawable, actor: Actor)

    fun doDropShadow(
        batch: Batch?,
        screen: OnjScreen,
        drawable: TransformDrawable,
        actor: Actor,
        scaleX: Float,
        scaleY: Float,
        rotation: Float
    )
}

class BakedDropShadow(
    background: ResourceHandle,
    private val screen: OnjScreen,
    val offX: Float = 0f,
    val offY: Float = 0f,
    var scaleX: Float = 2.0f,
    var scaleY: Float = 2.0f,
    override var showDropShadow: Boolean = true
) : DropShadow, ResourceBorrower {

    private val dropShadow: Promise<TextureRegion> =
        FortyFive.resourceManager.request(
            this,
            screen.lifetime,
            "$background${ResourceManager.DROP_SHADOW_END}"
        )

    override fun doDropShadow(batch: Batch?, screen: OnjScreen, drawable: Drawable, actor: Actor) =
        doDropShadow(batch, actor, 1f, 1f, 0f)

    override fun doDropShadow(
        batch: Batch?,
        screen: OnjScreen,
        drawable: TransformDrawable,
        actor: Actor,
        scaleX: Float,
        scaleY: Float,
        rotation: Float
    ) = doDropShadow(batch, actor, scaleX, scaleY, rotation)

    private fun doDropShadow(
        batch: Batch?,
        actor: Actor,
        scaleX: Float,
        scaleY: Float,
        rotation: Float
    ) {
        if (!showDropShadow) return
        val dropShadow = dropShadow.getOrNull() ?: return
        val width = actor.width * scaleX * this.scaleX
        val height = actor.height * scaleY * this.scaleY
        val x = actor.x - (width - actor.width) / 2 + offX
        val y = actor.y - (height - actor.height) / 2 + offY
        batch ?: return
        batch.draw(dropShadow, x, y, width / 2, height / 2, width, height, scaleX, scaleY, rotation)
    }

}

class SquareDropShadow(
    var color: Color,
    val offX: Float = 0f,
    val offY: Float = 0f,
    var scale: Float = 1f,
    var blurFactor: Float = 0.9f,
    override var showDropShadow: Boolean = true
) : DropShadow {

    override fun doDropShadow(batch: Batch?, screen: OnjScreen, drawable: Drawable, actor: Actor) {
        if (!showDropShadow) return
        val scaleX2 = scale
        val scaleY2 = scale
        val (x, y) = getXY(actor, scaleX2, scaleY2)
        val (sWidth, sHeight) = getWidthHeight(actor, scaleX2, scaleY2)
        doDropShadow(batch, screen, drawer = { drawable.draw(batch, x, y, sWidth, sHeight) })
    }

    override fun doDropShadow(
        batch: Batch?,
        screen: OnjScreen,
        drawable: TransformDrawable,
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
            drawable.draw(
                batch, x, y, sWidth / 2, sHeight / 2, sWidth, sHeight, 1f, 1f, rotation
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
