package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.game.UserPrefs
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.rendering.Renderable
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenBuilder
import com.fourinachamber.fortyfive.utils.*
import dev.lyze.flexbox.FlexBox
import ktx.actors.onEnter
import ktx.actors.onExit


/**
 * a screen that was build from an onj file.
 */
open class OnjScreen(
    val viewport: Viewport,
    batch: Batch,
    private val controllerContext: Any?,
    private val earlyRenderTasks: List<OnjScreen.() -> Unit>,
    private val lateRenderTasks: List<OnjScreen.() -> Unit>,
    styleManagers: List<StyleManager>,
    private val namedActors: MutableMap<String, Actor>,
    val printFrameRate: Boolean,
    val transitionAwayTimes: Map<String, Int>,
    val screenBuilder: ScreenBuilder,
    val music: ResourceHandle?,
    val playAmbientSounds: Boolean
) : ScreenAdapter(), Renderable, Lifetime, ResourceBorrower {

    var styleManagers: MutableList<StyleManager> = styleManagers.toMutableList()
        private set

    var _dragAndDrop: MutableMap<String, DragAndDrop> = mutableMapOf()
    val dragAndDrop: Map<String, DragAndDrop> get() = _dragAndDrop.toMap()

    private val createTime: Long = TimeUtils.millis()
    private val callbacks: MutableList<Pair<Long, () -> Unit>> = mutableListOf()
    private val callbackAddBuffer: MutableList<Pair<Long, () -> Unit>> = mutableListOf()
    private val additionalDisposables: MutableList<Disposable> = mutableListOf()

    private val additionalLateRenderTasks: MutableList<(Batch) -> Unit> = mutableListOf()
    private val additionalEarlyRenderTasks: MutableList<(Batch) -> Unit> = mutableListOf()

    var isVisible: Boolean = false
        private set

    var defaultCursor: Either<Cursor, Cursor.SystemCursor> = Cursor.SystemCursor.Arrow.eitherRight()
        @MainThreadOnly set(value) {
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

    private val _selectedActors: MutableSet<Actor> = mutableSetOf()

    val selectedActors: List<Actor> get() = _selectedActors.toList()

    fun changeSelectionFor(actor: Actor, fromMouse: Boolean = true) {
        if (actor in _selectedActors) deselectActor(actor)
        else selectActor(actor, fromMouse)
    }

    private fun selectActor(actor: Actor, fromMouse: Boolean = true) {
        val oldList = _selectedActors.toList()
        if (selectionHierarchy.isEmpty() || !curSelectionParent.hasActor(actor)) return
        if (actor is FocusableActor && !actor.isSelectable) return
        if (_selectedActors.add(actor)) {
            val newList =
                _selectedActors.toList() // reversed, so that deselectAllExcept makes sense to use on the newest element
            newList.reversed().filterNotNull()
                .forEach { it.fire(SelectChangeEvent(oldList, _selectedActors.toMutableList().toList(), fromMouse)) }
            curSelectionParent.onSelection(newList)
        }
    }

    private fun deselectActor(actor: Actor) {
        val oldList = _selectedActors.toList()
        if (_selectedActors.remove(actor))
            oldList.reversed()
                .forEach { it.fire(SelectChangeEvent(oldList, _selectedActors.toMutableList().toList())) }
    }

    fun deselectAllExcept(actor: Actor? = null) {
        val oldList = _selectedActors.toList()
        _selectedActors.removeIf { it != actor }
        if (oldList.size != _selectedActors.size)
            oldList.reversed()
                .forEach { it.fire(SelectChangeEvent(oldList, _selectedActors.toMutableList().toList())) }
    }

    var focusedActor: Actor? = null
        set(value) {
            if (value == null) {
                field?.let { it.fire(FocusChangeEvent(it, null)) }
            } else {
                if (selectionHierarchy.isEmpty() || !curSelectionParent.hasActor(value)) return
                field?.let { it.fire(FocusChangeEvent(it, value)) }
                value.let { it.fire(FocusChangeEvent(field, it)) }
            }
            field = value
        }
    private val selectionHierarchy: ArrayDeque<FocusableParent> = ArrayDeque()
    val curSelectionParent: FocusableParent get() = selectionHierarchy.last()

    var draggedPreviewActor: Actor? = null //current possibilty for dragAndDrop
    val draggedActor: Actor? get() = dragAndDrop.values.firstNotNullOfOrNull { it.dragActor } ?: draggedPreviewActor

    fun addToSelectionHierarchy(child: FocusableParent) {
        selectionHierarchy.add(child)
        curSelectionParent.updateFocusableActors(this)
    }

    fun escapeSelectionHierarchy(fromMouse: Boolean = true) {
        if (!fromMouse && draggedActor != null) return
        focusedActor = selectedActors.lastOrNull()
        deselectAllExcept()
        if (selectionHierarchy.size >= 2) { //there has to be always at least one selectionGroup for it to work
            val s = selectionHierarchy.removeLast()
            s.onLeave()
            curSelectionParent.updateFocusableActors(this)
        }
    }

    fun getFocusableActors(): MutableList<FocusableActor> {
        return getFocusableActors(stage.root)
    }

    private fun getFocusableActors(root: Group): MutableList<FocusableActor> {
        val selectableActors = mutableListOf<FocusableActor>()
        for (child in root.children) {
            if (child is FocusableActor) {
                if (child.group != null) selectableActors.add(child)
            }
            if (child is Group) selectableActors.addAll(getFocusableActors(child))
        }
        return selectableActors
    }

    fun focusNext(direction: Vector2? = null) {
        if (selectionHierarchy.isEmpty()) return
        val focusableElement = curSelectionParent.focusNext(direction, this)
        this.focusedActor = focusableElement as Actor
    }

    fun focusPrevious() {
        if (selectionHierarchy.isEmpty()) return
        val focusableElement = curSelectionParent.focusPrevious(this)
        this.focusedActor = focusableElement as Actor
    }

    private var awaitingConfirmationClick: Boolean = false

    private var screenInputProcessor: InputProcessor = object : InputAdapter() {

        override fun keyDown(keycode: Int): Boolean {
            if (!awaitingConfirmationClick) return false
            if (keycode in arrayOf(Keys.ENTER, Keys.NUMPAD_ENTER)) {
                awaitingConfirmationClick = false
                return true
            }
            return false
        }

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (!awaitingConfirmationClick) return false
            awaitingConfirmationClick = false
            return true
        }

    }

    var inputMap: KeyInputMap? = null
        @AllThreadsAllowed set(value) {
            field = value
            inputMultiplexer.clear()
            inputMultiplexer.addProcessor(screenInputProcessor)
            value?.let { inputMultiplexer.addProcessor(it) }
            inputMultiplexer.addProcessor(stage)
        }

    private val lifetime: EndableLifetime = EndableLifetime()

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    var background: String? by backgroundHandleObserver

    private val backgroundDrawable: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, this)

    private var inputMultiplexer: InputMultiplexer = InputMultiplexer()


    var currentHoverDetail: Actor? = null
        private set

    var currentDisplayDetailActor: DisplayDetailsOnHoverActor? = null
        private set

    init {
        addEarlyRenderTask {
            val drawable = backgroundDrawable ?: return@addEarlyRenderTask
            drawable.draw(it, 0f, 0f, stage.viewport.worldWidth, stage.viewport.worldHeight)
        }
        inputMultiplexer.addProcessor(screenInputProcessor)
        inputMultiplexer.addProcessor(stage)
    }

    fun addScreenController(controller: ScreenController) {
        _screenControllers.add(controller)
        controller.injectActors(this)
        controller.init(controllerContext)
        if (isVisible) controller.onShow()
    }

    inline fun <reified T : ScreenController> findController(): T? = screenControllers.find { it is T } as T?

    override fun onEnd(callback: () -> Unit) {
        lifetime.onEnd(callback)
    }

    @AllThreadsAllowed
    fun afterMs(ms: Int, callback: @MainThreadOnly () -> Unit) {
        callbackAddBuffer.add((TimeUtils.millis() + ms) to callback)
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

//    fun borrowResource(handle: ResourceHandle) {
//        useAssets.add(handle)
//        ResourceManager.borrow(this, handle)
//    }

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
    fun enterState(state: String) {
        if (state in _screenState) return
        _screenState.add(state)
        screenStateChangeListeners.forEach { it(true, state) }
    }

    @AllThreadsAllowed
    fun leaveState(state: String) {
        if (state !in _screenState) return
        _screenState.remove(state)
        screenStateChangeListeners.forEach { it(false, state) }
    }

    fun addOnScreenStateChangedListener(listener: (entered: Boolean, state: String) -> Unit) {
        screenStateChangeListeners.add(listener)
    }

    inline fun listenToScreenState(listenToState: String, crossinline listener: (entered: Boolean) -> Unit) {
        addOnScreenStateChangedListener { entered, state ->
            if (state != listenToState) return@addOnScreenStateChangedListener
            listener(entered)
        }
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

    fun addNamedActor(name: String, actor: Actor) {
        namedActors[name] = actor
    }

    fun removeNamedActor(name: String) {
        namedActors.remove(name)
    }

    @MainThreadOnly //I am not sure if it is only main thread, but this is the safer way I guess
    fun removeActorFromScreen(actor: Actor) {
        if (actor is Group) {
            actor.children.forEach { removeActorFromScreen(it) }
        }
        if (actor is StyledActor) {
            actor.styleManager?.let { styleManager ->
                styleManagers.remove(styleManager)
                val parent = actor.parent
                if (parent is FlexBox) {
                    parent.remove(styleManager.node)
                }
            }
        }
        actor.remove()
        //TODO remove from behaviour and dragAndDrop and so on
    }

    fun <T> addOnFocusDetailActor(actor: T) where T : Actor, T : DisplayDetailsOnHoverActor {
        val showHoverDetailLambda = { showHoverDetail(actor, actor, actor.actorTemplate) }
        if (actor is FocusableActor) {
            actor.onFocusChange { _, new ->
                if (new == actor) {
                    if (dragAndDrop.none { it.value.isDragging }) { //TODO MARVIN has to add that this if works in a fight too
                        Gdx.app.postRunnable(showHoverDetailLambda)
                    }
                } else {
                    hideHoverDetail()
                }
            }
        } else {
            actor.onEnter {
                Gdx.app.postRunnable(showHoverDetailLambda)
            }
            actor.onExit {
                hideHoverDetail()
            }
        }
    }

    private fun showHoverDetail(actor: Actor, displayDetailActor: DisplayDetailsOnHoverActor, detailTemplate: String) {
        if (!displayDetailActor.isHoverDetailActive) return
        if (detailTemplate.isBlank()) return
        if (currentHoverDetail != null) hideHoverDetail()
        val detail = screenBuilder.generateFromTemplate(
            detailTemplate,
            displayDetailActor.getFocusDetailData(),
            null,
            this
        ) ?: throw RuntimeException("hover template '$detailTemplate' does not exist")
        displayDetailActor.detailActor = detail

        displayDetailActor.setBoundsOfFocusDetailActor(this)
        currentHoverDetail = detail
        currentDisplayDetailActor = displayDetailActor
        displayDetailActor.onDetailDisplayStarted()

        leaveState("showHoverDetail")
        afterMs(20) {
            if (currentHoverDetail === detail) enterState("showHoverDetail")
        }
    }

    fun removeAllStyleManagers(actor: StyledActor) {
        styleManagers.remove(actor.styleManager)
        if (actor is Group) {
            actor.children
                .filterIsInstance<StyledActor>()
                .forEach { removeAllStyleManagers(it) }
        }
    }

    fun removeAllStyleManagersOfChildren(group: Group) = group
        .children
        .filter { it is StyledActor }
        .forEach { removeAllStyleManagers(it as StyledActor) }

    private fun hideHoverDetail() {

        val currentHoverDetail = currentHoverDetail
        if (currentHoverDetail is StyledActor) {
            removeAllStyleManagers(currentHoverDetail)
        }
        this.currentHoverDetail = null
        currentDisplayDetailActor?.detailActor = null
        currentDisplayDetailActor?.onDetailDisplayEnded()
    }

    @AllThreadsAllowed
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

    @MainThreadOnly
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

    @MainThreadOnly
    override fun hide() {
        super.hide()
        isVisible = false
    }

    fun swapStyleManager(
        old: StyleManager,
        new: StyleManager
    ) { // TODO: this whole swapping stylemanager thing is kinda ugly
        styleManagers = styleManagers.map { if (it === old) new else it }.toMutableList()
    }

    fun addStyleManager(manager: StyleManager) {
        styleManagers.add(manager)
    }

    fun update(delta: Float, isEarly: Boolean = false) {
        SoundPlayer.update(this, playAmbientSounds)
        styleManagers.forEach(StyleManager::update)
        if (printFrameRate) FortyFiveLogger.fps()
        if (!isEarly) screenControllers.forEach(ScreenController::update)
        updateCallbacks()
        stage.act(Gdx.graphics.deltaTime)
        currentHoverDetail?.act(delta)
    }

    fun centeredStageCoordsOfActor(name: String): Vector2 = namedActorOrError(name).let { actor ->
        actor.localToStageCoordinates(Vector2(actor.width / 2, actor.height / 2))
    }

    @MainThreadOnly
    override fun render(delta: Float) = try {
//        Thread.sleep(800) //TODO remove // (please don't, its great to find this method)
        stage.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        val oldStyleManagers = styleManagers.toList()
        if (stage.batch.isDrawing) stage.batch.end()
        stage.viewport.apply()
        doRenderTasks(earlyRenderTasks, additionalEarlyRenderTasks)
        stage.draw()
        doRenderTasks(lateRenderTasks, additionalLateRenderTasks)
        styleManagers
            .filter { it !in oldStyleManagers }
            .forEach(StyleManager::update) //all added items get updated too
        val draggedActorLocal = draggedActor
        if (draggedActorLocal == null) {
            stage.batch.begin()
            currentDisplayDetailActor?.drawFocusDetail(this, stage.batch)
//                currentHoverDetail?.draw(stage.batch, 1f)
            if (currentHoverDetail != null) {
                currentHoverDetail!!.draw(stage.batch, 1f)
            }
            stage.batch.end()
        } else {
            stage.batch.begin()
//            println("x: ${draggedActor.x}  y: ${draggedActor.y}   stagePos: ${draggedActor.localToStageCoordinates(Vector2(0F,0F))}")
            if (draggedActorLocal == draggedPreviewActor && draggedActorLocal is OffSettable) {//current possibilty for dragAndDrop
                val pos = draggedActorLocal.parent.localToStageCoordinates(Vector2(0F, 0F))
                draggedActorLocal.drawOffsetX += pos.x //current possibilty for dragAndDrop
                draggedActorLocal.drawOffsetY += pos.y//TODO ugly VERY UGLY (the part in the if at least, the else is okay)
                draggedActorLocal.draw(stage.batch, 1f)
                draggedActorLocal.drawOffsetX -= pos.x
                draggedActorLocal.drawOffsetY -= pos.y
            } else {
                draggedActorLocal.draw(stage.batch, 1f)
            }
            stage.batch.end()
        }
    } catch (e: Exception) {
        FortyFiveLogger.fatal(e)
    }

    fun styleManagerCount(): Int = styleManagers.size

    private fun doRenderTasks(tasks: List<OnjScreen.() -> Unit>, additionalTasks: MutableList<(Batch) -> Unit>) {
        stage.batch.begin()
        tasks.forEach { it(this) }
        additionalTasks.forEach { it(stage.batch) }
        stage.batch.end()
    }

    @MainThreadOnly
    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    fun confirmationClickTimelineAction(maxTime: Long? = null): Timeline.TimelineAction =
        object : Timeline.TimelineAction() {

            private var finishAt: Long? = null

            override fun start(timeline: Timeline) {
                super.start(timeline)
                maxTime?.let {
                    finishAt = TimeUtils.millis() + it
                }
                awaitingConfirmationClick = true
            }

            override fun isFinished(timeline: Timeline): Boolean {
                if (!awaitingConfirmationClick) return true
                finishAt?.let {
                    if (TimeUtils.millis() >= it) return true
                }
                return false
            }

            override fun end(timeline: Timeline) {
                awaitingConfirmationClick = false
            }
        }

    @MainThreadOnly
    override fun dispose() {
        hide()
        screenControllers.forEach(ScreenController::end)
        stage.dispose()
        additionalDisposables.forEach(Disposable::dispose)
        lifetime.die()
    }

    fun updateSelectable() {
        curSelectionParent.updateFocusableActors(this)
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
}
