package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.general.CustomLabel
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.AllThreadsAllowed
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import ktx.actors.alpha
import java.lang.Float.min

/**
 * special animations that affect the whole game
 */
abstract class GameAnimation {

    /**
     * true if the animation finished
     */
    abstract fun isFinished(): Boolean

    /**
     * updates the animation
     */
    @MainThreadOnly
    abstract fun update()

    /**
     * starts the animation
     */
    @AllThreadsAllowed
    open fun start() {}

    /**
     * called after the animation has finished
     */
    @AllThreadsAllowed
    open fun end() {}

}

/**
 * displays a banner over the whole screen with a zoom animation
 * @param banner the texture for the banner
 * @param duration the time in ms for which the banner is displayed
 * @param animationDuration the time in ms for which the banner is animated
 * @param beginScale the scale when the banner is first displayed
 * @param endScale the scale of the banner after [animationDuration] ms have passed
 */
class BannerAnimation(
    val banner: Drawable,
    private val onjScreen: OnjScreen,
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

        val viewport = onjScreen.stage.viewport
        val worldWidth = viewport.worldWidth
        val worldHeight = viewport.worldHeight

        banner.draw(
            batch,
            worldWidth / 2 - (banner.minWidth * scale) / 2f,
            worldHeight / 2 - (banner.minHeight * scale) / 2,
            banner.minWidth * scale,
            banner.minHeight * scale,
        )
    }

    override fun start() {
        startTime = TimeUtils.millis()
        runUntil = startTime + duration
        onjScreen.addLateRenderTask(renderTask)
    }

    override fun update() { }

    override fun isFinished(): Boolean = TimeUtils.millis() >= runUntil

    override fun end() {
        onjScreen.removeLateRenderTask(renderTask)
    }

    override fun toString(): String = "BannerAnimation"
}

/**
 * animates a text by raising it and fading it out (for example used for displaying the damage)
 */
class TextAnimation(
    private val screen: OnjScreen,
    private val x: Float,
    private val y: Float,
    initialText: String,
    fontColor: Color,
    private val fontScale: Float,
    font: BitmapFont,
    private val raise: Float,
    private val startFadeOutAt: Int,
    private val onjScreen: OnjScreen,
    private val duration: Int
) : GameAnimation() {

    private var startTime = 0L
    private var runUntil = 0L

    private val label = CustomLabel(screen, initialText, Label.LabelStyle(font, fontColor))

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
        onjScreen.addActorToRoot(label)
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
        onjScreen.removeActorFromRoot(label)
    }

    override fun toString(): String = "TextAnimation(${label.text})"

}

/**
 * fades an actor in and then out again
 * @param duration the duration of the whole animation
 * @param fadeIn the time in ms of the fadeIn animation (starts at startTime, finishes at startTime + fadeIn)
 * @param fadeOut the time in ms of the fadeOut animation
 * (starts at startTime + duration - fadeOut, finishes at startTime + duration)
 * @param fixedDimensions if not null the width of [actor] is set to the x-component and the height is set to the
 * y-component
 */
open class FadeInAndOutAnimation(
    protected val x: Float,
    protected val y: Float,
    val actor: Actor,
    private val onjScreen: OnjScreen,
    private val duration: Int,
    private val fadeIn : Int,
    private val fadeOut : Int,
    private val fixedDimensions: Vector2? = null
) : GameAnimation() {

    private var startTime = 0L
    private var runUntil = 0L

    override fun start() {
        startTime = TimeUtils.millis()
        runUntil = startTime + duration
        onjScreen.addActorToRoot(actor)
        if (actor is Widget && fixedDimensions == null) {
            actor.width = actor.prefWidth
            actor.height = actor.prefHeight
        }
        if (fixedDimensions != null) {
            actor.width = fixedDimensions.x
            actor.height = fixedDimensions.y
        }
        actor.setPosition(x, y)
    }

    override fun isFinished(): Boolean = TimeUtils.millis() >= runUntil
    override fun update() {
        actor.alpha = calcAlpha()
    }

    private fun calcAlpha(): Float {
        val timeDiff: Float = (TimeUtils.millis() - startTime).toFloat()
        return if (timeDiff <= fadeIn) (timeDiff / fadeIn)
        else if (timeDiff >= duration - fadeOut) {
            (1 - (timeDiff - (duration - fadeOut)) / fadeOut)
        }
        else 1f
    }

    override fun end() {
        onjScreen.removeActorFromRoot(actor)
    }

    override fun toString(): String = "FadeInAndOutAnimation"
}

/**
 * Works like [FadeInAndOutAnimation] but automatically creates a label to display the text
 * @param duration the duration of the whole animation
 * @param fadeIn the time in ms of the fadeIn animation (starts at startTime, finishes at startTime + fadeIn)
 * @param fadeOut the time in ms of the fadeOut animation
 * (starts at startTime + duration - fadeOut, finishes at startTime + duration)
 */
class FadeInAndOutTextAnimation(
    private val screen: OnjScreen,
    x: Float,
    y: Float,
    initialText: String,
    fontColor: Color,
    private val fontScale: Float,
    font: BitmapFont,
    onjScreen: OnjScreen,
    duration: Int,
    fadeIn : Int,
    fadeOut : Int
) : FadeInAndOutAnimation(
    x, y,
    CustomLabel(screen, initialText, Label.LabelStyle(font, fontColor)),
    onjScreen,
    duration, fadeIn, fadeOut
) {

    private val label = actor as CustomLabel

    var text: String = initialText
        set(value) {
            field = value
            label.setText(value)
        }

    override fun start() {
        super.start()
        label.setFontScale(fontScale)
        label.setAlignment(Align.center)
        label.setPosition(x, y + label.prefHeight / 2, Align.center)
    }

    override fun toString(): String = "FadeInAndOutTextAnimation(${label.text})"

}
