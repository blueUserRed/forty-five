package com.microwavestudios.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.PolygonRegion
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.utils.*

class RenderPipeline(val screen: CustomScreen) : ResourceBorrower {

    private val _lifetime = EndableLifetime()
    val lifetime: Lifetime
        get() = _lifetime

    private val batch: SpriteBatch = SpriteBatch().also { lifetime.tieDisposable(it) }

    private val shapeRenderer: ShapeRenderer by lazy {
        val renderer = ShapeRenderer()
        lifetime.tieDisposable(renderer)
        renderer
    }

    private val frameBufferManager: FrameBufferManager = FrameBufferManager().also {
//        it.addPingPongFrameBuffer("orb", Pixmap.Format.RGBA8888, true, 1f)
        it.addPingPongFrameBuffer("orb", Pixmap.Format.RGBA8888, true, 0.5f)
        lifetime.tieDisposable(it)
    }

    private val alphaReductionShader: Promise<BetterShader> =
        FortyFive.resourceManager.request(this, lifetime, "alpha_reduction_shader")

    private val postprocessor: Promise<BetterShader> =
        FortyFive.resourceManager.request(this, lifetime, "postprocessor_shader")

    private val gaussianBlurShader: Promise<BetterShader> =
        FortyFive.resourceManager.request(this, lifetime, "gaussian_blur_shader")

    private val earlyRenderTasks: MutableList<RenderTask> = mutableListOf()
    private val lateRenderTasks: MutableList<RenderTask> = mutableListOf()

    private val orbAnimations: MutableList<OrbAnimation> = mutableListOf()
    private var orbFinisesAt: Long = 0
    private val isOrbAnimActive: Boolean
        get() = orbAnimations.isNotEmpty() || TimeUtils.millis() <= orbFinisesAt

    private var shootReferenceTime: Long = -1
    private var popoutReferenceTime: Long = -1
    private var shakeReferenceTime: Long = -1

    fun render(deltaTime: Float) {
        val viewport = screen.viewport

        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        val frameBuffer = frameBufferManager.borrowFrameBuffer(true, screen) ?: return

        ScreenUtils.clear(0f, 0f, 0f, 1f)
        renderScreenToFrameBuffer(frameBuffer, deltaTime)
        postprocessAndRenderToScreen(frameBuffer)
        frameBufferManager.giveFrameBufferBack(frameBuffer)

        if (isOrbAnimActive) {
            updateOrbFbo(deltaTime)
            renderOrbFbo()
        }
        val debugMenu = screen.debugMenu ?: return
        if (!debugMenu.show) return
        renderDebugMenu(debugMenu)
    }

    private fun renderScreenToFrameBuffer(frameBuffer: FrameBuffer, deltaTime: Float) {
        val viewport = screen.viewport
        frameBuffer.begin()
        ScreenUtils.clear(0f, 0f, 0f, 1f)
        viewport.apply()
        viewport.update(frameBuffer.width, frameBuffer.height)
        screen.render(deltaTime)
        earlyRenderTasks.iterateRemoving { renderTask, remover ->
            if (renderTask.isComplete()) {
                renderTask.dispose()
                remover()
                return@iterateRemoving
            }
            renderTask.render(screen.stage.batch, screen)
        }
        frameBuffer.end()
    }

    private fun postprocessAndRenderToScreen(frameBuffer: FrameBuffer) {
        val viewport = screen.viewport
        val postprocessor = postprocessor.getOrNull() ?: return
        batch.begin()
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        batch.projectionMatrix = viewport.camera.combined
        batch.shader = postprocessor.shader
        postprocessor.prepare(screen)
        setPostprocessorUniforms(postprocessor)
        batch.draw(
            frameBuffer.colorBufferTexture,
            0f, 0f,
            viewport.worldWidth, viewport.worldHeight,
            0f, 0f, 1f, 1f // flips the y-axis
        )
        batch.end()
        lateRenderTasks.iterateRemoving { renderTask, remover ->
            if (renderTask.isComplete()) {
                renderTask.dispose()
                remover()
                return@iterateRemoving
            }
            renderTask.render(batch, screen)
        }
        batch.shader = null
    }

    private fun updateOrbFbo(delta: Float) {
        val viewport = screen.viewport
        if (alphaReductionShader.isNotResolved) FortyFive.resourceManager.forceResolve(alphaReductionShader)
        val alphaReductionShader = alphaReductionShader.getOrError()

        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        val (active, inactive) = frameBufferManager.getPingPongFrameBuffers("orb", screen) ?: return

        active.begin()
        ScreenUtils.clear(0f, 0f, 0f, 0f)

        batch.begin()
        viewport.update(active.width, active.height)
        batch.projectionMatrix = viewport.camera.combined

        batch.shader = alphaReductionShader.shader
        alphaReductionShader.prepare(screen)
        alphaReductionShader.shader.setUniformf("u_alphaReduction", delta * 1.3f)
        batch.enableBlending()
        batch.setBlendFunctionSeparate(
            GL20.GL_ONE, GL20.GL_ZERO,
            GL20.GL_ONE, GL20.GL_ZERO
        )
        batch.draw(
            inactive.colorBufferTexture,
            0f, 0f,
            viewport.worldWidth,
            viewport.worldHeight,
            0f, 0f, 1f, 1f // flips the y-axis
        )

        batch.flush()
        batch.shader = null
        batch.setBlendFunctionSeparate(
            GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
            GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA
        )

        val toRemove = mutableListOf<OrbAnimation>()
        orbAnimations.forEach { anim ->
            anim.newFrame()
            if (!anim.orbTexturePromise.isResolved) FortyFive.resourceManager.forceResolve(anim.orbTexturePromise)
            val drawable = anim.orbTexturePromise.getOrError()
            repeat(anim.segments) {
                if (anim.isFinished()) return@repeat
                anim.update()
                if (anim.isFinished()) {
                    toRemove.add(anim)
                    orbFinisesAt = TimeUtils.millis() + 500
                }
                val position = anim.position
                drawable.draw(
                    batch,
                    position.x, position.y,
                    anim.width, anim.height
                )
            }
        }
        orbAnimations.removeAll(toRemove)
        batch.end()
        active.end()
        frameBufferManager.swapPingPongFrameBuffers("orb")
    }

