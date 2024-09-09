package com.fourinachamber.fortyfive.screen.gameWidgets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.CustomLabel
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.AnimationSpawner
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjObject

class TextEffectEmitter(
    private val animationConfigs: Map<String, TextAnimationConfig>,
    private val screen: OnjScreen
) : Widget(), StyledActor, AnimationSpawner {

    override val actor: Actor = this
    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    private val runningAnimations: MutableList<TextAnimation> = mutableListOf()

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
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
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        label.setPosition(
            x + (-config.spawnVarianceX..config.spawnVarianceX).random(),
            y + (-config.spawnVarianceY..config.spawnVarianceY).random()
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
            else -> "number"
        }
        playAnimation(num.toString(), config)
    }

    private fun findConfig(name: String?): TextAnimationConfig {
        if (animationConfigs.isEmpty()) {
            throw RuntimeException("attempted to play animation on TextEffectEmitter with no config defined")
        }
        return name?.let { animationConfigs[it] } ?: animationConfigs["default"] ?: animationConfigs.values.first()
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
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
