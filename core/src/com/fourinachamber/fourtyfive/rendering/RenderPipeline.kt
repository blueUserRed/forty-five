package com.fourinachamber.fourtyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.game.GraphicsConfig
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.utils.Timeline

interface Renderable {

    // TODO: differentiate between renderables that can be rendered to a fbo and those who can not
    fun render(delta: Float)
}

class GameRenderPipeline(private val screen: OnjScreen) : Renderable {

    private val currentPostProcessingShaders = mutableListOf<BetterShader>()

    private val destroyModeShader by lazy {
        GraphicsConfig.destroyCardShader(screen)
    }

    private var sizeDirty = false

    override fun render(delta: Float) {
        if (sizeDirty) {
            screen.resize(Gdx.graphics.width, Gdx.graphics.height)
            sizeDirty = false
        }
        if (currentPostProcessingShaders.isEmpty()) {
            screen.render(delta)
            return
        }

        val fbo = try {
            FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.width, Gdx.graphics.height, false)
        } catch (e: java.lang.IllegalStateException) {
            // construction of FrameBuffer sometimes fails when the window is minimized
            return
        }

        fbo.begin()
        ScreenUtils.clear(0.0f, 0.0f, 0.0f, 1.0f)
        screen.stage.viewport.apply()
        screen.render(delta)
        fbo.end()

        currentPostProcessingShaders.forEachIndexed { index, shader ->
            val last = index == currentPostProcessingShaders.size - 1

            // TODO: this is either really smart or really stupid
            // TODO: this seems smart now but i bet it will blow up later

            val batch = SpriteBatch()
            if (!last) fbo.begin()
            batch.shader = shader.shader
            shader.shader.bind()
            shader.prepare(screen)
            batch.begin()
            batch.enableBlending()
            batch.draw(
                fbo.colorBufferTexture,
                0f, 0f,
                Gdx.graphics.width.toFloat(),
                Gdx.graphics.height.toFloat(),
                0f, 0f, 1f, 1f // flips the y-axis
            )
            batch.end()
            if (!last) fbo.end()
            batch.dispose()

        }
    }

    fun enterDestroyMode() {
        currentPostProcessingShaders.add(0, destroyModeShader)
        destroyModeShader.resetReferenceTime()
        sizeDirty = true
    }

    fun leaveDestroyMode() {
        currentPostProcessingShaders.remove(destroyModeShader)
        sizeDirty = true
    }

    fun getOnShotPostProcessingTimelineAction(): Timeline.TimelineAction {
        val shader = GraphicsConfig.shootShader(screen)
        val duration = GraphicsConfig.shootPostProcessingDuration()
        return object : Timeline.TimelineAction() {

            var finishesAt: Long = -1

            override fun start(timeline: Timeline) {
                super.start(timeline)
                finishesAt = TimeUtils.millis() + duration
                currentPostProcessingShaders.add(shader)
                shader.resetReferenceTime()
                sizeDirty = true
            }

            override fun isFinished(): Boolean = TimeUtils.millis() >= finishesAt

            override fun end() {
                super.end()
                currentPostProcessingShaders.remove(shader)
                sizeDirty = true
            }
        }
    }

}
