package com.microwavestudios.fortyfive.keyInput

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.controllers.Controller
import com.badlogic.gdx.controllers.ControllerListener
import com.badlogic.gdx.controllers.Controllers
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.profile.GlobalSave
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.screen.actors.CustomDirection
import com.microwavestudios.fortyfive.screen.actors.CustomScrollableBox
import com.microwavestudios.fortyfive.screen.commonComponents.WarningParent
import com.microwavestudios.fortyfive.utils.Vector2
import com.microwavestudios.fortyfive.utils.epsilonEquals
import java.util.Stack

/**
 * manages (most) of the inputs in the game. The input system is designed in a way that mostly
 * doesn't care about the input method used, only one implementation should be enough to get an
 * actor working with the mouse, the keyboard and maybe controllers in the future.
 *
 * To participate in the Input system actors must implement [InputActor]
 */
class InputManager(val screen: CustomScreen) : InputProcessor {

    private val actorBuffer: MutableList<Pair<Boolean, InputActor>> = mutableListOf()
    private val actors: MutableSet<InputActor> = mutableSetOf()
        get() {
            actorBuffer.forEach { (added, actor) ->
                if (added) field.add(actor) else field.remove(actor)
            }
            actorBuffer.clear()
            return field
        }

    private val groups: MutableMap<String, MutableList<InputActor>> = mutableMapOf()

    var currentlyDraggedActor: InputActor? = null
        private set

    private val dragAndDrops: MutableList<DragAndDrop> = mutableListOf()

    var keyboardFocused: InputActor? = null
        private set

    private val inputCallbacks: MutableMap<Input, MutableList<() -> Unit>> = mutableMapOf()

    private val activeModifierKeys: MutableList<ModifierKey> = mutableListOf()

    private val modals: Stack<Modal> = Stack()

    private val filters: MutableList<FocusFilter> = mutableListOf()

    private var currentDragAndDropModal: Modal? = null
    private var currentKeyboardDragAndDropActor: InputActor? = null
    private var awaitingDrop: List<InputActor>? = null
    private var lastDraggedOver: InputActor? = null

    var activeController: Controller? = null
        private set

    private val controllerListener = ControllerListenerImpl()
    private val additionalControllerListeners: MutableList<ControllerListener> = mutableListOf()
    private val disabledControllerAxis: MutableSet<ControllerAxis> = mutableSetOf()

    init {
        onInput(GameInputs.focusNext) { focusNext(FocusChangeDirection.NEXT) }
        onInput(GameInputs.focusPrevious) { focusNext(FocusChangeDirection.PREVIOUS) }
        onInput(GameInputs.focusUp) { focusNext(FocusChangeDirection.UP) }
        onInput(GameInputs.focusDown) { focusNext(FocusChangeDirection.DOWN) }
        onInput(GameInputs.focusLeft) { focusNext(FocusChangeDirection.LEFT) }
        onInput(GameInputs.focusRight) { focusNext(FocusChangeDirection.RIGHT) }
        onInput(GameInputs.cancel) { cancelKeyboardDragAndDrop() }
        onInput(GameInputs.scrollUp) { scroll(true, false) }
        onInput(GameInputs.scrollDown) { scroll(false, false) }
        onInput(GameInputs.scrollLeft) { scroll(false, true) }
        onInput(GameInputs.scrollRight) { scroll(true, true) }
        onInput(GameInputs.toggleFullScreen) { FortyFive.globalSave.fullscreen = !FortyFive.globalSave.fullscreen }
    }

    fun addControllerListener(listener: ControllerListener) {
        activeController?.addListener(listener)
        additionalControllerListeners.add(listener)
    }

    fun removeControllerListener(listener: ControllerListener) {
        activeController?.removeListener(listener)
        additionalControllerListeners.remove(listener)
    }

    fun disableAxis(axis: ControllerAxis) {
        disabledControllerAxis.add(axis)
    }

    fun vibrateController(duration: Int, strength: Float) {
        if (!FortyFive.globalSave.enableControllerVibration) return
        val controller = activeController ?: return
        controller.startVibration(duration, strength)
    }

    fun disableStick(left: Boolean) {
        if (left) {
            disabledControllerAxis.add(ControllerAxis.LEFT_X)
            disabledControllerAxis.add(ControllerAxis.LEFT_Y)
        } else {
            disabledControllerAxis.add(ControllerAxis.RIGHT_X)
            disabledControllerAxis.add(ControllerAxis.RIGHT_Y)
        }
    }

