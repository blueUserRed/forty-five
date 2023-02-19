package com.fourinachamber.fourtyfive.rendering

import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.game.GraphicsConfig
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.utils.Timeline

interface Renderable {
    fun render(delta: Float)
}

class GameRenderPipeline(private val screen: OnjScreen) : Renderable {

    private val currentPostProcessingShaders = mutableListOf<BetterShader>()

    override fun render(delta: Float) {
        screen.render(delta)
    }

    fun enterDestroyMode() {

    }

    fun leaveDestroyMode() {

    }

    fun getOnShotPostProcessingTimelineAction(): Timeline.TimelineAction {
        val shader: BetterShader = GraphicsConfig.shootShader(screen)
        val duration = GraphicsConfig.shootPostProcessingDuration()
        return object : Timeline.TimelineAction() {

            var finishesAt: Long = -1

            override fun start(timeline: Timeline) {
                super.start(timeline)
                finishesAt = TimeUtils.millis() + duration
                currentPostProcessingShaders.add(shader)
            }

            override fun isFinished(): Boolean = TimeUtils.millis() >= finishesAt

            override fun end() {
                super.end()
                currentPostProcessingShaders.remove(shader)
            }
        }
    }

}