    private fun renderOrbFbo() {
        val viewport = screen.viewport
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        val (active, inactive) = frameBufferManager.getPingPongFrameBuffers("orb", screen) ?: return
        if (!gaussianBlurShader.isResolved) FortyFive.resourceManager.forceResolve(gaussianBlurShader)
        val shader = gaussianBlurShader.getOrError()

        active.begin()
        ScreenUtils.clear(0f, 0f, 0f, 0f)

        batch.begin()
        viewport.update(active.width, active.height)
        batch.projectionMatrix = viewport.camera.combined
        batch.shader = shader.shader
        shader.prepare(screen)
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
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        batch.projectionMatrix = viewport.camera.combined
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
            if (!anim.orbTexturePromise.isResolved) FortyFive.resourceManager.forceResolve(anim.orbTexturePromise)
            val drawable = anim.orbTexturePromise.getOrError()
            val position = anim.position
            drawable.draw(
                batch,
                position.x, position.y,
                anim.width, anim.height
            )
        }
        batch.end()
    }


    private fun setPostprocessorUniforms(postprocessor: BetterShader) {
        println(shootReferenceTime)
        postprocessor.shader.setUniformi("u_doShoot", if (shootReferenceTime == -1L) 0 else 1)
        postprocessor.shader.setUniformf("u_shootTime", TimeUtils.timeSinceMillis(shootReferenceTime).toFloat() / 1000f)
        postprocessor.shader.setUniformi("u_doPopout", if (popoutReferenceTime == -1L) 0 else 1)
        postprocessor.shader.setUniformf("u_popoutTime", TimeUtils.timeSinceMillis(popoutReferenceTime).toFloat() / 1000f)
        postprocessor.shader.setUniformi("u_doShake", if (shakeReferenceTime == -1L) 0 else 1)
        postprocessor.shader.setUniformf("u_shakeTime", TimeUtils.timeSinceMillis(shakeReferenceTime).toFloat() / 1000f)
    }

    fun sizeChanged() {
        frameBufferManager.sizeChanged()
    }

    fun dispose() {
        _lifetime.die()
        earlyRenderTasks.forEach { it.dispose() }
        lateRenderTasks.forEach { it.dispose() }
    }

    fun addEarlyRenderTask(task: RenderTask) {
        task.start()
        earlyRenderTasks.add(task)
    }

    fun addLateRenderTask(task: RenderTask) {
        task.start()
        lateRenderTasks.add(task)
    }

    fun getGeometricFadeTimeline(
        duration: Int,
        reverse: Boolean = false,
        stayBlack: Boolean = false
    ): Timeline = Timeline.timeline {
        action {
            val task = GeometricFadeRenderTask(duration, reverse, stayBlack)
            addLateRenderTask(task)
        }
        delay(duration)
    }

    fun getFadeToBlackTimeline(
        duration: Int,
        reverse: Boolean = false,
        stayBlack: Boolean = false
    ): Timeline = Timeline.timeline {
        action {
            val task = FadeToBlackRenderTask(duration, reverse, stayBlack)
            addLateRenderTask(task)
        }
        delay(duration)
    }

    fun getScreenShakeTimeline(): Timeline {
        if (!FortyFive.globalSave.enableScreenShake) return Timeline()
        return Timeline.timeline {
            action { shakeReferenceTime = TimeUtils.millis() }
            delay(50)
            action { screen.inputManager.vibrateController(50, 0.6f) }
            delay(150)
            action { shakeReferenceTime = -1 }
        }
    }

    fun getScreenShakePopoutTimeline(): Timeline {
        if (!FortyFive.globalSave.enableScreenShake) return Timeline()
        return Timeline.timeline {
            action {
                popoutReferenceTime = TimeUtils.millis()
                screen.inputManager.vibrateController(100, 0.6f)
            }
            delay(((1f / 30f) * 1000f).toInt())
            action { popoutReferenceTime = -1 }
        }
    }

    fun getOnShotPostProcessingTimeline(): Timeline {
        if (!FortyFive.globalSave.enableScreenShake) return Timeline()
        val duration = 90
        return Timeline.timeline {
            action {
                shootReferenceTime = TimeUtils.millis()
                screen.inputManager.vibrateController(duration, 0.5f)
            }
            delay(duration)
            action { shootReferenceTime = -1 }
        }
    }

    fun startParryEffect() {

    }

    fun stopParryEffect() {

    }

    fun addOrbAnimation(orbAnimation: OrbAnimation) {
        orbAnimations.add(orbAnimation)
    }

    private fun renderDebugMenu(menu: DebugMenu) {
        val shapeRenderer = shapeRenderer
        val viewport = screen.viewport
        menu.update()
        val font = FortyFive.resourceManager.forceGet<BitmapFont>(this, lifetime, "redwing100")
                font.data.setScale(0.2f)
            val page = menu.currentPage()
        val pageText = page.getText(screen)
        var text = ""
        text += "* ---${page.name}---\n"
        text += "* Press alt + d to toggle the debug menu.\n"
        text += "* Use j/k to change page\n"
        text += "* page: ${menu.currentPageNumber()}/${menu.amountOfPages()}\n\n"
        text += pageText
        val layout = GlyphLayout(
            font,
            text,
            Color.WHITE,
            400f,
            Align.topLeft,
            false
        )
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setColor(0f, 0f, 0f, 0.8f)
        shapeRenderer.rect(
            viewport.worldWidth - 50f - layout.width,
            viewport.worldHeight - layout.height - 100f,
            layout.width + 100f,
            layout.height + 100f
        )
        shapeRenderer.end()
        batch.begin()
        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        font.draw(batch, layout, viewport.worldWidth - layout.width - 30, viewport.worldHeight - 50f)
        batch.end()
    }

    class OrbAnimation(
        val orbTexture: ResourceHandle,
        val width: Float,
        val height: Float,
        val renderPipeline: RenderPipeline,
        initialPosition: Vector2,
        initialVelocity: Vector2,
        val acceleration: Float,
        val speedCap: Float,
        val segments: Int,
        val velocityRampStart: Int,
        val velocityRamp: Float,
        val target: () -> Vector2
    ) {

        val orbTexturePromise: Promise<Drawable> =
            FortyFive.resourceManager.request(renderPipeline, renderPipeline.lifetime, orbTexture)

        var position: Vector2 = initialPosition
            private set

        private var velocity: Vector2 = initialVelocity

        private val currentTarget: Vector2 = Vector2(0, 0)
        private var currentDelta: Float = 0f
        private var finished: Boolean = false

        private var maxTime: Long = -1
        private var currentAcceleration: Float = acceleration

        fun newFrame() {
            if (maxTime == -1L) maxTime = TimeUtils.millis() + velocityRampStart
            currentTarget.set(target())
            currentDelta = Gdx.graphics.deltaTime
            if (TimeUtils.millis() > maxTime) currentAcceleration *= velocityRamp
        }

        fun update() {
            val delta = currentDelta / segments
            val targetDirection = (currentTarget - position).unit
            val accel = targetDirection.withMag(currentAcceleration)
            velocity += accel
            velocity.clamp(-speedCap, speedCap)
            val scaledVelocity = velocity * delta
            position += scaledVelocity
            val dist = (position - currentTarget).len()
            if (dist <= 50) {
                finished = true
            }
        }

        fun isFinished(): Boolean = finished
    }

}

