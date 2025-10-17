package com.microwavestudios.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.UserPrefs
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.rendering.*
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenBuilder
import com.microwavestudios.fortyfive.utils.*

/**
 * a screen that was build from an onj file.
 */
open class OnjScreen(
    val viewport: Viewport,
    batch: Batch,
    private val controllerContext: Any?,
    private val earlyRenderTasks: List<OnjScreen.() -> Unit>,
    private val lateRenderTasks: List<OnjScreen.() -> Unit>,
    private val namedActors: MutableMap<String, Actor>,
    val transitionAwayTimes: Map<String, Int>,
    val screenBuilder: ScreenBuilder,
    val music: ResourceHandle?,
    val playAmbientSounds: Boolean
) : ScreenAdapter(), Renderable, ResourceBorrower {

    private val callbacks: MutableList<Pair<Long, () -> Unit>> = mutableListOf()
    private val callbackAddBuffer: MutableList<Pair<Long, () -> Unit>> = mutableListOf()
    private val additionalDisposables: MutableList<Disposable> = mutableListOf()

    private val additionalLateRenderTasks: MutableList<(Batch) -> Unit> = mutableListOf()
    private val additionalEarlyRenderTasks: MutableList<(Batch) -> Unit> = mutableListOf()

    val screenEvents: EventPipeline = EventPipeline()

    var isVisible: Boolean = false
        private set

    var defaultCursor: Either<Cursor, Cursor.SystemCursor> = Cursor.SystemCursor.Arrow.eitherRight()
        set(value) {
            field = value
            Utils.setCursor(value)
        }

    private val _screenState: MutableSet<String> = mutableSetOf()
    val screenState: Set<String>
        get() = _screenState

    private val screenStateChangeListeners: MutableList<(entered: Boolean, state: String) -> Unit> = mutableListOf()

    val stage: Stage = Stage(viewport, batch)

    private val _screenControllers: MutableList<ScreenController> = mutableListOf()

    val screenControllers: List<ScreenController>
        get() = _screenControllers

    private val makeLaggy: Boolean
        get() = findDebugMenuPage<ScreenDebugMenuPage>()?.makeLaggy?.getValue(this, this::makeLaggy) ?: false

    private val _lifetime: EndableLifetime = EndableLifetime()
    val lifetime: Lifetime
        get() = _lifetime

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    var background: String? by backgroundHandleObserver

    private val backgroundDrawable: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, lifetime, arrayOf())

    private val actorsWithActiveHoverDetails: MutableList<InputActor> = mutableListOf()

    var debugMenu: DebugMenu? = null

    var mouseDraggedActor: InputActor? = null

    val inputManager = InputManager(this)
    private var inputMultiplexer: InputMultiplexer = InputMultiplexer()

    init {
        addEarlyRenderTask {
            val drawable = backgroundDrawable ?: return@addEarlyRenderTask
            drawable.draw(it, 0f, 0f, stage.viewport.worldWidth, stage.viewport.worldHeight)
        }
        inputMultiplexer.addProcessor(inputManager)
        inputMultiplexer.addProcessor(stage)
        inputManager.onInput(GameInputs.toggleDebugMenu) { debugMenu?.toggle() }
        inputManager.onInput(GameInputs.nextDebugMenuPage) { debugMenu?.nextDebugPage() }
        inputManager.onInput(GameInputs.previousDebugMenuPage) { debugMenu?.previousDebugPage() }
    }

    fun addScreenController(controller: ScreenController) {
        _screenControllers.add(controller)
        controller.injectActors(this)
        controller.init(controllerContext)
        if (isVisible) controller.onShow()
    }

    inline fun <reified T : DebugMenuPage> findDebugMenuPage(): T? = debugMenu?.findPage<T>()

    inline fun <reified T : ScreenController> findController(): T? = screenControllers.find { it is T } as T?

    fun afterMs(ms: Int, callback: () -> Unit) {
        callbackAddBuffer.add((TimeUtils.millis() + ms) to callback)
    }

    fun addDisposable(disposable: Disposable) {
        additionalDisposables.add(disposable)
    }

    fun addActorToRoot(actor: Actor) {
        stage.root.addActor(actor)
    }

    fun removeActorFromRoot(actor: Actor) {
        stage.root.removeActor(actor)
    }

    fun enterState(state: String) {
        if (state in _screenState) return
        _screenState.add(state)
        screenStateChangeListeners.forEach { it(true, state) }
    }

    fun leaveState(state: String) {
        if (state !in _screenState) return
        _screenState.remove(state)
        screenStateChangeListeners.forEach { it(false, state) }
    }

    fun addOnScreenStateChangedListener(listener: (entered: Boolean, state: String) -> Unit) {
        screenStateChangeListeners.add(listener)
    }

    fun addLateRenderTask(task: (Batch) -> Unit): Unit = run { additionalLateRenderTasks.add(task) }

    fun addEarlyRenderTask(task: (Batch) -> Unit): Unit = run { additionalEarlyRenderTasks.add(task) }

    fun removeLateRenderTask(task: (Batch) -> Unit) {
        additionalLateRenderTasks.remove(task)
    }

    fun addNamedActor(name: String, actor: Actor) {
        namedActors[name] = actor
        actor.name = name
    }

    fun showHoverDetail(actor: InputActor) {
        val detailWidget = actor.detailWidget ?: return
        if (detailWidget.isShown) return
        val detailActor = detailWidget.generateDetailActor(addFadeInAction = true)
        detailWidget.detailActor = detailActor
        detailWidget.updateBounds(actor.actor)
        actorsWithActiveHoverDetails.add(actor)
    }

    fun hideHoverDetail(sourceActor: InputActor) {
        sourceActor.detailWidget?.hide()
        actorsWithActiveHoverDetails.remove(sourceActor)
    }

    fun namedActorOrError(name: String): Actor = namedActors[name] ?: throw RuntimeException(
        "no actor named $name"
    )

    fun namedActorOrNull(name: String): Actor? = namedActors[name]

    private fun updateCallbacks() {
        val curTime = TimeUtils.millis()
        callbacks.addAll(callbackAddBuffer)
        callbackAddBuffer.clear()
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
        Gdx.input.inputProcessor = inputMultiplexer
        Utils.setCursor(defaultCursor)
        isVisible = true
        screenControllers.forEach { it.onShow() }
    }

    fun transitionAway() {
        inputMultiplexer.clear()
        enterState(transitionAwayScreenState)
        _screenControllers.forEach(ScreenController::onTransitionAway)
    }

    override fun hide() {
        super.hide()
        isVisible = false
    }

    fun update(delta: Float, isEarly: Boolean = false) {
        FortyFive.soundPlayer.update(this, playAmbientSounds)
        if (!isEarly) screenControllers.forEach(ScreenController::update)
        updateCallbacks()
        stage.act(Gdx.graphics.deltaTime)
        actorsWithActiveHoverDetails.forEach {
            it.detailWidget?.detailActor?.act(Gdx.graphics.deltaTime)
            it.detailWidget?.updateBounds(it.actor)
        }
    }

    fun centeredStageCoordsOfActor(name: String): Vector2 = namedActorOrError(name).let { actor ->
        actor.localToStageCoordinates(Vector2(actor.width / 2, actor.height / 2))
    }

    override fun render(delta: Float) = try {
        if (makeLaggy) Thread.sleep(500)
        val batch = stage.batch
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        if (batch.isDrawing) batch.end()
        stage.viewport.apply()
        doRenderTasks(earlyRenderTasks, additionalEarlyRenderTasks)
        stage.draw()
        batch.begin()
        actorsWithActiveHoverDetails.forEach {
            it.detailWidget?.drawDetailActor(batch)
        }
        mouseDraggedActor?.let { dragged ->
            val actor = dragged.actor
            val oX = actor.x
            val oY = actor.y
            actor.x = dragged.dragX
            actor.y = dragged.dragY
            dragged.drawInDrag(batch)
            actor.x = oX
            actor.y = oY
        }
        batch.end()
        doRenderTasks(lateRenderTasks, additionalLateRenderTasks)
    } catch (e: Exception) {
        FortyFive.logger.fatal(e)
    }

    private fun doRenderTasks(tasks: List<OnjScreen.() -> Unit>, additionalTasks: MutableList<(Batch) -> Unit>) {
        stage.batch.begin()
        tasks.forEach { it(this) }
        additionalTasks.forEach { it(stage.batch) }
        stage.batch.end()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        println("hi")
        screenEvents.fire(ScreenResizedEvent(width, height))
    }

    fun confirmationClickTimelineAction(maxTime: Long? = null): Timeline.TimelineAction = TODO()
