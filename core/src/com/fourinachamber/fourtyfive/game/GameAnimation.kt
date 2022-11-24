package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import java.lang.Float.min
import java.util.concurrent.TimeUnit

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