interface RenderTask {

    fun start()

    fun render(batch: Batch, screen: CustomScreen)

    fun dispose()

    fun isComplete(): Boolean
}

class FadeToBlackRenderTask(
    private var duration: Int,
    private val reverse: Boolean,
    private val stayBlack: Boolean
) : RenderTask {

    private val lifetime = EndableLifetime()

    private val shapeRenderer = ShapeRenderer().also { lifetime.tieDisposable(it) }

    private var fadeFinishesAt: Long = -1

    override fun start() {
        fadeFinishesAt = TimeUtils.millis() + duration
    }

    override fun render(
        batch: Batch,
        screen: CustomScreen
    ) {
        val now = TimeUtils.millis()
        screen.viewport.apply()
        shapeRenderer.projectionMatrix = screen.viewport.camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        val remaining = (fadeFinishesAt - now).toFloat()
        val alpha = if (reverse) {
            remaining / duration.toFloat()
        } else {
            1f - remaining / duration.toFloat()
        }
//        Gdx.gl.glEnable(GL20.GL_BLEND)
//        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.color = Color(0f, 0f, 0f, alpha)
        shapeRenderer.rect(0f, 0f, screen.viewport.worldWidth, screen.viewport.worldHeight)
        shapeRenderer.end()
    }

    override fun dispose() {
        lifetime.die()
    }

    override fun isComplete(): Boolean = TimeUtils.millis() >= fadeFinishesAt && !stayBlack
}

class GeometricFadeRenderTask(
    private var duration: Int,
    private val reverse: Boolean,
    private val stayBlack: Boolean
) : RenderTask, ResourceBorrower {

    private val lifetime = EndableLifetime()

    private val polygonBatch = PolygonSpriteBatch().also { lifetime.tieDisposable(it) }

    private var fadeFinishesAt: Long = -1

    private val blackTexture: Promise<Texture> = FortyFive.resourceManager.request(this, lifetime, "black_texture")

    override fun start() {
        fadeFinishesAt = TimeUtils.millis() + duration
    }

    override fun render(
        batch: Batch,
        screen: CustomScreen
    ) {
        val batch = polygonBatch
        batch.projectionMatrix = screen.viewport.camera.combined

        val now = TimeUtils.millis()
        val remaining = (fadeFinishesAt - now).toFloat()
        var percent = (remaining / duration).between(0f, 1f)
        val interpolation = Interpolation.pow3
        percent = interpolation.apply(percent)

        val width = screen.viewport.worldWidth
        val height = screen.viewport.worldHeight
        val overshoot = 300f
        val offX = if (reverse) {
            -(width + overshoot * 3) * (1f - percent)
        } else {
            (-overshoot) * (1f - percent) + (width + overshoot) * percent
        }
        val region = PolygonRegion(
            TextureRegion(blackTexture.getOrNull() ?: return),
            floatArrayOf(
                0f, 0f,
                -overshoot / width, 1f,
                1f + overshoot / width, 1f,
                1f, 0f
            ),
            shortArrayOf(
                0, 1, 2,
                0, 2, 3,
            )
        )
        batch.begin()
        batch.draw(region, offX, 0f, width + overshoot, height)
        batch.end()
    }

    override fun isComplete(): Boolean = TimeUtils.millis() >= fadeFinishesAt && !stayBlack

    override fun dispose() {
        lifetime.die()
    }
}

