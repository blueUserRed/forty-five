package com.microwavestudios.fortyfive.particle

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.microwavestudios.fortyfive.screen.OnjScreen

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
