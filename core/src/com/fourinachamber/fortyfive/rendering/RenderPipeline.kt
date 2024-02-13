package com.fourinachamber.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.CatmullRomSpline
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.*
import java.lang.Long.max
import kotlin.math.absoluteValue

interface Renderable {

    fun render(delta: Float)
}

open class RenderPipeline(
    protected val screen: OnjScreen,
    private val baseRenderable: Renderable
) : Disposable {

    var showFps: Boolean = false

    protected val frameBufferManager: FrameBufferManager = FrameBufferManager()

    protected var hasBeenDisposed: Boolean = false
        private set

    protected open val earlyTasks: MutableList<() -> Unit> = mutableListOf()
    protected open val lateTasks: MutableList<() -> Unit> = mutableListOf()

    protected open val postPreprocessingSteps: MutableList<() -> Unit> = mutableListOf()

    protected val batch: SpriteBatch = SpriteBatch()

    private val orbAnimations: MutableList<OrbAnimation> = mutableListOf()

    private val alphaReductionShaderDelegate = lazy {
        ResourceManager.get<BetterShader>(screen, "alpha_reduction_shader")
    }
    private val alphaReductionShader: BetterShader by alphaReductionShaderDelegate

    private val gaussianBlurShaderDelegate = lazy {
        ResourceManager.get<BetterShader>(screen, "gaussian_blur_shader")
    }
    private val gaussianBlurShader: BetterShader by gaussianBlurShaderDelegate

    private var orbFinisesAt: Long = -1
    private val isOrbAnimActive: Boolean
        get() = TimeUtils.millis() <= orbFinisesAt

    init {
        frameBufferManager.addPingPongFrameBuffer("orb", 1f)
        frameBufferManager.addPingPongFrameBuffer("pp", 1f)
    }

    private fun updateOrbFbo(delta: Float) {
        val (active, inactive) = frameBufferManager.getPingPongFrameBuffers("orb") ?: return

        active.begin()
        ScreenUtils.clear(0f, 0f, 0f, 0f)
        batch.begin()

        val shader = alphaReductionShader
        batch.flush()
        batch.shader = shader.shader
        shader.shader.bind()
        shader.shader.setUniformf("u_alphaReduction", delta * 1.3f)
        shader.prepare(screen)
        batch.enableBlending()
        batch.setBlendFunctionSeparate(GL20.GL_ONE, GL20.GL_ZERO, GL20.GL_ONE, GL20.GL_ZERO)
        batch.draw(
            inactive.colorBufferTexture,
            0f, 0f,
            screen.viewport.worldWidth,
            screen.viewport.worldHeight,
            0f, 0f, 1f, 1f // flips the y-axis
        )
        batch.flush()
        batch.shader = null
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        val toRemove = mutableListOf<OrbAnimation>()
        orbAnimations.forEach { anim ->
            val time = TimeUtils.millis() - anim.startTime
            val progress = time.toFloat() / anim.duration.toFloat()
            val lastProgress = anim.lastProgress
            if (progress >= 1f) {
                toRemove.add(anim)
                return@forEach
            }

            val drawable = ResourceManager.get<Drawable>(screen, anim.orbTexture)

            val segments = anim.segments + 1
            var curProgress = lastProgress
            repeat(segments) {
                if (it == segments - 1) return@repeat
                curProgress += (progress - lastProgress) / segments
                val position = anim.position(curProgress)
                drawable.draw(
                    batch,
                    position.x, position.y,
                    anim.width, anim.height
                )
            }

            anim.lastProgress = progress
        }
        orbAnimations.removeAll(toRemove)

        batch.end()
        active.end()
        frameBufferManager.swapPingPongFrameBuffers("orb")
    }

    private fun renderOrbFbo() {
        val (active, inactive) = frameBufferManager.getPingPongFrameBuffers("orb") ?: return
        val shader = gaussianBlurShader
        batch.flush()
        batch.enableBlending()
        active.begin()
        ScreenUtils.clear(0f, 0f, 0f, 0f)
        shader.shader.bind()
        shader.prepare(screen)
        batch.shader = shader.shader
        batch.enableBlending()
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shader.shader.setUniformf("u_dir", 1f, 0f)
        shader.shader.setUniformf("u_radius", 2.5f)
        batch.draw(
            inactive.colorBufferTexture,
            0f, 0f,
            screen.viewport.worldWidth,
            screen.viewport.worldHeight,
            0f, 0f, 1f, 1f // flips the y-axis
        )
        batch.flush()
        active.end()
        screen.viewport.apply()
        batch.projectionMatrix = screen.viewport.camera.combined
        shader.shader.setUniformf("u_dir", 0f, 1f)
        batch.draw(
            active.colorBufferTexture,
            0f, 0f,
            screen.viewport.worldWidth,
            screen.viewport.worldHeight,
            0f, 0f, 1f, 1f // flips the y-axis
        )
        batch.flush()
        batch.shader = null
        orbAnimations.forEach { anim ->
            val drawable = ResourceManager.get<Drawable>(screen, anim.orbTexture)
            val time = TimeUtils.millis() - anim.startTime
            val progress = time.toFloat() / anim.duration.toFloat()
            val position = anim.position(progress)
            drawable.draw(
                batch,
                position.x, position.y,
                anim.width, anim.height
            )
        }
    }

    open fun render(delta: Float) {
        if (hasBeenDisposed) return
        ScreenUtils.clear(0.0f, 0.0f, 0.0f, 1.0f)
        screen.viewport.apply()
        batch.projectionMatrix = screen.viewport.camera.combined
        if (isOrbAnimActive) updateOrbFbo(delta)
        if (postPreprocessingSteps.isEmpty()) {
            baseRenderable.render(delta)
            batch.begin()
            earlyTasks.forEach { it() }
            if (isOrbAnimActive) renderOrbFbo()
            lateTasks.forEach { it() }
            batch.end()
        } else {
            renderWithPostProcessors(delta)
        }
        if (!showFps) return
        val font = ResourceManager.get<BitmapFont>(screen, "red_wing_bmp")
        font.data.setScale(0.3f)
        val fps = Gdx.graphics.framesPerSecond
        val layout = GlyphLayout(font, "fps: $fps", Color.WHITE, 200f, 0, false)
        val viewport = ExtendViewport(1600f, 900f)
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        batch.begin()
        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        font.draw(batch, layout, viewport.worldWidth - 200f, 880f)
        batch.end()
    }

    private fun renderWithPostProcessors(delta: Float) {
        val (active, _) = frameBufferManager.getPingPongFrameBuffers("pp") ?: return
        active.begin()
        baseRenderable.render(delta)
        screen.viewport.apply()
        batch.projectionMatrix = screen.viewport.camera.combined
        batch.begin()
        earlyTasks.forEach { it() }
        batch.end()
        active.end()
        batch.begin()
        if (isOrbAnimActive) renderOrbFbo()
        postPreprocessingSteps.forEachIndexed { index, step ->
            frameBufferManager.swapPingPongFrameBuffers("pp")
            val (@Suppress("NAME_SHADOWING") active, _) = frameBufferManager.getPingPongFrameBuffers("pp") ?: return
            val last = index == postPreprocessingSteps.size - 1
            if (!last) active.begin()
            step()
            if (!last) active.end()
        }
        batch.end()
        batch.begin()
        screen.viewport.apply()
        batch.projectionMatrix = screen.viewport.camera.combined
        lateTasks.forEach { it() }
        batch.end()
    }

    protected fun shaderPostProcessingStep(shader: BetterShader): () -> Unit = lambda@{
        val (_, inactive) = frameBufferManager.getPingPongFrameBuffers("pp") ?: return@lambda
        batch.flush()
        batch.shader = shader.shader
        shader.shader.bind()
        shader.prepare(screen)
        batch.enableBlending()
        batch.draw(
            inactive.colorBufferTexture,
            0f, 0f,
            screen.viewport.worldWidth,
            screen.viewport.worldHeight,
            0f, 0f, 1f, 1f // flips the y-axis
        )
        batch.flush()
        batch.shader = null
    }

    fun addOrbAnimation(orbAnimation: OrbAnimation) {
        orbAnimation.startTime = TimeUtils.millis()
        orbAnimations.add(orbAnimation)
        orbFinisesAt = max(orbFinisesAt, TimeUtils.millis() + orbAnimation.duration + 500L)
    }

    open fun sizeChanged() {
        frameBufferManager.sizeChanged()
    }

    override fun dispose() {
        hasBeenDisposed = true
        frameBufferManager.dispose()
        batch.dispose()
    }

    data class OrbAnimation(
        var startTime: Long = 0L,
        var lastProgress: Float = 0f,
        val orbTexture: ResourceHandle,
        val width: Float,
        val height: Float,
        val duration: Int,
        val segments: Int,
        val position: (progress: Float) -> Vector2,
    ) {

        companion object {

            fun linear(start: Vector2, end: Vector2): (progress: Float) -> Vector2 = { progress ->
                Vector2(
                    start.x + (end.x - start.x) * progress,
                    start.y + (end.y - start.y) * progress,
                )
            }

            fun curvedPath(start: Vector2, end: Vector2, curveOffsetMultiplier: Float = 1f): (progress: Float) -> Vector2 {
                val midpoint = start midPoint end
                val length = (end - start).len().absoluteValue
                val controlPoints = arrayOf(
                    start,
                    start,
                    midpoint + midpoint.normal.withMag(length * 0.15f * curveOffsetMultiplier),
                    end,
                    end,
                )
                val spline = CatmullRomSpline(controlPoints, false)
                return { progress ->
                    val result = Vector2()
                    spline.valueAt(result, progress)
                    result
                }
            }

        }
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
    }

}

