package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Vector2
import com.fourinachamber.fortyfive.utils.between
import java.util.Stack

class InputManager(val screen: OnjScreen) : InputProcessor {

    private val actorBuffer: MutableList<InputActor> = mutableListOf()
    private val actors: MutableSet<InputActor> = mutableSetOf()
        get() {
            field.addAll(actorBuffer)
            actorBuffer.clear()
            return field
        }

    private val groups: MutableMap<String, MutableList<InputActor>> = mutableMapOf()

    private var currentlyDraggedActor: InputActor? = null

    private val dragAndDrops: MutableList<Pair<String, String>> = mutableListOf()

    private var keyboardFocused: InputActor? = null

    private val inputCallbacks: MutableMap<Input, MutableList<() -> Unit>> = mutableMapOf()

    private val activeModifierKeys: MutableList<ModifierKey> = mutableListOf()

    private val modals: Stack<Modal> = Stack()

    private val filters: MutableList<FocusFilter> = mutableListOf()

    private var currentDragAndDropModal: Modal? = null
    private var currentKeyboardDragAndDropActor: InputActor? = null
    private var awaitingDrop: List<InputActor>? = null

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

    fun popModal(modal: Modal) {
        val current = modals.peek()
        if (current !== modal) throw RuntimeException("tried to pop modal $modal that isn't at the top of the stack")
        modals.pop()
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
        actorBuffer.add(actor)
    }

    fun addActorToGroup(actor: InputActor, group: String) {
        groups.putIfAbsent(group, mutableListOf())
        groups[group]!!.add(actor)
    }

    fun removeActorFromGroup(actor: InputActor, group: String) {
        val group = groups[group] ?: return
        group.remove(actor)
    }

    fun enableDragAndDrop(source: String, target: String) {
        dragAndDrops.add(source to target)
    }

    fun disableDragAndDrop(source: String, target: String) {
        dragAndDrops.removeIf { it.first == source && it.second == target }
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
            if (canBeSelf && canBeFocused(inputActor, enforceLeaf = false)) return inputActor
            val next = inputActor.partOfFocusGrid?.move(inputActor, direction)
            if (next != null && canBeFocused(next, enforceLeaf = false)) return next
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
                else -> {
                    children = orderedChildren
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
            if (actor.inGroup(it.first)) it.second else null
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
        popModal(modal)
        val actor = currentKeyboardDragAndDropActor
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
        if (currentlyDraggedActor == null) {
            val hit = hit(screenX, screenY)
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
        actor.leaveInputStateManually(BaseStates.mouseDrag)
        awaitingDrop?.forEach { it.leaveInputStateManually(BaseStates.awaitingDropFromMouse) }
        awaitingDrop = null
        if (hitResult !is InputActor) return
        if (!hitResult.isDropTarget) return
        val dragAndDrop =
            dragAndDrops.find { actor.inGroup(it.first) && hitResult.inGroup(it.second) }
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
        if (hit is InputActor) {
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

        val mouseHover = InputState("mouseHover", arrayOf())
        val keyboardFocus = InputState("keyboardFocus", arrayOf())
        val mouseDrag = InputState("mouseDrag", arrayOf())
        val keyboardDrag = InputState("keyboardDrag", arrayOf())
        val awaitingDropFromKeyboard = InputState("awaitingDropFromKeyboard", arrayOf())
        val awaitingDropFromMouse = InputState("awaitingDropFromMouse", arrayOf())
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

        fun push() {
            screen.inputManager.pushModal(this)
        }

        fun finished() {
            screen.inputManager.popModal(this)
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

        fun remove(x: Int, y: Int) {
            val column = columns.getOrNull(x) ?: return
            if (y >= column.size) return
            val element = column[y]
            column[y] = null
            element?.partOfFocusGrid = null
        }

        fun get(x: Int, y: Int): InputActor? = columns.getOrNull(x)?.getOrNull(y)

        fun move(start: InputActor, direction: FocusChangeDirection): InputActor? {
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
            x = x.between(0, columns.size - 1)
            val column = columns[x]
            y = y.between(0, column.size - 1)
            return get(x, y)
        }

        operator fun contains(actor: InputActor): Boolean {
            columns.forEach { row ->
                row.forEach { a ->
                    if (a == actor) return true
                }
            }
            return false
        }

    }

    enum class FocusChangeDirection {
        LEFT, RIGHT, UP, DOWN, NEXT, PREVIOUS
    }

}
