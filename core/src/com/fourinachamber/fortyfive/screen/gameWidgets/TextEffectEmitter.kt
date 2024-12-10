package com.fourinachamber.fortyfive.screen.gameWidgets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.gameWidgets.TextEffectEmitter.TextAnimationConfig
import com.fourinachamber.fortyfive.screen.general.CustomLabel
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.AnimatedActor
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjObject

class TextEffectEmitter(
    private val animationConfigs: Map<String, TextAnimationConfig>,
    private val actor: Actor,
    private val screen: OnjScreen
) : AnimatedActor.NeedsUpdate {

    private val runningAnimations: MutableList<TextAnimation> = mutableListOf()

    override fun update() {
        val now = TimeUtils.millis()
        val iterator = runningAnimations.iterator()
        while (iterator.hasNext()) {
            val anim = iterator.next()
            if (anim.startTime + anim.duration <= now) {
                screen.removeActorFromRoot(anim.label)
                iterator.remove()
                continue
            }
            anim.label.y += anim.speed * Gdx.graphics.deltaTime
        }
    }

    fun playAnimation(text: String, configName: String? = null) {
        val config = findConfig(configName)
        val label = CustomLabel(screen, text, Label.LabelStyle(config.font, config.fontColor), true)
        label.setFontScale(config.fontScale)
        val (x, y) = actor.localToStageCoordinates(Vector2(0f, 0f))
        label.setPosition(
            x + actor.width / 2 + (-config.spawnVarianceX..config.spawnVarianceX).random(),
            y + actor.height / 2 + (-config.spawnVarianceY..config.spawnVarianceY).random()
        )
        val animation = TextAnimation(
            text,
            label,
            TimeUtils.millis(),
            config.animationDuration.random(),
            config.speed.random()
        )
        screen.addActorToRoot(label)
        runningAnimations.add(animation)
    }

    fun playNumberChangeAnimation(num: Int, overrideConfig: String? = null) {
        val config = overrideConfig ?: when {
            num < 0 -> "number_negative"
            num > 0 -> "number_positive"
            else -> "number_neutral"
        }
        playAnimation(num.toString(), config)
    }

    private fun findConfig(name: String?): TextAnimationConfig {
        if (animationConfigs.isEmpty()) {
            throw RuntimeException("attempted to play animation on TextEffectEmitter with no config defined")
        }
        return name?.let { animationConfigs[it] } ?: animationConfigs["default"] ?: animationConfigs.values.first()
    }

    private data class TextAnimation(
        val text: String,
        val label: CustomLabel,
        val startTime: Long,
        val duration: Int,
        val speed: Float
    )

    data class TextAnimationConfig(
        val font: BitmapFont,
        val fontColor: Color,
        val fontScale: Float,
        val speed: ClosedFloatingPointRange<Float>,
        val spawnVarianceX: Float,
        val spawnVarianceY: Float,
        val animationDuration: IntRange,
    )

    companion object {

        val standardTextAnimConfigs by lazy {
            // Ugly, but fine because fonts stay loaded all the time anyway
            val roadgeek = ResourceManager.forceGet<BitmapFont>(object : ResourceBorrower {}, Lifetime.endless, "roadgeek")
            mapOf(
                "number_neutral" to TextAnimationConfig(
                    font = roadgeek,
                    fontColor = Color.WHITE,
                    fontScale = 0.9f,
                    speed = 60f..80f,
                    spawnVarianceX = 30f,
                    spawnVarianceY = 30f,
                    animationDuration = 1000..1500
                ),
                "number_negative" to TextAnimationConfig(
                    font = roadgeek,
                    fontColor = Color.RED,
                    fontScale = 0.9f,
                    speed = -80f..-60f,
                    spawnVarianceX = 30f,
                    spawnVarianceY = 30f,
                    animationDuration = 1000..1500
                ),
                "number_positive" to TextAnimationConfig(
                    font = roadgeek,
                    fontColor = Color.GREEN,
                    fontScale = 0.9f,
                    speed = 60f..80f,
                    spawnVarianceX = 30f,
                    spawnVarianceY = 30f,
                    animationDuration = 1000..1500
                ),
            )
        }

        fun configsFromOnj(onj: OnjArray, screen: OnjScreen): Map<String, TextAnimationConfig> = onj
            .value
            .map { it as OnjObject }
            .associate { it.get<String>("name") to configFromOnj(it, screen) }

        fun configFromOnj(onj: OnjObject, screen: OnjScreen): TextAnimationConfig = TextAnimationConfig(
            ResourceManager.forceGet(screen, screen, onj.get<String>("font")),
            onj.get<Color>("color"),
            onj.get<Double>("fontScale").toFloat(),
            onj.get<OnjArray>("speed").toFloatRange(),
            onj.get<Double>("spawnVarianceX").toFloat(),
            onj.get<Double>("spawnVarianceY").toFloat(),
            onj.get<OnjArray>("duration").toIntRange(),
        )

    }

}

fun AnimatedActor.textEffectEmitter(animationConfigs: Map<String, TextAnimationConfig>): TextEffectEmitter {
    val emitter = TextEffectEmitter(animationConfigs, this as Actor, screen)
    animationsNeedingUpdate.add(emitter)
    return emitter
}