class FrameBufferManager : Disposable {

    private val singleBuffers: MutableMap<String, Pair<Float, FrameBuffer?>> = mutableMapOf()
    private val pingPongBuffers: MutableMap<String, Pair<Float, Pair<FrameBuffer, FrameBuffer>?>> = mutableMapOf()

    fun addFrameBuffer(name: String, sizeMultiplier: Float) {
        if (singleBuffers.containsKey(name)) throw RuntimeException("single FrameBuffer with name $name already exists")
        singleBuffers[name] = sizeMultiplier to null
    }

    fun getFrameBuffer(name: String): FrameBuffer? {
        val (sizeMultiplier, buffer) = singleBuffers[name] ?: throw RuntimeException("no single FrameBuffer with name $name")
        if (buffer != null) return buffer
        val newBuffer = tryCreateFrameBuffer(sizeMultiplier)
        singleBuffers[name] = sizeMultiplier to newBuffer
        return newBuffer
    }

    private fun tryCreateFrameBuffer(sizeMultiplier: Float): FrameBuffer? = try {
        val fbo = FrameBuffer(
            Pixmap.Format.RGBA8888,
            (Gdx.graphics.width * sizeMultiplier).toInt(),
            (Gdx.graphics.height * sizeMultiplier).toInt(),
            false
        )
        fbo.begin()
        ScreenUtils.clear(0f, 0f, 0f, 0f)
        fbo.end()
        fbo
    } catch (e: java.lang.IllegalStateException) {
        null
    }

