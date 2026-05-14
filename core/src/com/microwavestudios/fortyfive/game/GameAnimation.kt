package com.microwavestudios.fortyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.rendering.BetterShader
import com.microwavestudios.fortyfive.screen.actors.CustomLabel
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.utils.alpha
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
    abstract fun update()

    /**
     * starts the animation
     */
    open fun start() {}

    /**
     * called after the animation has finished
     */
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
    private val screen: RenderableScreen,
    private val duration: Int,
    private val animationDuration: Int,
    private val beginScale: Float,
    private val endScale: Float,
    private val interpolation: Interpolation = Interpolation.linear,
    private val customShader: BetterShader? = null,
) : GameAnimation() {

    private var startTime = 0L
    private var runUntil = 0L

    private val renderTask = { batch: Batch ->
        val timeDiff = TimeUtils.millis() - startTime
        var percent = min(timeDiff.toFloat() / animationDuration.toFloat(), 1f)
        percent = interpolation.apply(percent)
        val scale = beginScale + (endScale - beginScale) * percent

        val viewport = screen.stage.viewport
        val worldWidth = viewport.worldWidth
        val worldHeight = viewport.worldHeight

        customShader?.let {
            batch.flush()
            batch.shader = it.shader
            it.prepare(screen)
        }

        banner.draw(
            batch,
            worldWidth / 2 - (banner.minWidth * scale) / 2f,
            worldHeight / 2 - (banner.minHeight * scale) / 2,
            banner.minWidth * scale,
            banner.minHeight * scale,
        )

        customShader.let {
            batch.flush()
            batch.shader = null
        }
    }

    override fun start() {
        startTime = TimeUtils.millis()
        runUntil = startTime + duration
        screen.addLateRenderTask(renderTask)
        customShader?.resetReferenceTime()
    }

    override fun update() { }

    override fun isFinished(): Boolean = TimeUtils.millis() >= runUntil

    override fun end() {
        screen.removeLateRenderTask(renderTask)
    }

    override fun toString(): String = "BannerAnimation"
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
    private val customScreen: RenderableScreen,
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
        customScreen.addActorToRoot(actor)
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
        customScreen.removeActorFromRoot(actor)
    }

    override fun toString(): String = "FadeInAndOutAnimation"
}
