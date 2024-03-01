package com.fourinachamber.fortyfive.rendering

import com.badlogic.gdx.Gdx
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
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.game.UserPrefs
import com.fourinachamber.fortyfive.screen.Resource
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

    private val screenShakeShaderDelegate = lazy {
        ResourceManager.get<BetterShader>(screen, "screen_shake_shader")
    }
    private val screenShakeShader: BetterShader by screenShakeShaderDelegate

    private val gaussianBlurShaderDelegate = lazy {
        ResourceManager.get<BetterShader>(screen, "gaussian_blur_shader")
    }
    private val gaussianBlurShader: BetterShader by gaussianBlurShaderDelegate

    private var orbFinisesAt: Long = -1
    private val isOrbAnimActive: Boolean
        get() = TimeUtils.millis() <= orbFinisesAt


    private val shapeRenderer: ShapeRenderer = ShapeRenderer()

    private var fadeDuration: Int = -1
    private var fadeFinishesAt: Long = -1

    private val screenShakePostProcessingStep: () -> Unit by lazy {
        shaderPostProcessingStep(screenShakeShader)
    }

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

    init {
        frameBufferManager.addPingPongFrameBuffer("orb",  Pixmap.Format.RGBA8888, 0.5f)
        frameBufferManager.addPingPongFrameBuffer("pp", Pixmap.Format.RGB888, 1f)
    }

    fun getFadeToBlackTimeline(fadeDuration: Int, stayBlack: Boolean = false): Timeline = Timeline.timeline {
        action {
            this@RenderPipeline.fadeDuration = fadeDuration
            fadeFinishesAt = TimeUtils.millis() + fadeDuration
            lateTasks.add(fadeToBlackTask)
        }
        delayUntil { TimeUtils.millis() >= fadeFinishesAt }
        if (!stayBlack) action {
            lateTasks.remove(fadeToBlackTask)
        }
    }

    fun getScreenShakeTimeline(): Timeline = if (UserPrefs.enableScreenShake) Timeline.timeline {
        action { screenShakeShader.resetReferenceTime() }
        action { postPreprocessingSteps.add(screenShakePostProcessingStep) }
        delay(200)
        action { postPreprocessingSteps.remove(screenShakePostProcessingStep) }
    } else Timeline()

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
        showPerformanceInfo()
    }

    private fun showPerformanceInfo() {
        val font = ResourceManager.get<BitmapFont>(screen, "red_wing_bmp")
        font.data.setScale(0.2f)
        val fps = Gdx.graphics.framesPerSecond
        val loadedAssets = ResourceManager
            .resources
            .filter { it.state == Resource.ResourceState.LOADED || it.state == Resource.ResourceState.PREPARED }
            .size
        val javaHeap = String.format("%.3f", Gdx.app.javaHeap.toDouble() / (1000 * 1000))
        val nativeHeap = String.format("%.3f", Gdx.app.nativeHeap.toDouble() / (1000 * 1000))
        var text = """
            press 't' to toggle this display
            fps: $fps
            15s render lagSpike: ${screen.largestRenderTimeInLast15Sec()}ms
            15s avg. render time: ${screen.averageRenderTimeInLast15Sec()}ms
            active style managers: ${screen.styleManagerCount()}
            loaded assets: $loadedAssets/${ResourceManager.resources.size}
            version: ${FortyFiveLogger.versionTag}
        """.trimIndent()
//        text += """
//
//            javaHeap: $javaHeap
//            nativeHeap: $nativeHeap
//        """.trimIndent()
        val layout = GlyphLayout(
            font,
            text,
            Color.WHITE,
            200f,
            Align.topLeft,
            false
        )
        val viewport = ExtendViewport(1600f, 900f)
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        val shapeRenderer = ShapeRenderer()
        shapeRenderer.begin(ShapeType.Filled)
        viewport.apply()
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setColor(0f, 0f, 0f, 0.6f)
        shapeRenderer.rect(
            viewport.worldWidth - 50f - layout.width,
            880f - layout.height - 50f,
            500f,
            500f
        )
        shapeRenderer.end()
        shapeRenderer.dispose()
        batch.begin()
        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        font.draw(batch, layout, viewport.worldWidth - 300f, 880f)
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
//        if (isOrbAnimActive) renderOrbFbo()
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
        if (isOrbAnimActive) renderOrbFbo()
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
        shapeRenderer.dispose()
        batch.dispose()
        if (gaussianBlurShaderDelegate.isInitialized()) gaussianBlurShader.dispose()
        if (alphaReductionShaderDelegate.isInitialized()) alphaReductionShader.dispose()
        if (screenShakeShaderDelegate.isInitialized()) screenShakeShader.dispose()
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

    fun getOnShotPostProcessingTimeline(): Timeline = if (UserPrefs.enableScreenShake) Timeline.timeline {
        val duration = GraphicsConfig.shootPostProcessingDuration()
        action {
            shootShader.resetReferenceTime()
            postPreprocessingSteps.add(shootPostProcessingStep)
        }
        delay(duration)
        action {
            postPreprocessingSteps.remove(shootPostProcessingStep)
        }
    } else Timeline()

    fun startParryEffect() {
        postPreprocessingSteps.add(parryPostProcessingStep)
    }

    fun stopParryEffect() {
        postPreprocessingSteps.remove(parryPostProcessingStep)
    }

}