//interface Renderable {
//
//    fun render(delta: Float)
//}
//
//open class RenderPipeline(
//    protected val screen: CustomScreen,
//    private val baseRenderable: Renderable
//) : Disposable, ResourceBorrower {
//
//    private val _lifetime: EndableLifetime = EndableLifetime()
//    val lifetime: Lifetime
//        get() = _lifetime
//
//    protected val frameBufferManager: FrameBufferManager = FrameBufferManager()
//
//    protected var hasBeenDisposed: Boolean = false
//        private set
//
//    protected open val earlyTasks: MutableList<() -> Unit> = mutableListOf()
//    protected open val lateTasks: MutableList<() -> Unit> = mutableListOf()
//
//    protected open val postPreprocessingSteps: MutableList<() -> Unit> = mutableListOf()
//
//    protected val batch: SpriteBatch = SpriteBatch()
//    protected val polygonBatch: PolygonBatch = PolygonSpriteBatch()
//
//    private val orbAnimations: MutableList<OrbAnimation> = mutableListOf()
//
//    private val alphaReductionShader: Promise<BetterShader> =
//        FortyFive.resourceManager.request(this, lifetime, "alpha_reduction_shader")
//
//    private val screenShakeShader: Promise<BetterShader> =
//        FortyFive.resourceManager.request(this, lifetime, "screen_shake_shader")
//
//    private val screenShakePopoutShader: Promise<BetterShader> =
//        FortyFive.resourceManager.request(this, lifetime, "screen_shake_popout_shader")
//
//    private val gaussianBlurShader: Promise<BetterShader> =
//        FortyFive.resourceManager.request(this, lifetime, "gaussian_blur_shader")
//
//    private val blackTexture: Promise<Texture> =
//        FortyFive.resourceManager.request(this, lifetime, "black_texture")
//
//    private val postprocessor: Promise<BetterShader> =
//        FortyFive.resourceManager.request(this, lifetime, "postprocessor_shader")
//
//    private var orbFinisesAt: Long = -1
//    private val isOrbAnimActive: Boolean
//        get() = orbAnimations.isNotEmpty() || TimeUtils.millis() <= orbFinisesAt
//
//    private val shapeRenderer: ShapeRenderer = ShapeRenderer()
//
//    private var fadeDuration: Int = -1
//    private var fadeFinishesAt: Long = -1
//    private var reverseFade: Boolean = false
//
//    private var geometricFadeDuration: Int = -1
//    private var geometricFadeFinishesAt: Long = -1
//    private var reverseGeometricFade: Boolean = false
//
//    private val screenShakePostProcessingStep: () -> Unit by lazy {
//        shaderPostProcessingStep(screenShakeShader)
//    }
//
//    private val screenShakePopoutPostProcessingStep: () -> Unit by lazy {
//        shaderPostProcessingStep(screenShakePopoutShader)
//    }
//
//    private val fadeToBlackTask: () -> Unit = {
//        val now = TimeUtils.millis()
//        screen.viewport.apply()
//        shapeRenderer.projectionMatrix = screen.viewport.camera.combined
//        shapeRenderer.begin(ShapeType.Filled)
//        val remaining = (fadeFinishesAt - now).toFloat()
//        val alpha = if (reverseFade) {
//            remaining / fadeDuration.toFloat()
//        } else {
//            1f - remaining / fadeDuration.toFloat()
//        }
//        Gdx.gl.glEnable(GL20.GL_BLEND)
//        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
//        shapeRenderer.color = Color(0f, 0f, 0f, alpha)
//        shapeRenderer.rect(0f, 0f, screen.viewport.worldWidth, screen.viewport.worldHeight)
//        shapeRenderer.end()
//    }
//
//    private val geometricFadeTask: () -> Unit = lambda@{
//        screen.viewport.apply()
//        val batch = polygonBatch
//        batch.projectionMatrix = screen.viewport.camera.combined
//
//        val now = TimeUtils.millis()
//        val remaining = (geometricFadeFinishesAt - now).toFloat()
//        var percent = (remaining / geometricFadeDuration).between(0f, 1f)
//        val interpolation = Interpolation.pow3
//        percent = interpolation.apply(percent)
//
//        Gdx.gl.glEnable(GL20.GL_BLEND)
//        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
//        val width = screen.viewport.worldWidth
//        val height = screen.viewport.worldHeight
//        val overshoot = 300f
//        val offX = if (reverseGeometricFade) {
//            -(width + overshoot * 3) * (1f - percent)
//        } else {
//            (-overshoot) * (1f - percent) + (width + overshoot) * percent
//        }
//        val region = PolygonRegion(
//            TextureRegion(blackTexture.getOrNull() ?: return@lambda),
//            floatArrayOf(
//                0f, 0f,
//                -overshoot / width, 1f,
//                1f + overshoot / width, 1f,
//                1f, 0f
//            ),
//            shortArrayOf(
//                0, 1, 2,
//                0, 2, 3,
//            )
//        )
//        batch.begin()
//        batch.draw(region, offX, 0f, width + overshoot, height)
//        batch.end()
//    }
//
//    init {
//        frameBufferManager.addPingPongFrameBuffer("orb",  Pixmap.Format.RGBA8888, 0.5f)
//        frameBufferManager.addPingPongFrameBuffer("pp", Pixmap.Format.RGB888, 1f)
//        postPreprocessingSteps.add(shaderPostProcessingStep(postprocessor))
//    }
//
//    fun getGeometricFadeTimeline(
//        fadeDuration: Int,
//        reverse: Boolean,
//        stayBlack: Boolean = false
//    ): Timeline = Timeline.timeline {
//        action {
//            geometricFadeDuration = fadeDuration
//            geometricFadeFinishesAt = TimeUtils.millis() + fadeDuration
//            reverseGeometricFade = reverse
//            lateTasks.add(geometricFadeTask)
//        }
//        delayUntil { TimeUtils.millis() > geometricFadeFinishesAt }
//        if (!stayBlack) action {
//            lateTasks.remove(geometricFadeTask)
//        }
//    }
//
//    fun getFadeToBlackTimeline(
//        fadeDuration: Int,
//        stayBlack: Boolean = false,
//        reverse: Boolean = false
//    ): Timeline = Timeline.timeline {
//        action {
//            this@RenderPipeline.fadeDuration = fadeDuration
//            fadeFinishesAt = TimeUtils.millis() + fadeDuration
//            reverseFade = reverse
//            lateTasks.add(fadeToBlackTask)
//        }
//        delayUntil { TimeUtils.millis() >= fadeFinishesAt }
//        if (!stayBlack) action {
//            lateTasks.remove(fadeToBlackTask)
//        }
//    }
//
//    fun getScreenShakeTimeline(): Timeline = if (FortyFive.globalSave.enableScreenShake) Timeline.timeline {
//        if (!screenShakeShader.isResolved) FortyFive.resourceManager.forceResolve(screenShakeShader)
//        val screenShakeShader = screenShakeShader.getOrError()
//        action { screenShakeShader.resetReferenceTime() }
//        action { postPreprocessingSteps.add(screenShakePostProcessingStep) }
//        delay(50)
//        action { screen.inputManager.vibrateController(50, 0.6f) }
//        delay(150)
//        action { postPreprocessingSteps.remove(screenShakePostProcessingStep) }
//    } else Timeline()
//
//    fun getScreenShakePopoutTimeline(): Timeline = if (FortyFive.globalSave.enableScreenShake) Timeline.timeline {
//        if (!screenShakePopoutShader.isResolved) FortyFive.resourceManager.forceResolve(screenShakePopoutShader)
//        val screenShakePopoutShader = screenShakeShader.getOrError()
//        action { screenShakePopoutShader.resetReferenceTime() }
//        action {
//            postPreprocessingSteps.add(screenShakePopoutPostProcessingStep)
//            screen.inputManager.vibrateController(100, 0.6f)
//        }
//        delay(((1f / 30f) * 1000f).toInt())
//        action { postPreprocessingSteps.remove(screenShakePopoutPostProcessingStep) }
//    } else Timeline()
//
//    private fun updateOrbFbo(delta: Float) {
//        val (active, inactive) = frameBufferManager.getPingPongFrameBuffers("orb") ?: return
//
//        active.begin()
//        ScreenUtils.clear(0f, 0f, 0f, 0f)
//        batch.begin()
//
//        if (!alphaReductionShader.isResolved) FortyFive.resourceManager.forceResolve(alphaReductionShader)
//        val shader = alphaReductionShader.getOrError()
//        batch.flush()
//        batch.shader = shader.shader
//        shader.shader.bind()
//        shader.shader.setUniformf("u_alphaReduction", delta * 1.3f)
//        shader.prepare(screen)
//        batch.enableBlending()
//        batch.setBlendFunctionSeparate(GL20.GL_ONE, GL20.GL_ZERO, GL20.GL_ONE, GL20.GL_ZERO)
//        batch.draw(
//            inactive.colorBufferTexture,
//            0f, 0f,
//            screen.viewport.worldWidth,
//            screen.viewport.worldHeight,
//            0f, 0f, 1f, 1f // flips the y-axis
//        )
//        batch.flush()
//        batch.shader = null
//        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
//        val toRemove = mutableListOf<OrbAnimation>()
//        orbAnimations.forEach { anim ->
//            anim.newFrame()
//
//            if (!anim.orbTexturePromise.isResolved) FortyFive.resourceManager.forceResolve(anim.orbTexturePromise)
//            val drawable = anim.orbTexturePromise.getOrError()
//
//            repeat(anim.segments) {
//                if (anim.isFinished()) return@repeat
//                anim.update()
//                if (anim.isFinished()) {
//                    toRemove.add(anim)
//                    orbFinisesAt = TimeUtils.millis() + 500
//                }
//                val position = anim.position
//                drawable.draw(
//                    batch,
//                    position.x, position.y,
//                    anim.width, anim.height
//                )
//            }
//
//        }
//        orbAnimations.removeAll(toRemove)
//
//        batch.end()
//        active.end()
//        frameBufferManager.swapPingPongFrameBuffers("orb")
//    }
//
//    private fun renderOrbFbo() {
//        val (active, inactive) = frameBufferManager.getPingPongFrameBuffers("orb") ?: return
//        if (!gaussianBlurShader.isResolved) FortyFive.resourceManager.forceResolve(gaussianBlurShader)
//        val shader = gaussianBlurShader.getOrError()
//        batch.flush()
//        batch.enableBlending()
//        active.begin()
//        ScreenUtils.clear(0f, 0f, 0f, 0f)
//        shader.shader.bind()
//        shader.prepare(screen)
//        batch.shader = shader.shader
//        batch.enableBlending()
//        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
//        shader.shader.setUniformf("u_dir", 1f, 0f)
//        shader.shader.setUniformf("u_radius", 2.5f)
//        batch.draw(
//            inactive.colorBufferTexture,
//            0f, 0f,
//            screen.viewport.worldWidth,
//            screen.viewport.worldHeight,
//            0f, 0f, 1f, 1f // flips the y-axis
//        )
//        batch.flush()
//        active.end()
//        screen.viewport.apply()
//        batch.projectionMatrix = screen.viewport.camera.combined
//        shader.shader.setUniformf("u_dir", 0f, 1f)
//        batch.draw(
//            active.colorBufferTexture,
//            0f, 0f,
//            screen.viewport.worldWidth,
//            screen.viewport.worldHeight,
//            0f, 0f, 1f, 1f // flips the y-axis
//        )
//        batch.flush()
//        batch.shader = null
//        orbAnimations.forEach { anim ->
//            if (!anim.orbTexturePromise.isResolved) FortyFive.resourceManager.forceResolve(anim.orbTexturePromise)
//            val drawable = anim.orbTexturePromise.getOrError()
//            val position = anim.position
//            drawable.draw(
//                batch,
//                position.x, position.y,
//                anim.width, anim.height
//            )
//        }
//    }
//
//    open fun render(delta: Float) {
//        if (hasBeenDisposed) return
//        ScreenUtils.clear(0.0f, 0.0f, 0.0f, 1.0f)
//        screen.viewport.apply()
//        batch.projectionMatrix = screen.viewport.camera.combined
//        if (isOrbAnimActive) updateOrbFbo(delta)
//        if (postPreprocessingSteps.isEmpty()) {
//            baseRenderable.render(delta)
//            batch.begin()
//            earlyTasks.forEach { it() }
//            if (isOrbAnimActive) renderOrbFbo()
//            lateTasks.forEach { it() }
//            batch.end()
//        } else {
//            renderWithPostProcessors(delta)
//        }
//        val debugMenu = screen.debugMenu ?: return
//        if (!debugMenu.show) return
//        renderDebugMenu(debugMenu)
//    }
//
//    private fun renderDebugMenu(menu: DebugMenu) {
//        menu.update()
//        val font = FortyFive.resourceManager.forceGet<BitmapFont>(this, lifetime, "redwing100")
//        font.data.setScale(0.2f)
//
//        val page = menu.currentPage()
//        val pageText = page.getText(screen)
//        var text = ""
//        text += "* ---${page.name}---\n"
//        text += "* Press alt + d to toggle the debug menu.\n"
//        text += "* Use j/k to change page\n"
//        text += "* page: ${menu.currentPageNumber()}/${menu.amountOfPages()}\n\n"
//        text += pageText
//
//        val layout = GlyphLayout(
//            font,
//            text,
//            Color.WHITE,
//            400f,
//            Align.topLeft,
//            false
//        )
//        val viewport = ExtendViewport(1600f, 900f)
//        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
//        shapeRenderer.begin(ShapeType.Filled)
//        viewport.apply()
//        shapeRenderer.projectionMatrix = viewport.camera.combined
//        Gdx.gl.glEnable(GL20.GL_BLEND);
//        shapeRenderer.setColor(0f, 0f, 0f, 0.8f)
//        shapeRenderer.rect(
//            viewport.worldWidth - 50f - layout.width,
//            880f - layout.height - 50f,
//            layout.width + 100f,
//            layout.height + 100f
//        )
//        shapeRenderer.end()
//        batch.begin()
//        viewport.apply()
//        batch.projectionMatrix = viewport.camera.combined
//        font.draw(batch, layout, viewport.worldWidth - layout.width - 30, 880f)
//        batch.end()
//    }
//
//    private fun renderWithPostProcessors(delta: Float) {
//        val (active, _) = frameBufferManager.getPingPongFrameBuffers("pp") ?: return
//        active.begin()
//        baseRenderable.render(delta)
//        screen.viewport.apply()
//        batch.projectionMatrix = screen.viewport.camera.combined
//        batch.begin()
//        earlyTasks.forEach { it() }
//        batch.end()
//        active.end()
//        batch.begin()
////        if (isOrbAnimActive) renderOrbFbo()
//        postPreprocessingSteps.forEachIndexed { index, step ->
//            frameBufferManager.swapPingPongFrameBuffers("pp")
//            val (@Suppress("NAME_SHADOWING") active, _) = frameBufferManager.getPingPongFrameBuffers("pp") ?: return
//            val last = index == postPreprocessingSteps.size - 1
//            if (!last) active.begin()
//            step()
//            if (!last) active.end()
//        }
//        batch.end()
//        batch.begin()
//        screen.viewport.apply()
//        batch.projectionMatrix = screen.viewport.camera.combined
//        if (isOrbAnimActive) renderOrbFbo()
//        lateTasks.forEach { it() }
//        batch.end()
//    }
//
//    protected fun shaderPostProcessingStep(shaderPromise: Promise<BetterShader>): () -> Unit = lambda@{
//        if (!shaderPromise.isResolved) FortyFive.resourceManager.forceResolve(shaderPromise)
//        val shader = shaderPromise.getOrError()
//        val (_, inactive) = frameBufferManager.getPingPongFrameBuffers("pp") ?: return@lambda
//        batch.flush()
//        batch.shader = shader.shader
//        shader.shader.bind()
//        shader.prepare(screen)
//        batch.enableBlending()
//        batch.draw(
//            inactive.colorBufferTexture,
//            0f, 0f,
//            screen.viewport.worldWidth,
//            screen.viewport.worldHeight,
//            0f, 0f, 1f, 1f // flips the y-axis
//        )
//        batch.flush()
//        batch.shader = null
//    }
//
//    fun addOrbAnimation(orbAnimation: OrbAnimation) {
//        orbAnimations.add(orbAnimation)
//    }
//
//    open fun sizeChanged() {
//        frameBufferManager.sizeChanged()
//    }
//
//    override fun dispose() {
//        hasBeenDisposed = true
//        frameBufferManager.dispose()
//        shapeRenderer.dispose()
//        batch.dispose()
//        polygonBatch.dispose()
//        _lifetime.die()
//    }
//
//
//    class OrbAnimation(
//        val orbTexture: ResourceHandle,
//        val width: Float,
//        val height: Float,
//        val renderPipeline: RenderPipeline,
//        initialPosition: Vector2,
//        initialVelocity: Vector2,
//        val acceleration: Float,
//        val speedCap: Float,
//        val segments: Int,
//        val velocityRampStart: Int,
//        val velocityRamp: Float,
//        val target: () -> Vector2
//    ) {
//
//        val orbTexturePromise: Promise<Drawable> =
//            FortyFive.resourceManager.request(renderPipeline, renderPipeline.lifetime, orbTexture)
//
//        var position: Vector2 = initialPosition
//            private set
//
//        private var velocity: Vector2 = initialVelocity
//
//        private val currentTarget: Vector2 = Vector2(0, 0)
//        private var currentDelta: Float = 0f
//        private var finished: Boolean = false
//
//        private var maxTime: Long = -1
//        private var currentAcceleration: Float = acceleration
//
//        fun newFrame() {
//            if (maxTime == -1L) maxTime = TimeUtils.millis() + velocityRampStart
//            currentTarget.set(target())
//            currentDelta = Gdx.graphics.deltaTime
//            if (TimeUtils.millis() > maxTime) currentAcceleration *= velocityRamp
//        }
//
//        fun update() {
//            val delta = currentDelta / segments
//            val targetDirection = (currentTarget - position).unit
//            val accel = targetDirection.withMag(currentAcceleration)
//            velocity += accel
//            velocity.clamp(-speedCap, speedCap)
//            val scaledVelocity = velocity * delta
//            position += scaledVelocity
//            val dist = (position - currentTarget).len()
//            if (dist <= 50) {
//                finished = true
//            }
//        }
//
//        fun isFinished(): Boolean = finished
//    }
//
//}
//
//class GameRenderPipeline(screen: CustomScreen) : RenderPipeline(screen, screen) {
//
//    private val shootShader: Promise<BetterShader> = FortyFive.resourceManager.request(this, lifetime, "shoot_shader")
//    private val shootPostProcessingStep: () -> Unit by lazy {
//        shaderPostProcessingStep(shootShader)
//    }
//
//    private val parryShader: Promise<BetterShader> = FortyFive.resourceManager.request(this, lifetime, "parry_shader")
//    private val parryPostProcessingStep: () -> Unit by lazy {
//        shaderPostProcessingStep(parryShader)
//    }
//
//    fun getOnShotPostProcessingTimeline(): Timeline = if (FortyFive.globalSave.enableScreenShake) Timeline.timeline {
//        if (!shootShader.isResolved) FortyFive.resourceManager.forceResolve(shootShader)
//        val shootShader = shootShader.getOrError()
//        val duration = 90
//        action {
//            shootShader.resetReferenceTime()
//            postPreprocessingSteps.add(shootPostProcessingStep)
//            screen.inputManager.vibrateController(duration, 0.5f)
//        }
//        delay(duration)
//        action {
//            postPreprocessingSteps.remove(shootPostProcessingStep)
//        }
//    } else Timeline()
//
//    fun startParryEffect() {
//        postPreprocessingSteps.add(parryPostProcessingStep)
//    }
//
//    fun stopParryEffect() {
//        postPreprocessingSteps.remove(parryPostProcessingStep)
//    }
//
//}