    fun controllerConnected(controller: Controller) {
        val event = WarningParent.ShowWarningEvent(WarningParent.Level.INFO, "New Controller connected!")
        screen.events.fire(event)
        screen.events.fire(ControllersChangedEvent)
        if (FortyFive.globalSave.currentControllerUid != null) return
        screen.events.fire(NewControllerSelectedEvent(controller.uniqueId))
    }

    fun controllerDisconnected(controller: Controller) {
        val isActiveController = controller == activeController
        val event = if (isActiveController) {
            WarningParent.ShowWarningEvent(WarningParent.Level.HIGH, "Active controller disconnected!")
        } else {
            WarningParent.ShowWarningEvent(WarningParent.Level.INFO, "Controller disconnected!")
        }
        screen.events.fire(event)
        screen.events.fire(ControllersChangedEvent)
        if (!isActiveController) return
        val nextBestController = Controllers.getControllers().firstOrNull()
        screen.events.fire(NewControllerSelectedEvent(nextBestController?.uniqueId))
    }

    private fun makeControllerActive(controller: Controller) {
        activeController?.let { activeController ->
            activeController.removeListener(controllerListener)
            additionalControllerListeners.forEach { activeController.removeListener(it) }
        }
        activeController = controller
        controller.addListener(controllerListener)
        additionalControllerListeners.forEach { controller.addListener(it) }
    }

    fun recheckFocused() {
        val keyboardFocused = keyboardFocused ?: return
        if (canBeFocused(keyboardFocused)) return
        focusNext(FocusChangeDirection.NEXT)
    }

    fun pushModal(modal: Modal) {
        modals.push(modal)
        recheckFocused()
    }

    fun checkModals() {
        while (modals.isNotEmpty() && modals.peek().finished) modals.pop()
    }

    private fun activeModal(): Modal? = if (modals.isEmpty()) null else modals.peek()

    fun addFilter(filter: FocusFilter) {
        filters.add(filter)
        recheckFocused()
    }

    fun removeFilter(filter: FocusFilter) {
        val removed = filters.remove(filter)
        if (!removed) return
        recheckFocused()
    }

    fun init() {
        screen.events.watchFor<NewControllerSelectedEvent> { (uid) ->
            FortyFive.globalSave.currentControllerUid = uid
            val controller = Controllers.getControllers().find { it.uniqueId == uid }
                ?: return@watchFor
            makeControllerActive(controller)
        }
        val currentControllerUid = FortyFive.globalSave.currentControllerUid
            ?: return
        val controller = Controllers.getControllers().find { it.uniqueId == currentControllerUid }
            ?: return
        makeControllerActive(controller)
    }

    fun update() {
        checkKeyHeldDown()
        checkAxisHeld()
    }

    private fun checkAxisHeld() {

        val controller = activeController ?: return
        val mapping = controller.mapping

        fun checkInput(input: Input, actor: InputActor?): Boolean = input
            .causes
            .filterIsInstance<Input.Cause.ControllerAxisHeld>()
            .any { cause ->
                val value = controller.getAxis(cause.axis.getCode(mapping))
                val valueMatch = if (cause.threshold < 0) {
                    value < cause.threshold
                } else {
                    value > cause.threshold
                }
                if (!valueMatch) return@any false
                if (cause.requireStates.isEmpty()) return@any true
                if (actor == null) return@any false
                cause.requireStates.all { actor.isInInputState(it) }
            }

        inputCallbacks.forEach { (input, callbacks) ->
            if (checkInput(input, null)) callbacks.forEach { it() }
        }
        actors.forEach { actor ->
            actor.inputCallbacks.forEach { (input, callbacks) ->
                if (checkInput(input, actor)) callbacks.forEach { it() }
            }
        }
    }

    private fun checkKeyHeldDown() {

        fun checkInput(input: Input, actor: InputActor?): Boolean = input
            .causes
            .filterIsInstance<Input.Cause.KeyHeldDown>()
            .any { cause ->
                if (!Gdx.input.isKeyPressed(cause.key)) return@any false
                if (cause.requireStates.isEmpty()) return@any true
                if (actor == null) return@any false
                cause.requireStates.all {
                    actor.isInInputState(it)
                }
            }

        inputCallbacks.forEach { (input, callbacks) ->
            if (checkInput(input, null)) callbacks.forEach { it() }
        }
        actors.forEach { actor ->
            actor.inputCallbacks.forEach { (input, callbacks) ->
                if (checkInput(input, actor)) callbacks.forEach { it() }
            }
        }
    }

