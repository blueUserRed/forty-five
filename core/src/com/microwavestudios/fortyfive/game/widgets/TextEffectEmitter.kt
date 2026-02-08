package com.microwavestudios.fortyfive.game.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.widgets.TextEffectEmitter.TextAnimationConfig
import com.microwavestudios.fortyfive.particle.ParticleSystem
import com.microwavestudios.fortyfive.particle.TextParticleRenderer
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.screen.actors.AnimatedActor

class TextEffectEmitter(
    animationConfigs: Map<String, TextAnimationConfig>,
    private val actor: Actor,
    private val screen: CustomScreen
) {

    private val animationConfigs: Map<String, TextAnimationConfig> = animationConfigs + standardTextAnimConfigs

    private val particleRenderer: MutableMap<TextAnimationConfig, TextParticleRenderer> = mutableMapOf()

    fun playNumberChangeAnimation(num: Int, overrideConfig: String? = null) {
        val config = overrideConfig ?: when {
            num < 0 -> "number_negative"
            num > 0 -> "number_positive"
            else -> "number_neutral"
        }
        playAnimation(num.toString(), config)
    }

    fun playAnimation(text: String, configName: String? = null) {
        val config = findConfig(configName)
        val renderer = rendererForConfig(config)
        renderer.spawn(config.font, config.fontScale, config.fontColor, text)
    }

    private fun rendererForConfig(config: TextAnimationConfig): TextParticleRenderer {
        val cachedRenderer = particleRenderer[config]
        if (cachedRenderer != null) return cachedRenderer
        val emitter = createEmitter(screen.textEffectParticleSystem, config)
        val renderer = TextParticleRenderer(emitter)
        emitter.renderer = renderer
        particleRenderer[config] = renderer
        return renderer
    }

    private fun createEmitter(system: ParticleSystem, config: TextAnimationConfig) = system.emitter {
        syncSpawnPosWithActor(actor)

        if (config.positiveSpeed == null) {
            xVelocityRange = (-2f..2f)
            yVelocityRange = (10f..20f)
            ttlRange = (5000L..5000L)

            updateParticles { particle ->
                if (particle.forces.y != 0f) return@updateParticles
                if (particle.velocity.y > -14f) return@updateParticles
                particle.applyForce(0f, 1f)
            }
        } else {
            val speed = config.positiveSpeed
            yVelocityRange = speed
            ttlRange = (1000L..1000L)

            initParticle { particle ->
                particle.applyForce(0f, 1f)
            }

            val fadeOutFraction = 1f / 3f
            updateParticles { particle ->
                val fadeOutTime = (particle.ttl * fadeOutFraction).toLong()
                val startFadeOutAt = particle.spawnedAt + (particle.ttl - fadeOutTime)
                val now = TimeUtils.millis()
                if (now < startFadeOutAt) return@updateParticles
                val passedFadeTime = now - startFadeOutAt
                val fadeFraction = passedFadeTime.toFloat() / fadeOutTime.toFloat()
                particle.alpha = 1f - fadeFraction
            }

        }
    }

    private fun findConfig(name: String?): TextAnimationConfig {
        if (animationConfigs.isEmpty()) {
            throw RuntimeException("attempted to play animation on TextEffectEmitter with no config defined")
        }
        return name?.let { animationConfigs[it] } ?: animationConfigs["default"] ?: animationConfigs.values.first()
    }

    data class TextAnimationConfig(
        val font: BitmapFont,
        val fontColor: Color,
        val fontScale: Float,
        val positiveSpeed: ClosedFloatingPointRange<Float>? = null
    )

    companion object {

        val roadgeek: BitmapFont by lazy {
            FortyFive.resourceManager.forceGet<BitmapFont>(
                object : ResourceBorrower {},
                FortyFive.gameLifetime,
                "roadgeek60"
            )
        }

        val standardTextAnimConfigs by lazy {

            mapOf(
                "number_neutral" to TextAnimationConfig(
                    font = roadgeek,
                    fontColor = Color.WHITE,
                    fontScale = 0.6f,
                ),
                "number_negative" to TextAnimationConfig(
                    font = roadgeek,
                    fontColor = Color.RED,
                    fontScale = 0.6f,
                ),
                "number_positive" to TextAnimationConfig(
                    font = roadgeek,
                    fontColor = Color.GREEN,
                    fontScale = 0.6f,
                    positiveSpeed = 3f..5f,
                ),
            )
        }
    }

}

fun AnimatedActor.textEffectEmitter(
    animationConfigs: Map<String, TextAnimationConfig> = mapOf()
): TextEffectEmitter {
    val emitter = TextEffectEmitter(animationConfigs, this as Actor, screen)
    return emitter
}