//        object : Timeline.TimelineAction() {
//
//            private var finishAt: Long? = null
//
//            override fun start(timeline: Timeline) {
//                super.start(timeline)
//                maxTime?.let {
//                    finishAt = TimeUtils.millis() + it
//                }
//                awaitingConfirmationClick = true
//            }
//
//            override fun isFinished(timeline: Timeline): Boolean {
//                if (!awaitingConfirmationClick) return true
//                finishAt?.let {
//                    if (TimeUtils.millis() >= it) return true
//                }
//                return false
//            }
//
//            override fun end(timeline: Timeline) {
//                awaitingConfirmationClick = false
//            }
//        }

    override fun dispose() {
        hide()
        screenControllers.forEach(ScreenController::end)
        stage.dispose()
        additionalDisposables.forEach(Disposable::dispose)
        _lifetime.die()
    }


    companion object {

        const val logTag = "screen"

        const val transitionAwayScreenState = "transition away"

        fun toggleFullScreen(forceFullscreen: Boolean = false) {
            if (UserPrefs.windowMode == UserPrefs.WindowMode.Window || forceFullscreen) {
                UserPrefs.windowMode =
                    if (UserPrefs.lastFullScreenAsBorderless)
                        UserPrefs.WindowMode.BorderlessWindow
                    else
                        UserPrefs.WindowMode.Fullscreen
            } else {
                UserPrefs.windowMode = UserPrefs.WindowMode.Window
            }
        }
    }

    data class ScreenResizedEvent(val width: Int, val height: Int)
}