class FrameBufferManager : Disposable {

    private val singleBuffers: MutableMap<String, Triple<Float, Pixmap.Format, FrameBuffer?>> = mutableMapOf()
    private val pingPongBuffers: MutableMap<String, Triple<Float, Pixmap.Format, Pair<FrameBuffer, FrameBuffer>?>> = mutableMapOf()

    fun addFrameBuffer(name: String, format: Pixmap.Format, sizeMultiplier: Float) {
        if (singleBuffers.containsKey(name)) throw RuntimeException("single FrameBuffer with name $name already exists")
        singleBuffers[name] = Triple(sizeMultiplier, format,null)
    }

    fun getFrameBuffer(name: String): FrameBuffer? {
        val (sizeMultiplier, format, buffer) = singleBuffers[name] ?: throw RuntimeException("no single FrameBuffer with name $name")
        if (buffer != null) return buffer
        val newBuffer = tryCreateFrameBuffer(format, sizeMultiplier)
        singleBuffers[name] = Triple(sizeMultiplier, format, newBuffer)
        return newBuffer
    }

    private fun tryCreateFrameBuffer(format: Pixmap.Format, sizeMultiplier: Float): FrameBuffer? = try {
        val fbo = FrameBuffer(
            format,
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

    fun addPingPongFrameBuffer(name: String, format: Pixmap.Format, sizeMultiplier: Float) {
        if (pingPongBuffers.containsKey(name)) throw RuntimeException("ping pong FrameBuffer with name $name already exists")
        pingPongBuffers[name] = Triple(sizeMultiplier, format, null)
    }

    fun getPingPongFrameBuffers(name: String): Pair<FrameBuffer, FrameBuffer>? {
        val (sizeMultiplier, format, buffers) = pingPongBuffers[name] ?: throw RuntimeException("no ping pong FrameBuffer with name $name")
        if (buffers != null) return buffers
        val first = tryCreateFrameBuffer(format, sizeMultiplier) ?: return null
        val second = tryCreateFrameBuffer(format, sizeMultiplier) ?: run {
            first.dispose()
            return null
        }
        pingPongBuffers[name] = Triple(sizeMultiplier, format, first to second)
        return first to second
    }

    fun swapPingPongFrameBuffers(name: String) {
        val (sizeMultiplier, format, buffers) = pingPongBuffers[name] ?: throw RuntimeException("no ping pong FrameBuffer with name $name")
        pingPongBuffers[name] = Triple(sizeMultiplier, format, (buffers?.let { it.second to it.first }))
    }

    fun sizeChanged() {
        singleBuffers.replaceAll { _, (sizeMultiplier, format, buffer) ->
            buffer?.dispose()
            Triple(sizeMultiplier, format, null)
        }
        pingPongBuffers.replaceAll { _, (sizeMultiplier,format, buffers) ->
            buffers?.let {
                it.first.dispose()
                it.second.dispose()
            }
            Triple(sizeMultiplier, format, null)
        }
    }

    override fun dispose() {
        singleBuffers.values.forEach { (_, _, buffer) -> buffer?.dispose() }
        pingPongBuffers.values.forEach { (_, _, buffers) ->
            buffers?.let {
                it.first.dispose()
                it.second.dispose()
            }
        }
    }
}
