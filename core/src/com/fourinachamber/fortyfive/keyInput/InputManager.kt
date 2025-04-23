package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Vector2
import java.util.Stack

class InputManager(val screen: OnjScreen) : InputProcessor {

    private val actors: MutableSet<InputActor> = mutableSetOf()

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

    init {
        onInput(GameInputs.focusNext) { focusNext(false) }
        onInput(GameInputs.focusPrevious) { focusNext(true) }
        onInput(GameInputs.cancel) { cancelKeyboardDragAndDrop() }
    }

    fun recheckFocused() {
        val keyboardFocused = keyboardFocused
        if (keyboardFocused == null) return
        if (canBeFocused(keyboardFocused)) return
        focusNext(false)
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
        actors.add(actor)
    }

    fun addActorToGroup(actor: InputActor, group: String) {
        groups.putIfAbsent(group, mutableListOf())
        groups[group]!!.add(actor)
    }

    fun enableDragAndDrop(source: String, target: String) {
        dragAndDrops.add(source to target)
    }

    fun disableDragAndDrop(source: String, target: String) {
        dragAndDrops.removeIf { it.first == source && it.second == target }
    }

    fun focusNext(reverse: Boolean) {
        val root = findInputActorRoot()
        var new = findNextFocusable(keyboardFocused ?: root, null, !reverse)
        if (keyboardFocused != null && new == null) {
            new = findNextFocusable(root, null, !reverse) // wrap around when end is reached
        }
        this@InputManager.changeKeyboardFocusedActor(new, false)
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
        if (keyboardFocused != null) {
            keyboardFocused.leaveInputStateManually(BaseStates.keyboardFocus)
        }
        this.keyboardFocused = to
        keyboardFocused = this.keyboardFocused
        if (keyboardFocused != null) {
            keyboardFocused.enterInputStateManually(BaseStates.keyboardFocus)
        }
    }

    fun startKeyboardDragAndDrop(actor: InputActor) {
        if (currentDragAndDropModal != null || currentlyDraggedActor != null) return
        val targets = dragAndDrops.mapNotNull {
            if (actor.inGroup(it.first)) it.second else null
        }
        val modal = Modal(targets, screen)
        currentDragAndDropModal = modal
        currentKeyboardDragAndDropActor = actor
        pushModal(modal)
    }

    fun finishKeyboardDragAndDrop(actor: InputActor) {
        if (!actor.isDropTarget) return
        val dragged = currentKeyboardDragAndDropActor ?: return
        actor.notifyDropped(dragged)
        cancelKeyboardDragAndDrop()
    }

    fun cancelKeyboardDragAndDrop() {
        val modal = currentDragAndDropModal
        if (modal == null) return
        if (activeModal() !== modal) return
        popModal(modal)
        currentKeyboardDragAndDropActor = null
        currentDragAndDropModal = null
    }

    private fun findInputActorRoot(): InputActor {
        val root = screen.stage.root
        if (root is InputActor) return root
        throw RuntimeException("Stage root does not implement InputActor")
    }

    private fun findNextFocusable(
        inputActor: InputActor,
        searchAfter: InputActor?,
        forward: Boolean,
        searchUpwards: Boolean = true,
        canBeSelf: Boolean = false
    ): InputActor? {
        val actor = inputActor.actor
        if (inputActor.keyboardFocusable == KeyboardFocusable.GROUP) {
            actor as? Group ?: throw RuntimeException("keyboardFocusable.Group should only be set on groups")
            val children = if (forward) actor.children else actor.children.reversed()
            val searchAfterIndex = children.indexOf(searchAfter?.actor)
            children.forEachIndexed { index, child ->
                if (searchAfter != null && searchAfterIndex != -1 && index <= searchAfterIndex) return@forEachIndexed
                if (child !is InputActor) return@forEachIndexed
                val next = findNextFocusable(child, null, forward, searchUpwards = false, canBeSelf = true)
                if (next != null) return next
            }
        }
        if (canBeSelf && canBeFocused(inputActor)) return inputActor
        if (!searchUpwards) return null
        val parent = actor.parent
        if (parent !is InputActor) return null
        return findNextFocusable(parent, inputActor, forward, canBeSelf = true)
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
            actor.inputCallbacks.forEach { input, callbacks ->
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
        highestPriority = -1
        winnerCallbacks = null
        inputCallbacks.forEach { input, callbacks ->
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
            currentlyDraggedActor = hit
        }
        val actor = currentlyDraggedActor!!
        val worldSpace = screen.viewport.unproject(Vector2(screenX, screenY))
        val transformed = actor.actor.parent.stageToLocalCoordinates(worldSpace)
        actor.dragX = transformed.x - actor.actor.width / 2
        actor.dragY = transformed.y - actor.actor.height / 2
        return false
    }

    private fun finishDrag(screenX: Int, screenY: Int, hitResult: Actor?) {
        val actor = currentlyDraggedActor ?: return
        currentlyDraggedActor = null
        actor.isDragged = false
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

        val mouseHover = InputState(arrayOf())
        val keyboardFocus = InputState(arrayOf())
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

}
