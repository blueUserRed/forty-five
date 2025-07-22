package com.microwavestudios.fortyfive.keyInput

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.utils.Vector2
import java.util.Stack

class InputManager(val screen: OnjScreen) : InputProcessor {

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

    init {
        onInput(GameInputs.focusNext) { focusNext(FocusChangeDirection.NEXT) }
        onInput(GameInputs.focusPrevious) { focusNext(FocusChangeDirection.PREVIOUS) }
        onInput(GameInputs.focusUp) { focusNext(FocusChangeDirection.UP) }
        onInput(GameInputs.focusDown) { focusNext(FocusChangeDirection.DOWN) }
        onInput(GameInputs.focusLeft) { focusNext(FocusChangeDirection.LEFT) }
        onInput(GameInputs.focusRight) { focusNext(FocusChangeDirection.RIGHT) }
        onInput(GameInputs.cancel) { cancelKeyboardDragAndDrop() }
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

    private fun canBeFocused(actor: InputActor, enforceLeaf: Boolean = true): Boolean = when {
        enforceLeaf && actor.keyboardFocusable != KeyboardFocusable.LEAF -> false
        !actor.actor.isVisible -> false
        filters.any { filter -> filter.groups.any { actor.inGroup(it) } } -> false
        enforceLeaf && activeModal()?.allowGroups?.none { group -> actor.inGroup(group) } ?: false -> false
        actor.actor.parent !is InputActor -> true
        else -> canBeFocused(actor.actor.parent as InputActor, false)
    }

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
            val children: Iterable<Actor>
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
            var parent = keyboardFocused.actor.parent
            while (parent is InputActor) {
                parent.childWasKeyboardFocused(keyboardFocused)
                parent = parent.parent
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
                var priority = modifierSize * 1000 + stateSize
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
        if (currentlyDraggedActor != null) finishDrag(screenX, screenY, hit)

        fun checkInput(input: Input, isHit: Boolean, callbacks: List<() -> Unit>) {
            val cause = input.causes.filterIsInstance<Input.Cause.Mouse>().find { it.button.code == button }
            if (cause == null) return
            if (cause.requireDirectHit && !isHit) return
            callbacks.forEach { it() }
        }

        actors.forEach { inputActor ->
            val isHit = inputActor.actor === hit
            val callbacksForInputs = inputActor.inputCallbacks
            if (!canBeFocused(inputActor)) return@forEach
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

    private var lastHovered: Actor? = null

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

    object BaseStates {

        val mouseHover = InputState("mouseHover")
        val keyboardFocus = InputState("keyboardFocus")
        val mouseDrag = InputState("mouseDrag")
        val keyboardDrag = InputState("keyboardDrag")
        val awaitingDropFromKeyboard = InputState("awaitingDropFromKeyboard")
        val awaitingDropFromMouse = InputState("awaitingDropFromMouse")
        val draggedHover = InputState("draggedHover")
    }

    class FocusFilter(val groups: List<String>, private val screen: OnjScreen) {

        fun start() {
            screen.inputManager.addFilter(this)
        }

        fun end() {
            screen.inputManager.removeFilter(this)
        }
    }

    class Modal(val allowGroups: List<String>, private val screen: OnjScreen) {

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

    data class DragAndDrop(val source: String, val target: String, val screen: OnjScreen) {

        fun enable() {
            screen.inputManager.enableDragAndDrop(this)
        }

        fun disable() {
            screen.inputManager.disableDragAndDrop(this)
        }
    }

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

    enum class FocusChangeDirection {
        LEFT, RIGHT, UP, DOWN, NEXT, PREVIOUS
    }

}