class FrameBufferManager : Disposable {

    private val singleBuffers: MutableMap<String, Triple<Float, Pixmap.Format, FrameBuffer?>> = mutableMapOf()
    private val pingPongBuffers: MutableMap<String, PingPongFrameBuffers> = mutableMapOf()

    private data class PingPongFrameBuffers(
        val sizeMultiplier: Float,
        val useViewportSize: Boolean,
        val format: Pixmap.Format,
        var fbo1: FrameBuffer?,
        var fbo2: FrameBuffer?,
        var swapBuffers: Boolean
    )

    private data class BorrowableFrameBuffer(
        val viewportSize: Boolean,
        val frameBuffer: FrameBuffer,
        var borrowed: Boolean
    )

    private val freeFrameBuffers: MutableList<BorrowableFrameBuffer> = mutableListOf()

    fun borrowFrameBuffer(useViewportSize: Boolean, screen: CustomScreen): FrameBuffer? {
        val existing = freeFrameBuffers.find { !it.borrowed && it.viewportSize == useViewportSize }
        if (existing != null) {
            existing.borrowed = true
            return existing.frameBuffer
        }
        val newBuffer = tryCreateFrameBuffer(Pixmap.Format.RGB888, 1f, screen.viewport, useViewportSize)
            ?: return null
        freeFrameBuffers.add(BorrowableFrameBuffer(useViewportSize, newBuffer, true))
        return newBuffer
    }