    private fun canBeFocused(actor: InputActor, enforceLeaf: Boolean = true): Boolean = when {
        enforceLeaf && actor.keyboardFocusable != KeyboardFocusable.LEAF -> false
        !actor.actor.isVisible -> false
        filters.any { filter -> filter.groups.any { actor.inGroup(it) } } -> false
        enforceLeaf && activeModal()?.allowGroups?.none { group -> actor.inGroup(group) } ?: false -> false
        actor.actor.parent !is InputActor -> true
        else -> canBeFocused(actor.actor.parent as InputActor, false)
    }

    /**
     * adds a global input listener
     */
    fun onInput(input: Input, callback: () -> Unit) {
        inputCallbacks.putIfAbsent(input, mutableListOf())
        inputCallbacks[input]!!.add(callback)
    }

    fun addActor(actor: InputActor) {
        actorBuffer.add(true to actor)
    }

    fun removeActor(actor: InputActor) {
        actorBuffer.add(false to actor)
    }

    fun addActorToGroup(actor: InputActor, group: String) {
        groups.putIfAbsent(group, mutableListOf())
        groups[group]!!.add(actor)
    }

    fun removeActorFromGroup(actor: InputActor, group: String) {
        val group = groups[group] ?: return
        group.remove(actor)
    }

    /**
     * adds a new [DragAndDrop] that enables dragging actors in group [source] to
     * group [target]
     */
    fun addDragAndDrop(source: String, target: String): DragAndDrop {
        val dragAndDrop = DragAndDrop(source, target, screen)
        dragAndDrops.add(dragAndDrop)
        return dragAndDrop
    }

    fun enableDragAndDrop(dragAndDrop: DragAndDrop) {
        dragAndDrops.add(dragAndDrop)
    }

    fun disableDragAndDrop(source: String, target: String) {
        dragAndDrops.removeIf { it.source == source && it.target == target }
    }

    fun disableDragAndDrop(dragAndDrop: DragAndDrop) {
        dragAndDrops.remove(dragAndDrop)
    }

    fun focusNext(direction: FocusChangeDirection) {
        val root = findInputActorRoot()
        var new = findNextFocusable(keyboardFocused ?: root, null, direction)
        if (keyboardFocused != null && new == null) {
            new = findNextFocusable(root, null, direction) // wrap around when end is reached
        }
        changeKeyboardFocusedActor(new, false)
    }

