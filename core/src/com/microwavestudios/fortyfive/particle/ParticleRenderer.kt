package com.microwavestudios.fortyfive.particle

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.utils.FortyFiveLogger
import com.microwavestudios.fortyfive.utils.Promise

abstract class ParticleRenderer(val emitter: ParticleSystem.Emitter) {

    abstract fun render(batch: Batch, screen: OnjScreen)
}

class TextParticleRenderer(emitter: ParticleSystem.Emitter) : ParticleRenderer(emitter) {

    private val infos: MutableList<RenderInfo> = mutableListOf()

    override fun render(
        batch: Batch,
        screen: OnjScreen
    ) {
        infos.removeIf { !it.particle.active }
        infos.forEach { renderRenderInfo(batch, it) }
    }

    private fun renderRenderInfo(batch: Batch, info: RenderInfo) {
        val font = info.font
        font.data.setScale(info.scale)
        font.setColor(info.color.r, info.color.g, info.color.b, info.color.a * info.particle.alpha)
        font.draw(
            batch,
            info.text,
            info.particle.x,
            info.particle.y,
        )
    }

    fun spawn(
        font: BitmapFont,
        scale: Float,
        color: Color,
        text: String
    ) {
        infos.add(RenderInfo(
            font, scale, color, text, emitter.spawn()
        ))
    }

    data class RenderInfo(
        val font: BitmapFont,
        val scale: Float,
        val color: Color,
        val text: String,
        val particle: ParticleSystem.Particle
    )
}

class TextureParticleRenderer(
    val textureHandle: String,
    val width: Float,
    val height: Float,
    val color: Color,
    private val screen: OnjScreen,
    emitter: ParticleSystem.Emitter
) : ParticleRenderer(emitter), ResourceBorrower {

    private val drawable: Promise<Drawable> = FortyFive.resourceManager.request(this, screen.lifetime, textureHandle)

    override fun render(
        batch: Batch,
        screen: OnjScreen
    ) {
        val originalColor = batch.color.cpy()
        batch.flush()
        batch.setColor(color.r, color.g, color.b, color.a)
        val drawable = drawable.getOrNull() ?: return
        emitter.particles.forEach { particle ->
            drawable.draw(
                batch,
                particle.x - width / 2,
                particle.y - height / 2,
                width, height
            )
        }
        batch.flush()
        batch.color = originalColor
    }

}
