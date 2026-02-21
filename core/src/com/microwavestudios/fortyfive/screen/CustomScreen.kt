package com.microwavestudios.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.particle.ParticleSystem
import com.microwavestudios.fortyfive.rendering.*
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenBuilder
import com.microwavestudios.fortyfive.utils.*

// TODO: this is one of the oldest classes in the game an contains a lot of features that have
// been replaced by better ones. These features have been marked as Deprecated and need to be removed
// eventually
/**
 * represents a screen of the game that performs important management tasks like
 * drawing and updating the actors, drawing
 * [DetailWidgets][com.microwavestudios.fortyfive.screen.commonComponents.DetailWidget],
 * drawing dragged actors, distributing input events, etc.
 * Also contains some utility functions, e.g. for callbacks.
 *
 * To create a screen, typically
 * [FromKotlinScreenBuilder][com.microwavestudios.fortyfive.screen.screenBuilder.FromKotlinScreenBuilder]
 * is used together with [ScreenCreator][com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator].
 * What screen is shown is managed by the [ScreenManager]
 */
open class CustomScreen(
    val viewport: Viewport,
    batch: Batch,
    private val controllerContext: Any?,
    private val earlyRenderTasks: List<CustomScreen.() -> Unit>,
    private val lateRenderTasks: List<CustomScreen.() -> Unit>,
    private val namedActors: MutableMap<String, Actor>,
    val transitions: Map<String, ScreenManager.ScreenTransition>,
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
    /**
     * stores a collection of global screen states, to handle things like popup. Old feature,
     * using events is typically more convenient
     */
    @Deprecated("Use events instead")
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
    /**
     * lives as long as the screen is shown
     */
    val lifetime: Lifetime
        get() = _lifetime

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    var background: ResourceHandle? by backgroundHandleObserver

    private val backgroundDrawable: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, lifetime, arrayOf())

    private val actorsWithActiveHoverDetails: MutableList<InputActor> = mutableListOf()

    var debugMenu: DebugMenu? = null

    var mouseDraggedActor: InputActor? = null

    val inputManager = InputManager(this)
    private var inputMultiplexer: InputMultiplexer = InputMultiplexer()

    val textEffectParticleSystem: ParticleSystem = ParticleSystem(viewport.worldWidth, viewport.worldHeight).also {
        it.addBaseForce(0f, -1f)
    }

    /**
     * used to distribute events across the actor hierarchy, controllers, etc.
     */
    val events: EventPipeline = EventPipeline()

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

    /**
     * runs [callback] after [ms] milliseconds have passed
     */
    fun afterMs(ms: Int, callback: () -> Unit) {
        callbackAddBuffer.add((TimeUtils.millis() + ms) to callback)
    }

    @Deprecated("use lifetime.tieDisposable() instead")
    fun addDisposable(disposable: Disposable) {
        additionalDisposables.add(disposable)
    }

    @Deprecated("Was typically used for things like animations, but there is almost always a better solution")
    fun addActorToRoot(actor: Actor) {
        stage.root.addActor(actor)
    }

    fun removeActorFromRoot(actor: Actor) {
        stage.root.removeActor(actor)
    }

    @Deprecated("use events instead")
    fun enterState(state: String) {
        if (state in _screenState) return
        _screenState.add(state)
        screenStateChangeListeners.forEach { it(true, state) }
    }

    @Deprecated("use events instead")
    fun leaveState(state: String) {
        if (state !in _screenState) return
        _screenState.remove(state)
        screenStateChangeListeners.forEach { it(false, state) }
    }

    @Deprecated("use events instead")
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
        val detailActor = detailWidget.generateDetailActor(addFadeInAction = true) ?: return
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
        isVisible = true
        screenControllers.forEach { it.onShow() }
    }

    fun active() {
        Gdx.input.inputProcessor = inputMultiplexer
        Utils.setCursor(defaultCursor)
        screenControllers.forEach { it.onActive() }
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

    override fun render(delta: Float) = try {
        if (makeLaggy) Thread.sleep(500)
        inputManager.update()
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
            dragged.drawInDrag(batch, oX, oY)
            actor.x = oX
            actor.y = oY
        }
        textEffectParticleSystem.update()
        textEffectParticleSystem.render(batch, this)
        batch.end()
        doRenderTasks(lateRenderTasks, additionalLateRenderTasks)
    } catch (e: Exception) {
        FortyFive.logger.fatal(e)
    }

    private fun doRenderTasks(tasks: List<CustomScreen.() -> Unit>, additionalTasks: MutableList<(Batch) -> Unit>) {
        stage.batch.begin()
        tasks.forEach { it(this) }
        additionalTasks.forEach { it(stage.batch) }
        stage.batch.end()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        screenEvents.fire(ScreenResizedEvent(width, height))
    }

    override fun dispose() {
        hide()
        inputManager.end()
        screenControllers.forEach(ScreenController::end)
        stage.dispose()
        additionalDisposables.forEach(Disposable::dispose)
        _lifetime.die()
    }

    companion object {

        const val logTag = "screen"

        const val transitionAwayScreenState = "transition away"
    }

    data class ScreenResizedEvent(val width: Int, val height: Int)
}
