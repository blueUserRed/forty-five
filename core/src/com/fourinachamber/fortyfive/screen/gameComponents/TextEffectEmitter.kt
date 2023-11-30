package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.general.CustomLabel
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.AnimationSpawner
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.utils.random
import com.fourinachamber.fortyfive.utils.component1
import com.fourinachamber.fortyfive.utils.component2

class TextEffectEmitter(
    private val font: BitmapFont,
    private val fontColor: Color,
    private val fontScale: Float,
    private val speed: ClosedFloatingPointRange<Float>,
    private val spawnVarianceX: Float,
    private val spawnVarianceY: Float,
    private val animationDuration: IntRange,
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

    fun playAnimation(text: String) {
        val label = CustomLabel(screen, text, Label.LabelStyle(font, fontColor))
        label.setFontScale(fontScale)
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        label.setPosition(
            x + (-spawnVarianceX..spawnVarianceX).random(),
            y + (-spawnVarianceY..spawnVarianceY).random()
        )
        val animation = TextAnimation(
            text,
            label,
            TimeUtils.millis(),
            animationDuration.random(),
            speed.random()
        )
        screen.addActorToRoot(label)
        runningAnimations.add(animation)
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

}