    private fun findNextFocusable(
        inputActor: InputActor,
        searchAfter: InputActor?,
        direction: FocusChangeDirection,
        searchUpwards: Boolean = true,
        canBeSelf: Boolean = false,
        strictVerticalHorizontal: Boolean = true,
        cantBeInGrid: FocusGrid? = null
    ): InputActor? {
        val actor = inputActor.actor
        var newCantBeInGrid = cantBeInGrid
        if (
            newCantBeInGrid == null &&
            !canBeSelf &&
            (direction == FocusChangeDirection.NEXT || direction == FocusChangeDirection.PREVIOUS)
        ) {
            newCantBeInGrid = inputActor.partOfFocusGrid
        }
        if (
            inputActor.partOfFocusGrid != null &&
            direction != FocusChangeDirection.NEXT &&
            direction != FocusChangeDirection.PREVIOUS
        ) {
            if (canBeSelf && canBeFocused(inputActor)) return inputActor
            val result = inputActor.partOfFocusGrid?.move(inputActor, direction)
            when (result) {
                is FocusGrid.MoveResult.MovedToActor -> {
                    val next = result.actor
                    return if (next != null && canBeFocused(next)) next else null
                }
                is FocusGrid.MoveResult.LeftGrid -> {}
                else -> {}
            }
        }
        if (inputActor.keyboardFocusable == KeyboardFocusable.GROUP) {
            actor as? Group ?: throw RuntimeException("keyboardFocusable.Group should only be set on groups")
            val orderedChildren = inputActor.childrenInCorrectOrderOrOriginal()
            val alignment = inputActor.childrenFocusAlignment
            var children: Iterable<Actor>
            when (direction) {
                FocusChangeDirection.DOWN -> {
                    children = if (alignment == FocusAlignment.HORIZONTAL && strictVerticalHorizontal) {
                        listOf()
                    } else {
                        orderedChildren
                    }
                }
                FocusChangeDirection.RIGHT -> {
                    children = if (alignment == FocusAlignment.VERTICAL && strictVerticalHorizontal) {
                        listOf()
                    } else {
                        orderedChildren
                    }
                }
                FocusChangeDirection.UP -> {
                    children = if (alignment == FocusAlignment.HORIZONTAL && strictVerticalHorizontal) {
                        listOf()
                    } else {
                        orderedChildren.reversed()
                    }
                }
                FocusChangeDirection.LEFT -> {
                    children = if (alignment == FocusAlignment.VERTICAL && strictVerticalHorizontal) {
                        listOf()
                    } else {
                        orderedChildren.reversed()
                    }
                }
                FocusChangeDirection.NEXT -> {
                    children = orderedChildren
                }
                FocusChangeDirection.PREVIOUS -> {
                    children = orderedChildren.reversed()
                }
            }

            if (inputActor.childrenFocusReversed) children = children.reversed()
            val searchAfterIndex = children.indexOf(searchAfter?.actor)
            children.forEachIndexed { index, child ->
                if (searchAfter != null && searchAfterIndex != -1 && index <= searchAfterIndex) return@forEachIndexed
                if (child !is InputActor) return@forEachIndexed
                val next = findNextFocusable(
                    child,
                    null,
                    direction,
                    searchUpwards = false,
                    canBeSelf = true,
                    strictVerticalHorizontal = false,
                    cantBeInGrid = newCantBeInGrid
                )
                if (next != null) return next
            }
        }
        if (
            canBeSelf &&
            canBeFocused(inputActor) &&
            (inputActor.partOfFocusGrid == null || inputActor.partOfFocusGrid != newCantBeInGrid)
        ) {
            return inputActor
        }
        if (!searchUpwards) return null
        val parent = actor.parent
        if (parent !is InputActor) return null
        return findNextFocusable(
            parent,
            inputActor,
            direction,
            canBeSelf = true,
            strictVerticalHorizontal = strictVerticalHorizontal,
            cantBeInGrid = newCantBeInGrid
        )
    }

    fun changeKeyboardFocusedActor(to: InputActor?) {
        changeKeyboardFocusedActor(to, true)
    }

    private fun scroll(forward: Boolean, horizontal: Boolean) {
        val focused = keyboardFocused?.actor ?: return
        var cur: Actor? = focused
        while (cur != null && cur !is CustomScrollableBox) {
            cur = cur.parent
        }
        cur ?: return
        val dir = cur.scrollDirectionStart
        if (horizontal) {
            if (dir == CustomDirection.BOTTOM || dir == CustomDirection.TOP) return
        } else {
            if (dir == CustomDirection.LEFT || dir == CustomDirection.RIGHT) return
        }
        val amount = 1f
        if (forward) {
            cur.scrolledBy(-amount)
        } else {
            cur.scrolledBy(amount)
        }
    }

    private fun changeKeyboardFocusedActor(to: InputActor?, checkIfActorCanBeFocused: Boolean) {
        if (checkIfActorCanBeFocused && to != null && !canBeFocused(to)) return
        val lastHovered = lastHovered
        if (lastHovered is InputActor) {
            lastHovered.leaveInputStateManually(BaseStates.mouseHover)
            this.lastHovered = null
        }
        var keyboardFocused = keyboardFocused
        keyboardFocused?.leaveInputStateManually(BaseStates.keyboardFocus)
        this.keyboardFocused = to
        keyboardFocused = this.keyboardFocused
        if (keyboardFocused != null) {
            keyboardFocused.enterInputStateManually(BaseStates.keyboardFocus)
            var cur = keyboardFocused
            while (cur is InputActor) {
                val parent = cur.actor.parent as? InputActor ?: break
                parent.childWasKeyboardFocused(cur)
                cur = parent
            }
        }
    }

