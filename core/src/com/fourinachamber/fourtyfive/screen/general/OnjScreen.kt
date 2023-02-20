package com.fourinachamber.fourtyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.glutils.FrameBuffer
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
import com.fourinachamber.fourtyfive.keyInput.KeySelectionHierarchyBuilder
import com.fourinachamber.fourtyfive.keyInput.KeySelectionHierarchyNode
import com.fourinachamber.fourtyfive.screen.ResourceBorrower
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.utils.*
import kotlin.system.measureTimeMillis


/**
 * a screen that was build from an onj file.
 */
open class OnjScreen @MainThreadOnly constructor(
    val viewport: Viewport,
    batch: Batch,
    private val background: String?,
    private val useAssets: List<String>,
    private val earlyRenderTasks: List<OnjScreen.() -> Unit>,
    private val lateRenderTasks: List<OnjScreen.() -> Unit>,
    private val namedCells: Map<String, Cell<*>>,
    private val namedActors: Map<String, Actor>,
    private val printFrameRate: Boolean
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
        @MainThreadOnly set(value) {
            field = value
            Utils.setCursor(value)
        }

    var postProcessor: PostProcessor? = null
        @MainThreadOnly set(value) {
            field = value
            value?.resetReferenceTime()
            resize(Gdx.graphics.width, Gdx.graphics.height)
        }

    val stage: Stage = Stage(viewport, batch)

    var screenController: ScreenController? = null
        @MainThreadOnly set(value) {
            field?.end()
            field = value
            if (isVisible) value?.init(this)
        }

    var selectedActor: KeySelectableActor? = null
        @AllThreadsAllowed set(value) {
            field?.isSelected = false
            field = value
            field?.isSelected = true
            selectedNode = null
        }

    var selectedNode: KeySelectionHierarchyNode? = null
        @AllThreadsAllowed set(value) {
            if (!(value?.isSelectable ?: true)) {
                throw RuntimeException("only a node that is selectable can be assigned to 'selectedNode'")
            }
            value?.actor?.let { selectedActor = it as KeySelectableActor }
            field = value
        }

    var inputMap: KeyInputMap? = null
        @AllThreadsAllowed set(value) {
            field = value
            inputMultiplexer.clear()
            value?.let { inputMultiplexer.addProcessor(it) }
            inputMultiplexer.addProcessor(stage)
        }

    private val keySelectDrawable: Drawable by lazy {
        GraphicsConfig.keySelectDrawable(this)
    }

    private val backgroundDrawable: Drawable? by lazy {
        background?.let { ResourceManager.get<Drawable>(this, it) }
    }

    private var inputMultiplexer: InputMultiplexer = InputMultiplexer()

    var keySelectionHierarchy: KeySelectionHierarchyNode? = null
        private set

    init {
        useAssets.forEach {
            ResourceManager.borrow(this, it)
        }
        addEarlyRenderTask {
            val drawable = backgroundDrawable ?: return@addEarlyRenderTask
            drawable.draw(it, 0f, 0f, stage.viewport.worldWidth, stage.viewport.worldHeight)
        }
        addLateRenderTask {
            val highlight = selectedActor?.getHighlightArea() ?: return@addLateRenderTask
            keySelectDrawable.draw(it, highlight.x, highlight.y, highlight.width, highlight.height)
        }
        inputMultiplexer.addProcessor(stage)
    }

    @AllThreadsAllowed
    fun afterMs(ms: Int, callback: @MainThreadOnly () -> Unit) {
        callbacks.add((TimeUtils.millis() + ms) to callback)
    }

    @AllThreadsAllowed
    fun addDisposable(disposable: Disposable) {
        additionalDisposables.add(disposable)
    }

    @AllThreadsAllowed
    fun addActorToRoot(actor: Actor) {
        stage.root.addActor(actor)
    }

    @AllThreadsAllowed
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

    @AllThreadsAllowed
    fun removeActorFromRoot(actor: Actor) {
        stage.root.removeActor(actor)
    }

    @AllThreadsAllowed
    fun resortRootZIndices() {
        stage.root.children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    @AllThreadsAllowed
    fun buildKeySelectHierarchy() {
        keySelectionHierarchy = KeySelectionHierarchyBuilder().build(stage.root)
    }

    @AllThreadsAllowed
    fun addLateRenderTask(task: @MainThreadOnly (Batch) -> Unit): Unit = run { additionalLateRenderTasks.add(task) }

    @AllThreadsAllowed
    fun addEarlyRenderTask(task: @MainThreadOnly (Batch) -> Unit): Unit = run { additionalEarlyRenderTasks.add(task) }

    @AllThreadsAllowed
    fun removeLateRenderTask(task: @MainThreadOnly (Batch) -> Unit) {
        additionalLateRenderTasks.remove(task)
    }

    @AllThreadsAllowed
    fun removeEarlyRenderTask(task: @MainThreadOnly (Batch) -> Unit) {
        additionalEarlyRenderTasks.remove(task)
    }

    @AllThreadsAllowed
    fun namedActorOrError(name: String): Actor = namedActors[name] ?: throw RuntimeException(
        "no actor named $name"
    )

    @AllThreadsAllowed
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

    @MainThreadOnly
    override fun show() {
        screenController?.init(this)
        Gdx.input.inputProcessor = inputMultiplexer
        Utils.setCursor(defaultCursor)
        isVisible = true
    }

    @MainThreadOnly
    override fun hide() {
        super.hide()
        screenController?.end()
        isVisible = false
    }

    @MainThreadOnly
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

    @MainThreadOnly
    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    @MainThreadOnly
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
