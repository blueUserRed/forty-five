package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.CustomLabel
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import ktx.actors.alpha
import java.lang.Float.min

abstract class GameAnimation {

    abstract fun isFinished(): Boolean
    abstract fun update()
    open fun start() {}
    open fun end() {}

}

class BannerAnimation(
    val banner: TextureRegion,
    private val screenDataProvider: ScreenDataProvider,
    private val duration: Int,
    private val animationDuration: Int,
    private val beginScale: Float,
    private val endScale: Float
) : GameAnimation() {

    private var startTime = 0L
    private var runUntil = 0L

    private val renderTask = { batch: Batch ->

        val timeDiff = TimeUtils.millis() - startTime
        val percent = min(timeDiff.toFloat() / animationDuration.toFloat(), 1f)
        val scale = beginScale + (endScale - beginScale) * percent

        val viewport = screenDataProvider.stage.viewport
        val worldWidth = viewport.worldWidth
        val worldHeight = viewport.worldHeight

        batch.draw(
            banner,
            worldWidth / 2 - (banner.regionWidth * scale) / 2f,
            worldHeight / 2 - (banner.regionHeight * scale) / 2,
            banner.regionWidth.toFloat() * scale,
            banner.regionHeight.toFloat() * scale,
        )
    }

    override fun start() {
        startTime = TimeUtils.millis()
        runUntil = startTime + duration
        screenDataProvider.addLateRenderTask(renderTask)
    }

    override fun update() { }

    override fun isFinished(): Boolean = TimeUtils.millis() >= runUntil

    override fun end() {
        screenDataProvider.removeLateRenderTask(renderTask)
    }

}

class TextAnimation(
    private val x: Float,
    private val y: Float,
    initialText: String,
    private val fontColor: Color,
    private val fontScale: Float,
    private val font: BitmapFont,
    private val raise: Float,
    private val startFadeOutAt: Int,
    private val screenDataProvider: ScreenDataProvider,
    private val duration: Int
) : GameAnimation() {

    private var startTime = 0L
    private var runUntil = 0L

    private val label = CustomLabel(initialText, Label.LabelStyle(font, fontColor))

    var text: String = initialText
        set(value) {
            field = value
            label.setText(value)
        }

    override fun start() {
        startTime = TimeUtils.millis()
        runUntil = startTime + duration
        label.setFontScale(fontScale)
        label.fixedZIndex = Int.MAX_VALUE // lol
        screenDataProvider.addActorToRoot(label)
        label.setAlignment(Align.center)
    }

    override fun isFinished(): Boolean = TimeUtils.millis() >= runUntil

    override fun update() {
        val timeDiff = TimeUtils.millis() - startTime
        val percent = min(timeDiff.toFloat() / duration.toFloat(), 1f)
        val raiseBy = raise * percent

        label.width = label.prefWidth
        label.height = label.prefHeight
        label.alpha = calcAlpha()
        label.setPosition(
            x - label.width / 2,
            y + label.height / 2 + raiseBy
        )
    }

    private fun calcAlpha(): Float {
        val timeDiff = TimeUtils.millis() - startTime - startFadeOutAt
        val percent = min(timeDiff / (duration - startFadeOutAt).toFloat(), 1f)
        return 1 - percent
    }

    override fun end() {
        screenDataProvider.removeActorFromRoot(label)
    }

}