    fun startKeyboardDragAndDrop(actor: InputActor) {
        if (currentDragAndDropModal != null || currentlyDraggedActor != null) return
        if (!actor.isDraggable) return
        val targets = dragAndDrops.mapNotNull {
            if (actor.inGroup(it.source)) it.target else null
        }
        val modal = Modal(targets, screen)
        currentDragAndDropModal = modal
        currentKeyboardDragAndDropActor = actor
        actor.enterInputStateManually(BaseStates.keyboardDrag)
        val awaitingDrop = mutableListOf<InputActor>()
        groups.forEach { (name, actors) ->
            if (name !in targets) return@forEach
            actors.forEach {
                it.enterInputStateManually(BaseStates.awaitingDropFromKeyboard)
                awaitingDrop.add(it)
            }
        }
        this.awaitingDrop = awaitingDrop
        pushModal(modal)
    }

    fun finishKeyboardDragAndDrop(actor: InputActor) {
        if (!actor.isDropTarget) return
        val dragged = currentKeyboardDragAndDropActor ?: return
        actor.notifyDropped(dragged)
        cancelKeyboardDragAndDrop(false)
    }

    fun cancelKeyboardDragAndDrop(focusPrevious: Boolean = true) {
        val modal = currentDragAndDropModal
        if (modal == null) return
        if (activeModal() !== modal) return
        modal.finished()
        val actor = currentKeyboardDragAndDropActor
        actor?.leaveInputStateManually(BaseStates.keyboardDrag)
        currentKeyboardDragAndDropActor?.leaveInputStateManually(BaseStates.keyboardFocus)
        currentKeyboardDragAndDropActor = null
        currentDragAndDropModal = null
        awaitingDrop?.forEach { it.leaveInputStateManually(BaseStates.awaitingDropFromKeyboard) }
        awaitingDrop = null
        if (focusPrevious) {
            changeKeyboardFocusedActor(actor)
        }
    }

    private fun findInputActorRoot(): InputActor {
        val root = screen.stage.root
        if (root is InputActor) return root
        throw RuntimeException("Stage root does not implement InputActor")
    }

    private fun hit(x: Int, y: Int): Actor? {
        val root = screen.stage.root
        val worldSpace = screen.viewport.unproject(Vector2(x, y))
        val transformed = root.localToParentCoordinates(worldSpace)
        return root.hit(transformed.x, transformed.y, true)
    }

    override fun keyDown(keycode: Int): Boolean {
        ModifierKey.entries.forEach { entry ->
            if (keycode in entry.codes) activeModifierKeys.add(entry)
        }
        return false
    }