    fun addPingPongFrameBuffer(name: String, sizeMultiplier: Float) {
        if (pingPongBuffers.containsKey(name)) throw RuntimeException("ping pong FrameBuffer with name $name already exists")
        pingPongBuffers[name] = sizeMultiplier to null
    }

    fun getPingPongFrameBuffers(name: String): Pair<FrameBuffer, FrameBuffer>? {
        val (sizeMultiplier, buffers) = pingPongBuffers[name] ?: throw RuntimeException("no ping pong FrameBuffer with name $name")
        if (buffers != null) return buffers
        val newBuffers = tryCreateFrameBuffer(sizeMultiplier)
            ?.let { first -> tryCreateFrameBuffer(sizeMultiplier)?.let { first to it } }
        pingPongBuffers[name] = sizeMultiplier to newBuffers
        return newBuffers
    }

    fun swapPingPongFrameBuffers(name: String) {
        val (sizeMultiplier, buffers) = pingPongBuffers[name] ?: throw RuntimeException("no ping pong FrameBuffer with name $name")
        pingPongBuffers[name] = sizeMultiplier to (buffers?.let { it.second to it.first })
    }

    fun sizeChanged() {
        singleBuffers.replaceAll { _, (sizeMultiplier, buffer) ->
            buffer?.dispose()
            sizeMultiplier to null
        }
        pingPongBuffers.replaceAll { _, (sizeMultiplier, buffers) ->
            buffers?.let {
                it.first.dispose()
                it.second.dispose()
            }
            sizeMultiplier to null
        }
    }

    override fun dispose() {
        singleBuffers.values.forEach { (_, buffer) -> buffer?.dispose() }
        pingPongBuffers.values.forEach { (_, buffers) ->
            buffers?.let {
                it.first.dispose()
                it.second.dispose()
            }
        }
    }
}