    fun giveFrameBufferBack(frameBuffer: FrameBuffer) {
        val match = freeFrameBuffers.find { it.frameBuffer === frameBuffer }
        if (match != null) {
            match.borrowed = false
            return
        }
        frameBuffer.dispose()
    }

    fun addNamedFrameBuffer(name: String, format: Pixmap.Format, sizeMultiplier: Float) {
        if (singleBuffers.containsKey(name)) throw RuntimeException("single FrameBuffer with name $name already exists")
        singleBuffers[name] = Triple(sizeMultiplier, format,null)
    }

    fun getNamedFrameBuffer(name: String, screen: CustomScreen): FrameBuffer? {
        val (sizeMultiplier, format, buffer) = singleBuffers[name] ?: throw RuntimeException("no single FrameBuffer with name $name")
        if (buffer != null) return buffer
        val newBuffer = tryCreateFrameBuffer(format, sizeMultiplier, screen.viewport, false)
        singleBuffers[name] = Triple(sizeMultiplier, format, newBuffer)
        return newBuffer
    }

    private fun tryCreateFrameBuffer(
        format: Pixmap.Format,
        sizeMultiplier: Float,
        viewport: Viewport,
        viewportSize: Boolean
    ): FrameBuffer? = try {
        var width: Int
        var height: Int
        if (viewportSize) {
            width = viewport.screenWidth
            height = viewport.screenHeight
        } else {
            width = Gdx.graphics.width
            height = Gdx.graphics.height
        }
        val fbo = FrameBuffer(
            format,
            (width * sizeMultiplier).toInt(),
            (height * sizeMultiplier).toInt(),
            false
        )
        fbo.begin()
        ScreenUtils.clear(0f, 0f, 0f, 0f)
        fbo.end()
        fbo
    } catch (e: java.lang.IllegalStateException) {
        null
    }