    override fun keyUp(keycode: Int): Boolean {

        activeModifierKeys.removeIf { modifier -> keycode in modifier.codes }

        fun checkInput(actor: InputActor?, input: Input): Int {
            var highestPriority = -1
            input.causes.forEach { cause ->
                if (cause !is Input.Cause.Keyboard) return@forEach
                if (cause.primaryKey != keycode) return@forEach
                val modifiersPressed = cause.modifierKeys.all { it in activeModifierKeys }
                if (!modifiersPressed) return@forEach
                if (actor != null) {
                    val inNecessaryStates = cause.requireStates.all { actor.isInInputState(it) }
                    if (!inNecessaryStates) return@forEach
                } else {
                    if (cause.requireStates.isNotEmpty()) return@forEach
                }
                val modifierSize = cause.modifierKeys.size
                val stateSize = cause.requireStates.size
                val priority = modifierSize * 1000 + stateSize
                if (priority > highestPriority) highestPriority = priority
            }
            return highestPriority
        }

        val inDragAndDrop = currentDragAndDropModal != null

        var highestPriority = -1
        var winnerCallbacks: List<() -> Unit>? = null
        actors.forEach { actor ->
            actor.inputCallbacks.forEach { (input, callbacks) ->
                val priority = checkInput(actor, input)
                if (inDragAndDrop) {
                    if (priority > -1 && input == GameInputs.confirmDragAndDrop) finishKeyboardDragAndDrop(actor)
                    return@forEach
                }
                if (priority == -1 || priority <= highestPriority) return@forEach
                highestPriority = priority
                winnerCallbacks = callbacks
            }
        }
        winnerCallbacks?.forEach { it() }
        if (winnerCallbacks != null && winnerCallbacks!!.isNotEmpty()) return false
        highestPriority = -1
        winnerCallbacks = null
        inputCallbacks.forEach { (input, callbacks) ->
            val priority = checkInput(null, input)
            if (priority == -1 || priority <= highestPriority) return@forEach
            highestPriority = priority
            winnerCallbacks = callbacks
        }
        winnerCallbacks?.forEach { it() }
        return false
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun touchDown(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        return false
    }

    override fun touchUp(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        cancelKeyboardDragAndDrop()
        val hit = hit(screenX, screenY)
        if (currentlyDraggedActor != null) {
            finishDrag(screenX, screenY, hit)
            return false
        }

        fun checkInput(input: Input, isHit: Boolean, callbacks: List<() -> Unit>) {
            val cause = input.causes.filterIsInstance<Input.Cause.Mouse>().find { it.button.code == button }
            if (cause == null) return
            if (cause.requireDirectHit && !isHit) return
            callbacks.forEach { it() }
        }

        actors.forEach { inputActor ->
            val isHit = inputActor.actor === hit
            val callbacksForInputs = inputActor.inputCallbacks
            if (!canBeFocused(inputActor, false)) return@forEach
            callbacksForInputs.forEach { input, callbacks ->
                checkInput(input, isHit, callbacks)
            }
        }

        inputCallbacks.forEach { input, callbacks ->
            checkInput(input, false, callbacks)
        }

        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val hit = hit(screenX, screenY)
        if (currentlyDraggedActor == null) {
            if (hit !is InputActor) return false
            if (!hit.isDraggable) return false
            hit.isDragged = true
            hit.enterInputStateManually(BaseStates.mouseDrag)
            screen.mouseDraggedActor = hit
            currentlyDraggedActor = hit
            val awaitingDrop = mutableListOf<InputActor>()
            dragAndDrops.forEach { (drag, drop) ->
                if (!hit.inGroup(drag)) return@forEach
                groups.forEach { (name, actors) ->
                    if (name == drop) awaitingDrop.addAll(actors)
                }
            }
            awaitingDrop.forEach { it.enterInputStateManually(BaseStates.awaitingDropFromMouse) }
            this.awaitingDrop = awaitingDrop
        }
        val actor = currentlyDraggedActor!!
        val newDraggedOver = hit as? InputActor
        if (newDraggedOver != lastDraggedOver) {
            lastDraggedOver?.leaveInputStateManually(BaseStates.draggedHover)
            newDraggedOver?.enterInputStateManually(BaseStates.draggedHover)
            lastDraggedOver = newDraggedOver
        }
        val worldSpace = screen.viewport.unproject(Vector2(screenX, screenY))
        val transformed = worldSpace
//        val transformed = actor.actor.parent.stageToLocalCoordinates(worldSpace)
        actor.dragX = transformed.x - actor.actor.width / 2
        actor.dragY = transformed.y - actor.actor.height / 2
        return false
    }

    private fun finishDrag(screenX: Int, screenY: Int, hitResult: Actor?) {
        val actor = currentlyDraggedActor ?: return
        screen.mouseDraggedActor = null
        currentlyDraggedActor = null
        actor.isDragged = false
        lastDraggedOver?.leaveInputStateManually(BaseStates.draggedHover)
        lastDraggedOver = null
        actor.leaveInputStateManually(BaseStates.mouseDrag)
        awaitingDrop?.forEach { it.leaveInputStateManually(BaseStates.awaitingDropFromMouse) }
        awaitingDrop = null
        if (hitResult !is InputActor) return
        if (!hitResult.isDropTarget) return
        val dragAndDrop =
            dragAndDrops.find { actor.inGroup(it.source) && hitResult.inGroup(it.target) }
        if (dragAndDrop == null) return
        hitResult.notifyDropped(actor)
    }

    var lastHovered: Actor? = null
        private set

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        cancelKeyboardDragAndDrop()
        val hit = hit(screenX, screenY)
        val lastHovered = lastHovered
        if (lastHovered === hit) return false
        if (lastHovered is InputActor) {
            lastHovered.leaveInputStateManually(BaseStates.mouseHover)
        }
        if (hit is InputActor && canBeFocused(hit, enforceLeaf = false)) {
            this@InputManager.changeKeyboardFocusedActor(null, true)
            hit.enterInputStateManually(BaseStates.mouseHover)
        }
        this.lastHovered = hit
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return false
    }

    fun end() {
        activeController?.removeListener(controllerListener)
    }


