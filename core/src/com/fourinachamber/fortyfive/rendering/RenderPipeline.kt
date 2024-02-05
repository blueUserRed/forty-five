package com.fourinachamber.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Timeline

interface Renderable {

    fun render(delta: Float)
}

open class RenderPipeline(
    protected val screen: OnjScreen,
    private val baseRenderable: Renderable
) : Disposable {

    private var _fbo: FrameBuffer? = null

    protected val fbo: FrameBuffer?
        get() {
            if (_fbo != null) return _fbo
            _fbo = try {
                println("creating new fbo")
                FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.width, Gdx.graphics.height, false)
            } catch (e: java.lang.IllegalStateException) {
                null
            }
            return _fbo
        }

    protected open val earlyTasks: MutableList<() -> Unit> = mutableListOf()
    protected open val lateTasks: MutableList<() -> Unit> = mutableListOf()

    protected open val postPreprocessingSteps: MutableList<() -> Unit> = mutableListOf()

    protected val batch: SpriteBatch = SpriteBatch()

    open fun init() {
    }

    open fun render(delta: Float) {
        ScreenUtils.clear(0.0f, 0.0f, 0.0f, 1.0f)
        if (postPreprocessingSteps.isEmpty()) {
            batch.begin()
            earlyTasks.forEach { it() }
            baseRenderable.render(delta)
            lateTasks.forEach { it() }
            batch.end()
        } else {
            renderWithPostProcessors(delta)
        }
    }

    private fun renderWithPostProcessors(delta: Float) {
        val fbo = fbo ?: return
        fbo.begin()
        baseRenderable.render(delta)
        batch.begin()
        earlyTasks.forEach { it() }
        batch.end()
        fbo.end()
        batch.begin()
        screen.viewport.apply()
        batch.projectionMatrix = screen.viewport.camera.combined
        postPreprocessingSteps.forEachIndexed { index, step ->
            val last = index == postPreprocessingSteps.size - 1
            if (!last) fbo.begin()
            step()
            if (!last) fbo.end()
        }
        batch.flush()
        screen.viewport.apply()
        batch.projectionMatrix = screen.viewport.camera.combined
        lateTasks.forEach { it() }
        batch.end()
    }

    protected fun shaderPostProcessingStep(shader: BetterShader): () -> Unit = lambda@{
        val fbo = fbo ?: return@lambda
        batch.flush()
        batch.shader = shader.shader
        shader.shader.bind()
        shader.prepare(screen)
        batch.enableBlending()
        batch.draw(
            fbo.colorBufferTexture,
            0f, 0f,
            screen.viewport.worldWidth,
            screen.viewport.worldHeight,
            0f, 0f, 1f, 1f // flips the y-axis
        )
        batch.flush()
        batch.shader = null
    }

    open fun sizeChanged() {
        _fbo?.dispose()
        _fbo = null
    }

    override fun dispose() {
        _fbo?.dispose()
        batch.dispose()
    }
}

class GameRenderPipeline(screen: OnjScreen) : RenderPipeline(screen, screen) {

    private val shapeRenderer: ShapeRenderer = ShapeRenderer()

    private var fadeFinishesAt: Long = -1
    private val fadeDuration: Int = 2000

    private val fadeToBlackTask: () -> Unit = {
        val now = TimeUtils.millis()
        screen.viewport.apply()
        shapeRenderer.projectionMatrix = screen.viewport.camera.combined
        shapeRenderer.begin(ShapeType.Filled)
        val remaining = (fadeFinishesAt - now).toFloat()
        val alpha = 1f - remaining / fadeDuration.toFloat()
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.color = Color(0f, 0f, 0f, alpha)
        shapeRenderer.rect(0f, 0f, screen.viewport.worldWidth, screen.viewport.worldHeight)
        shapeRenderer.end()
    }

    private val shootShaderDelegate = lazy {
        GraphicsConfig.shootShader(screen)
    }
    private val shootShader: BetterShader by shootShaderDelegate
    private val shootPostProcessingStep: () -> Unit by lazy {
        shaderPostProcessingStep(shootShader)
    }

    private val parryShaderDelegate = lazy {
        ResourceManager.get<BetterShader>(screen, "parry_shader")
    }
    private val parryShader: BetterShader by parryShaderDelegate
    private val parryPostProcessingStep: () -> Unit by lazy {
        shaderPostProcessingStep(parryShader)
    }

    fun getOnDeathPostProcessingTimeline(): Timeline = Timeline.timeline {
        action {
            fadeFinishesAt = TimeUtils.millis() + fadeDuration
            lateTasks.add(fadeToBlackTask)
        }
        delayUntil { TimeUtils.millis() >= fadeFinishesAt }
        action {
            lateTasks.remove(fadeToBlackTask)
        }
    }

    fun getOnShotPostProcessingTimeline(): Timeline = Timeline.timeline {
        val duration = GraphicsConfig.shootPostProcessingDuration()
        action {
            shootShader.resetReferenceTime()
            postPreprocessingSteps.add(shootPostProcessingStep)
        }
        delay(duration)
        action {
            postPreprocessingSteps.remove(shootPostProcessingStep)
        }
    }

    fun startParryEffect() {
        postPreprocessingSteps.add(parryPostProcessingStep)
    }

    fun stopParryEffect() {
        postPreprocessingSteps.remove(parryPostProcessingStep)
    }

    override fun dispose() {
        super.dispose()
        shapeRenderer.dispose()
        if (shootShaderDelegate.isInitialized()) shootShader.dispose()
        if (parryShaderDelegate.isInitialized()) parryShader.dispose()
    }

}