    fun addPingPongFrameBuffer(name: String, format: Pixmap.Format, useViewportSize: Boolean, sizeMultiplier: Float) {
        if (pingPongBuffers.containsKey(name)) throw RuntimeException("ping pong FrameBuffer with name $name already exists")
        pingPongBuffers[name] = PingPongFrameBuffers(
            sizeMultiplier,
            useViewportSize,
            format,
            null, null,
            false
        )
    }

    fun getPingPongFrameBuffers(name: String, screen: CustomScreen): Pair<FrameBuffer, FrameBuffer>? {
        val buffers = pingPongBuffers[name] ?: throw RuntimeException("no ping pong FrameBuffer with name $name")
        if (buffers.fbo1 != null) {
            val fbo1 = buffers.fbo1!!
            val fbo2 = buffers.fbo2!!
            return if (buffers.swapBuffers) Pair(fbo2, fbo1) else Pair(fbo1, fbo2)
        }
        val format = buffers.format
        val sizeMultiplier = buffers.sizeMultiplier
        val first = tryCreateFrameBuffer(format, sizeMultiplier, screen.viewport, false) ?: return null
        val second = tryCreateFrameBuffer(format, sizeMultiplier, screen.viewport, false) ?: run {
            first.dispose()
            return null
        }
        buffers.fbo1 = first
        buffers.fbo2 = second
        return first to second
    }

    fun swapPingPongFrameBuffers(name: String) {
        val buffers = pingPongBuffers[name] ?: throw RuntimeException("no ping pong FrameBuffer with name $name")
        buffers.swapBuffers = !buffers.swapBuffers
    }

    fun sizeChanged() {
        singleBuffers.replaceAll { _, (sizeMultiplier, format, buffer) ->
            buffer?.dispose()
            Triple(sizeMultiplier, format, null)
        }
        pingPongBuffers.forEach { (_, buffers) ->
            buffers.fbo1 = null
            buffers.fbo2 = null
        }
        freeFrameBuffers.forEach { value ->
            if (!value.borrowed) value.frameBuffer.dispose()
        }
        freeFrameBuffers.clear()
    }

    override fun dispose() {
        singleBuffers.values.forEach { (_, _, buffer) -> buffer?.dispose() }
        pingPongBuffers.values.forEach { buffers ->
            buffers.fbo1?.dispose()
            buffers.fbo2?.dispose()
        }
    }
}