    private inner class ControllerListenerImpl : ControllerListener {

        private val ignoreAxis: MutableSet<Int> = mutableSetOf()

        override fun connected(controller: Controller?) {
        }

        override fun disconnected(controller: Controller?) {
        }

        override fun buttonDown(
            controller: Controller?,
            buttonCode: Int
        ): Boolean = false

        override fun buttonUp(
            controller: Controller?,
            buttonCode: Int
        ): Boolean {
            controller ?: return false

            fun checkInput(input: Input, actor: InputActor?): Boolean = input
                .causes
                .filterIsInstance<Input.Cause.ControllerButtonBased>()
                .any { cause ->
                    cause.button.getCode(controller.mapping) == buttonCode &&
                            cause.requireStates.all { actor?.isInInputState(it) ?: false }
                }

//            val mapping = controller.mapping
//            when (buttonCode) {
//                mapping.buttonStart -> "buttonStart"
//                mapping.buttonL1 -> "buttonL1"
//                mapping.buttonDpadDown -> "buttonDpadDown"
//                mapping.buttonDpadUp -> "buttonDpadUp"
//                mapping.buttonDpadLeft -> "buttonDpadLeft"
//                mapping.buttonDpadRight -> "buttonDpadRight"
//                mapping.buttonR1 -> "buttonR1"
//                mapping.buttonA -> "buttonA"
//                mapping.buttonB -> "buttonB"
//                mapping.buttonX -> "buttonX"
//                mapping.buttonY -> "buttonY"
//                mapping.buttonBack -> "buttonBack"
//                mapping.buttonRightStick -> "buttonRightStick"
//                mapping.buttonLeftStick -> "buttonLeftStick"
//                else -> buttonCode.toString()
//            }.let { println(it) }

            var foundInput = false
            val inDragAndDrop = currentDragAndDropModal != null
            actors.forEach { actor ->
                actor.inputCallbacks.forEach { (input, callbacks) ->
                    val matches = checkInput(input, actor)
                    if (!matches) return@forEach
                    if (inDragAndDrop) {
                        if (input == GameInputs.confirmDragAndDrop) finishKeyboardDragAndDrop(actor)
                        return@forEach
                    }
                    foundInput = true
                    callbacks.forEach { it() }
                }
            }
            if (foundInput) return false
            inputCallbacks.forEach { (input, callbacks) ->
                val matches = checkInput(input, null)
                if (!matches) return@forEach
                callbacks.forEach { it() }
            }

            return false
        }

        override fun axisMoved(
            controller: Controller?,
            axisCode: Int,
            value: Float
        ): Boolean {
            controller ?: return false
            disabledControllerAxis.forEach { controllerAxis ->
                if (controllerAxis.getCode(controller.mapping) == axisCode) return false
            }
            if (axisCode in ignoreAxis) {
                if (value.epsilonEquals(0f, 0.1f)) ignoreAxis.remove(axisCode)
                return false
            }

            fun checkInput(input: Input, actor: InputActor?): Boolean {
                val result = input
                    .causes
                    .filterIsInstance<Input.Cause.ControllerAxisFlick>()
                    .any { cause ->
                        val valueMatch = if (cause.threshold < 0) {
                            value < cause.threshold
                        } else {
                            value > cause.threshold
                        }
                        cause.axis.getCode(controller.mapping) == axisCode &&
                                cause.requireStates.all { actor?.isInInputState(it) ?: false } &&
                                valueMatch
                    }
                if (!result) return false
                ignoreAxis.add(axisCode)
                return true
            }

            var foundMatch = false
            actors.forEach { actor ->
                actor.inputCallbacks.forEach { (input, callbacks) ->
                    val matches = checkInput(input, actor)
                    if (!matches) return@forEach
                    foundMatch = true
                    callbacks.forEach { it() }
                }
            }
            if (foundMatch) return false
            inputCallbacks.forEach { (input, callbacks) ->
                val matches = checkInput(input, null)
                if (!matches) return@forEach
                callbacks.forEach { it() }
            }

            return false
        }

    }


    /**
     * contains basic input states that correspond to events coming from a specific device. Use only
     * when it is necessary to know where a state comes from. Prefer using [GameInputs]
     */
    object BaseStates {

        val mouseHover = InputState("mouseHover")
        val keyboardFocus = InputState("keyboardFocus")
        val mouseDrag = InputState("mouseDrag")
        val keyboardDrag = InputState("keyboardDrag")
        val awaitingDropFromKeyboard = InputState("awaitingDropFromKeyboard")
        val awaitingDropFromMouse = InputState("awaitingDropFromMouse")
        val draggedHover = InputState("draggedHover")
    }

