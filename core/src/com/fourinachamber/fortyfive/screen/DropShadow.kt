package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Lifetime
import com.fourinachamber.fortyfive.utils.Promise

interface DropShadowActor {

    var dropShadow: DropShadow?

}

data class DropShadow(
    val color: Color,
    val multiplier: Float,
    val offX: Float,
    val offY: Float,
) {

    inline fun doDropShadow(batch: Batch?, screen: OnjScreen, drawer: () -> Unit) {
        batch ?: return
        val shader = dropShadowShader.getOrNull() ?: return
        shader.prepare(screen)
        batch.shader = shader.shader
        shader.shader.setUniformf("u_multiplier", multiplier)
        shader.shader.setUniformf("u_offset", Vector2(offX, offY))
        shader.shader.setUniformf("u_color", color)
        drawer()
        batch.flush()
        batch.shader = null
    }

    companion object : ResourceBorrower {

        val dropShadowShader: Promise<BetterShader> by lazy {
            ResourceManager.request(this, Lifetime.endless, "drop_shadow_shader")
        }
    }

}
