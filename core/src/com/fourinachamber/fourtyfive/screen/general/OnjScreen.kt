package com.fourinachamber.fourtyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fourtyfive.game.GraphicsConfig
import com.fourinachamber.fourtyfive.keyInput.KeyInputMap
import com.fourinachamber.fourtyfive.screen.ResourceBorrower
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.utils.Either
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.Utils
import com.fourinachamber.fourtyfive.utils.eitherRight
import kotlin.system.measureTimeMillis


/**
 * a screen that was build from an onj file.
 */
open class OnjScreen(
    private val drawables: MutableMap<String, Drawable>,
    val viewport: Viewport,
    batch: Batch,
    private val background: String?,
//    private val toDispose: List<Disposable>,
    private val useAssets: List<String>,
    private val earlyRenderTasks: List<OnjScreen.() -> Unit>,
    private val lateRenderTasks: List<OnjScreen.() -> Unit>,
    private val namedCells: Map<String, Cell<*>>,
    private val namedActors: Map<String, Actor>,
    private val printFrameRate: Boolean,
    private val keyInputMap: KeyInputMap? = null
) : ScreenAdapter(), ResourceBorrower {

    var dragAndDrop: Map<String, DragAndDrop> = mapOf()

    var lastRenderTime: Long = 0
        private set

    private val createTime: Long = TimeUtils.millis()
    private val callbacks: MutableList<Pair<Long, () -> Unit>> = mutableListOf()
    private val additionalDisposables: MutableList<Disposable> = mutableListOf()

    private val additionalLateRenderTasks: MutableList<(Batch) -> Unit> = mutableListOf()
    private val additionalEarlyRenderTasks: MutableList<(Batch) -> Unit> = mutableListOf()

    private var isVisible: Boolean = false

    var defaultCursor: Either<Cursor, Cursor.SystemCursor> = Cursor.SystemCursor.Arrow.eitherRight()
        set(value) {
            field = value
            Utils.setCursor(value)
        }

    var postProcessor: PostProcessor? = null
        set(value) {
            field = value
            value?.resetReferenceTime()
        }

    val stage: Stage = Stage(viewport, batch)

    var screenController: ScreenController? = null
        set(value) {
            field?.end()
            field = value
            if (isVisible) value?.init(this)
        }

    var highlightArea: Rectangle? = null

    private val keySelectDrawable: Drawable by lazy {
        GraphicsConfig.keySelectDrawable(this)
    }

    private val backgroundDrawable: Drawable? by lazy {
        background?.let { ResourceManager.get<Drawable>(this, it) }
    }

    init {
        useAssets.forEach {
            ResourceManager.borrow(this, it)
        }
        addEarlyRenderTask {
            val drawable = backgroundDrawable ?: return@addEarlyRenderTask
            drawable.draw(it, 0f, 0f, stage.viewport.worldWidth, stage.viewport.worldHeight)
        }
        addLateRenderTask {
            val highlight = highlightArea ?: return@addLateRenderTask
            keySelectDrawable.draw(it, highlight.x, highlight.y, highlight.width, highlight.height)
        }
    }

    fun afterMs(ms: Int, callback: () -> Unit) {
        callbacks.add((TimeUtils.millis() + ms) to callback)
    }

    fun addDrawable(name: String, drawable: Drawable) {
        drawables[name] = drawable
    }

    fun addDisposable(disposable: Disposable) {
        additionalDisposables.add(disposable)
    }

    fun addActorToRoot(actor: Actor) {
        stage.root.addActor(actor)
    }

    fun invalidateEverything() {

        fun invalidateGroup(group: Group) {
            for (child in group.children) {
                if (child is Layout) {
                    child.invalidate()
                }
                if (child is Group) invalidateGroup(child)
            }
        }

        invalidateGroup(stage.root)
    }

    fun removeActorFromRoot(actor: Actor) {
        stage.root.removeActor(actor)
    }

    fun resortRootZIndices() {
        stage.root.children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    fun addLateRenderTask(task: (Batch) -> Unit): Unit = run { additionalLateRenderTasks.add(task) }
    fun addEarlyRenderTask(task: (Batch) -> Unit): Unit = run { additionalEarlyRenderTasks.add(task) }
    fun removeLateRenderTask(task: (Batch) -> Unit): Unit = run { additionalLateRenderTasks.remove(task) }
    fun removeEarlyRenderTask(task: (Batch) -> Unit): Unit = run { additionalEarlyRenderTasks.remove(task) }

//    fun drawableOrError(name: String): Drawable {
//        return ResourceManager.get(this, name)
//    }
//
//    fun fontOrError(name: String): BitmapFont {
//        return ResourceManager.get(this, name)
//    }
//
//    fun postProcessorOrError(name: String): PostProcessor = postProcessors[name] ?: throw RuntimeException(
//        "no post processor named $name"
//    )
//
//    fun particleOrError(name: String): ParticleEffect = particles[name] ?: throw RuntimeException(
//        "no particle named $name"
//    )
//
//    fun cursorOrError(name: String): Cursor = cursors[name] ?: throw RuntimeException(
//        "no cursor named $name"
//    )

    fun namedActorOrError(name: String): Actor = namedActors[name] ?: throw RuntimeException(
        "no actor named $name"
    )

    fun namedCellOrError(name: String): Cell<*> = namedCells[name] ?: throw RuntimeException(
        "no cell named $name"
    )

    private fun updateCallbacks() {
        val curTime = TimeUtils.millis()
        val iterator = callbacks.iterator()
        while (iterator.hasNext()) {
            val (time, callback) = iterator.next()
            if (time <= curTime) {
                callback()
                iterator.remove()
            }
        }
    }

    override fun show() {
        screenController?.init(this)
        val multiplexer = InputMultiplexer()
//        val inputMap = KeyInputMap(listOf(
//            KeyInputMapEntry(Keys.F, listOf()) {
//                if (!Gdx.graphics.isFullscreen) {
//                    Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
//                } else {
//                    Gdx.graphics.setWindowedMode(600, 400)
//                }
//                return@KeyInputMapEntry true
//            }
//        ))
        keyInputMap?.let { multiplexer.addProcessor(it) }
        multiplexer.addProcessor(stage)
        Gdx.input.inputProcessor = multiplexer
        Utils.setCursor(defaultCursor)
        isVisible = true
    }

    override fun hide() {
        super.hide()
        screenController?.end()
        isVisible = false
    }

    override fun render(delta: Float) = try {
        if (printFrameRate) FourtyFiveLogger.fps()
        screenController?.update()
        updateCallbacks()
        lastRenderTime = measureTimeMillis {
            stage.act(Gdx.graphics.deltaTime)
            if (postProcessor == null) {
                ScreenUtils.clear(0.0f, 0.0f, 0.0f, 1.0f)
                if (stage.batch.isDrawing) stage.batch.end()
                doRenderTasks(earlyRenderTasks, additionalEarlyRenderTasks)
                stage.draw()
                doRenderTasks(lateRenderTasks, additionalLateRenderTasks)
            } else {
                renderWithPostProcessing()
            }
        }
    } catch (e: Exception) {
        FourtyFiveLogger.severe(logTag, "exception in render function")
        FourtyFiveLogger.stackTrace(e)
    }

    private fun doRenderTasks(tasks: List<OnjScreen.() -> Unit>, additionalTasks: MutableList<(Batch) -> Unit>) {
        stage.batch.begin()
        tasks.forEach { it(this) }
        additionalTasks.forEach { it(stage.batch) }
        stage.batch.end()
    }

    private fun renderWithPostProcessing() {

        val fbo = try {
            FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.width, Gdx.graphics.height, false)
        } catch (e: java.lang.IllegalStateException) {
            // construction of FrameBuffer sometimes fails when the window is minimized
            return
        }

        fbo.begin()
        ScreenUtils.clear(0.0f, 0.0f, 0.0f, 1.0f)
        viewport.apply()
        doRenderTasks(earlyRenderTasks, additionalEarlyRenderTasks)
        stage.draw()
        doRenderTasks(lateRenderTasks, additionalLateRenderTasks)
        fbo.end()

        val batch = SpriteBatch()

        val postProcessor = postProcessor!!

        batch.shader = postProcessor.shader
        postProcessor.shader.bind()

        postProcessor.shader.setUniformMatrix("u_projTrans", viewport.camera.combined)

        postProcessor.bindUniforms()
        postProcessor.bindArgUniforms()

        batch.begin()
        ScreenUtils.clear(0.0f, 0.0f, 0.0f, 1.0f)
        batch.enableBlending()
        batch.draw(
            fbo.colorBufferTexture,
            0f, 0f,
            Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat(),
            0f, 0f, 1f, 1f // flips the y-axis
        )
        batch.end()

        fbo.dispose()
        batch.dispose()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        screenController?.end()
        stage.dispose()
//        toDispose.forEach(Disposable::dispose)
        useAssets.forEach {
            ResourceManager.giveBack(this, it)
        }
        additionalDisposables.forEach(Disposable::dispose)
    }

    companion object {
        const val logTag = "screen"
    }

}