    /**
     * FocusFilter is used to prevent actors in the groups [groups] from participating in the input
     * system. This is useful when actors are not on-screen the whole time or can be disabled.
     *
     * Note: Unlike [Modal], FocusFilters stack
     */
    class FocusFilter(val groups: List<String>, private val screen: CustomScreen) {

        fun start() {
            screen.inputManager.addFilter(this)
        }

        fun end() {
            screen.inputManager.removeFilter(this)
        }
    }

    /**
     * Modals are used to block all groups but [allowGroups] from participating in the input system.
     * This can be useful when implementing popups, to only allow the user to interact with the popup
     * and nothing else. In that case a [FocusFilter] typically has to be used as well, to block
     * events in the popup when it isn't shown
     *
     * Note: Unlike [FocusFilter], only the most recent modal is active and can block events
     */
    class Modal(val allowGroups: List<String>, private val screen: CustomScreen) {

        var finished: Boolean = false
            private set

        fun push() {
            finished = false
            screen.inputManager.pushModal(this)
        }

        fun finished() {
            finished = true
            screen.inputManager.checkModals()
        }
    }

    /**
     * allows enabling and disabling drag and drops
     */
    data class DragAndDrop(val source: String, val target: String, val screen: CustomScreen) {

        fun enable() {
            screen.inputManager.enableDragAndDrop(this)
        }

        fun disable() {
            screen.inputManager.disableDragAndDrop(this)
        }
    }

    /**
     * a way of controlling the keyboard focus when a lot of elements are present in a grid-like layout
     */
    class FocusGrid {

        private val columns: MutableList<MutableList<InputActor?>> = mutableListOf()

        fun set(x: Int, y: Int, element: InputActor?) {
            val width = columns.size
            val additionalColumnsNeeded = x - width + 1
            if (additionalColumnsNeeded > 0) {
                repeat(additionalColumnsNeeded) { columns.add(mutableListOf()) }
            }
            val column = columns[x]
            val height = column.size
            val additionalElementsNeeded = y - height + 1
            if (additionalElementsNeeded > 0) {
                repeat(additionalElementsNeeded) { column.add(null) }
            }
            val previous = column[y]
            if (previous != null) remove(x, y)
            column[y] = element
            element?.let {
                it.partOfFocusGrid = this
                it.focusGridX = x
                it.focusGridY = y
            }
        }

        fun clear() {
            columns.indices.forEach { x ->
                columns[x].indices.forEach { y ->
                    remove(x, y)
                }
            }
        }

        fun remove(x: Int, y: Int) {
            val column = columns.getOrNull(x) ?: return
            if (y >= column.size) return
            val element = column[y]
            column[y] = null
            element?.partOfFocusGrid = null
        }

        fun get(x: Int, y: Int): InputActor? = columns.getOrNull(x)?.getOrNull(y)

        fun move(start: InputActor, direction: FocusChangeDirection): MoveResult {
            if (start.partOfFocusGrid !== this) {
                throw RuntimeException("FocusGrid.move called with actor that isn't part of the grid")
            }
            var x = start.focusGridX
            var y = start.focusGridY
            when (direction) {
                FocusChangeDirection.UP -> y--
                FocusChangeDirection.DOWN -> y++
                FocusChangeDirection.RIGHT -> x++
                FocusChangeDirection.LEFT -> x--
                else -> {}
            }
            if (x !in columns.indices) return MoveResult.LeftGrid
            val column = columns[x]
            if (y !in column.indices) return MoveResult.LeftGrid
            return MoveResult.MovedToActor(get(x, y))
        }

        operator fun contains(actor: InputActor): Boolean {
            columns.forEach { row ->
                row.forEach { a ->
                    if (a == actor) return true
                }
            }
            return false
        }

        sealed class MoveResult {
            data object LeftGrid : MoveResult()
            class MovedToActor(val actor: InputActor?) : MoveResult()
        }

    }

    data class NewControllerSelectedEvent(val uid: String?)
    data object ControllersChangedEvent

    enum class FocusChangeDirection {
        LEFT, RIGHT, UP, DOWN, NEXT, PREVIOUS
    }

}
