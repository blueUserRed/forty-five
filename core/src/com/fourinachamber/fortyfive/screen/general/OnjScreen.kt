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
import kotlinx.coroutines.flow.StateFlow
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
            val maxNbrOfMembers = curSelectionParent.maxSelectionMembers
            if (maxNbrOfMembers > 0 && _selectedActors.size > maxNbrOfMembers) {
                val o = _selectedActors.toList()
                _selectedActors.removeAll(o.subList(0, selectedActors.size - maxNbrOfMembers).toSet())
                o.forEach { it.fire(SelectChangeEvent(oldList, selectedActors, fromMouse)) }
                curSelectionParent.onSelection(selectedActors, fromMouse)
            } else {
                val n = selectedActors.reversed().toList()
                n.forEach { it.fire(SelectChangeEvent(oldList, selectedActors, fromMouse)) }
                curSelectionParent.onSelection(selectedActors, fromMouse)
            }
        }
    }

    private var selectableDirty: Boolean = true

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
                .forEach { it.fire(SelectChangeEvent(oldList, _selectedActors.toList())) }
    }

    private var previousFocusedActor: Actor? = null
    var focusedActor: Actor? = null
        set(value) {
            if (value == null) {
                field?.let { it.fire(FocusChangeEvent(it, null, nextFocusActorSetFromMouse)) }
            } else {
//                if (selectionHierarchy.isEmpty() || !curSelectionParent.hasActor(value)) return
                if (field == value) return
                field?.let { it.fire(FocusChangeEvent(it, value, nextFocusActorSetFromMouse)) }
                value.let { it.fire(FocusChangeEvent(field, it, nextFocusActorSetFromMouse)) }
            }
            previousFocusedActor = field
            field = value
            nextFocusActorSetFromMouse = true
        }

    private var nextFocusActorSetFromMouse: Boolean = true
    private val selectionHierarchy: ArrayDeque<FocusableParent> = ArrayDeque()
    val curSelectionParent: FocusableParent get() = selectionHierarchy.last()

    /**
     * the actor, that jumps to the center of the cursor when holding the click bevor drag and dropping
     */
    var draggedPreviewActor: Actor? = null
    val draggedActor: Actor? get() = dragAndDrop.values.firstNotNullOfOrNull { it.dragActor } ?: draggedPreviewActor

    fun addToSelectionHierarchy(child: FocusableParent) {
        selectionHierarchy.add(child)
        curSelectionParent.updateFocusableActors(this)
        val focusedActor1 = focusedActor
        if (focusedActor1 != null && !curSelectionParent.hasActor(focusedActor1)) {
            focusedActor = null
        }
    }

    fun escapeSelectionHierarchy(fromMouse: Boolean = true, deselectActors: Boolean = true) {
        if (!fromMouse && draggedActor != null) return
        nextFocusActorSetFromMouse = fromMouse
        focusedActor = selectedActors.lastOrNull()
        if (deselectActors) deselectAllExcept()
        if (selectionHierarchy.size >= 2) { //there has to be always at least one selectionGroup for it to work
            val s = selectionHierarchy.removeLast()
            s.onLeave()
            curSelectionParent.updateFocusableActors(this)
        }
        if (focusedActor == null && !fromMouse) {
            nextFocusActorSetFromMouse = false
            focusedActor = if (curSelectionParent.hasActorPrimary(previousFocusedActor)) {
                previousFocusedActor
            } else {
                curSelectionParent.focusNext(null, this) as Actor?
            }
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
        nextFocusActorSetFromMouse = false
        this.focusedActor = focusableElement as Actor?
    }

    fun focusPrevious() {
        if (selectionHierarchy.isEmpty()) return
        val focusableElement = curSelectionParent.focusPrevious(this)
        nextFocusActorSetFromMouse = false
        this.focusedActor = focusableElement as Actor?
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


    val programmedDetailSources: MutableList<DisplayDetailActor> = mutableListOf()

    init {
        addEarlyRenderTask {
            val drawable = backgroundDrawable ?: return@addEarlyRenderTask
            drawable.draw(it, 0f, 0f, stage.viewport.worldWidth, stage.viewport.worldHeight)
        }
        addEarlyRenderTask {
            if (selectableDirty) {
                curSelectionParent.updateFocusableActors(this)
                selectableDirty = false
            }
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
        actor.name = name
    }

    fun removeNamedActor(name: String) {
        namedActors.remove(name)
    }

    @MainThreadOnly //I am not sure if it is only main thread, but this is the safer way I guess
    fun removeActorFromScreen(actor: Actor) {
        if (actor is Group) {
            actor.children.toMutableList().forEach { removeActorFromScreen(it) }
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
        _dragAndDrop.values.forEach { it.removeAllListenersWithActor(actor) }
        if (actor is FocusableActor && actor.group != null) selectableDirty = true
        //TODO remove from behaviour and so on
    }

    fun <T> addOnFocusDetailActor(actor: T) where T : Actor, T : DisplayDetailActor {
        if (actor is FocusableActor) {
            actor.onFocusChange { _, new ->
                if (actor.isFocused) {
                    if (draggedActor == null) {
                        showFocusDetail(actor)
                    }
                } else {
                    hideHoverDetail(actor)
                }
            }
        } else {
            actor.onEnter {
                showFocusDetail(actor)
            }
            actor.onExit {
                hideHoverDetail(actor)
            }
        }
    }

    private fun <T> showFocusDetail(actor: T) where T : Actor, T : DisplayDetailActor {
        val detailWidget = actor.detailWidget ?: return
        if (detailWidget.isShown) return
        val detailActor = detailWidget.generateDetailActor(addFadeInAction = true)
        detailWidget.detailActor = detailActor
        detailWidget.updateBounds(actor)
        actor.fire(DetailDisplayStateChange(true)) //this may be never needed, but its nice to have if we do
    }

    private fun <T> hideHoverDetail(sourceActor: T) where T : Actor, T : DisplayDetailActor {
        sourceActor.fire(DetailDisplayStateChange(false))
        sourceActor.detailWidget?.hide()
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
        val focusedActor1 = focusedActor
        if (focusedActor1 is DisplayDetailActor) focusedActor1.detailWidget?.detailActor?.act(delta)
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

        stage.batch.begin()
        programmedDetailSources.forEach { it.detailWidget?.drawDetailActor(stage.batch) }
        stage.batch.end()
        val draggedActorLocal = draggedActor
        if (draggedActorLocal == null) {
            stage.batch.begin()
            val focusedActor1 = focusedActor
            if (focusedActor1 is DisplayDetailActor) focusedActor1.detailWidget?.drawDetailActor(stage.batch)
            stage.batch.end()
        } else {
            stage.batch.begin()
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
