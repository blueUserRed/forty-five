package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.fourinachamber.fortyfive.screen.general.DetailWidget
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.FortyFiveLogger

interface InputActor {

    val actor: Actor
    val inputCallbacks: Map<Input, List<() -> Unit>>
    val observedStates: Set<InputState>

    var detailWidget: DetailWidget?

    var isDraggable: Boolean
    var isDropTarget: Boolean
    val groups: List<String>

    var isDragged: Boolean
    var dragX: Float
    var dragY: Float

    var keyboardFocusable: KeyboardFocusable

    fun <T> initInput(actor: T, screen: OnjScreen) where T : Actor, T : InputActor

    fun onInput(input: Input, callback: () -> Unit)

    fun observeInputState(inputState: InputState)

    fun observeInputState(state: InputState, onEnter: () -> Unit, onLeave: () -> Unit)

    fun onEnterInputState(state: InputState, onEnter: () -> Unit)

    fun onLeaveInputState(state: InputState, onEnter: () -> Unit)

    fun notifyInputStateChanged(state: InputState, entered: Boolean)

    fun isInInputState(state: InputState): Boolean

    fun bindDetailToInputState(state: InputState?)

    fun joinGroup(group: String)

    fun inGroup(group: String): Boolean

    fun onDrop(callback: (InputActor) -> Unit)

    fun notifyDropped(actor: InputActor)

    fun enterInputStateManually(state: InputState)

    fun leaveInputStateManually(state: InputState)

    fun startDragAndDropOn(input: Input)

    fun childrenInCorrectOrder(): List<Actor>? = null

    fun childrenInCorrectOrderOrOriginal(): Iterable<Actor>

}

enum class KeyboardFocusable {
    LEAF, GROUP, NONE
}

class InputActorImpl : InputActor {

    private val _callbacks: MutableMap<Input, MutableList<() -> Unit>> = mutableMapOf()
    override val inputCallbacks: Map<Input, List<() -> Unit>>
        get() = _callbacks

    override val observedStates: Set<InputState>
        get() = _observedStates

    private val dropCallbacks: MutableList<(InputActor) -> Unit> = mutableListOf()

    override var detailWidget: DetailWidget? = null
    private var detailState: InputState? = null

    override var isDraggable: Boolean = false

    override var isDropTarget: Boolean = false
        set(value) {
            // make sure the actors listens to the confirmDragAndDrop Input when it is droppable, or else
            // it wouldn't react when the user selects this as the drop target
            _callbacks.putIfAbsent(GameInputs.confirmDragAndDrop, mutableListOf())
            addToInputManagerIfNecessary()
            field = value
        }

    private val _groups: MutableList<String> = mutableListOf()
    override val groups: List<String>
        get() = _groups

    override var isDragged: Boolean = false
    override var dragX: Float = 0f
    override var dragY: Float = 0f

    override var keyboardFocusable: KeyboardFocusable = KeyboardFocusable.NONE

    private val _observedStates: MutableSet<InputState> = mutableSetOf()

    override val actor: Actor
        get() = _actor

    private lateinit var _actor: Actor
    private lateinit var screen: OnjScreen

    private var wasAdded: Boolean = false

    private val inputStateListeners: MutableMap<InputState, Pair<MutableList<() -> Unit>, MutableList<() -> Unit>>> =
        mutableMapOf()

    private val currentInputStates: MutableSet<InputState> = mutableSetOf()

    override fun <T> initInput(actor: T, screen: OnjScreen) where T : Actor, T : InputActor {
        this._actor = actor
        this.screen = screen
    }

    override fun onInput(input: Input, callback: () -> Unit) {
        _callbacks.putIfAbsent(input, mutableListOf())
        _callbacks[input]!!.add(callback)
        addToInputManagerIfNecessary()
    }

    private fun addToInputManagerIfNecessary() {
        if (wasAdded) return
        screen.inputManager.addActor(actor as InputActor)
        wasAdded = true
    }

    override fun observeInputState(inputState: InputState) {
        _observedStates.add(inputState)
        inputState.causedByStates.forEach { state -> _observedStates.add(state) }
    }

    override fun observeInputState(state: InputState, onEnter: () -> Unit, onLeave: () -> Unit) {
        observeInputState(state)
        inputStateListeners.putIfAbsent(state, mutableListOf<() -> Unit>() to mutableListOf<() -> Unit>())
        val listeners = inputStateListeners[state]!!
        listeners.first.add(onEnter)
        listeners.second.add(onLeave)
    }

    override fun onEnterInputState(state: InputState, onEnter: () -> Unit) {
        observeInputState(state)
        inputStateListeners.putIfAbsent(state, mutableListOf<() -> Unit>() to mutableListOf<() -> Unit>())
        val listeners = inputStateListeners[state]!!
        listeners.first.add(onEnter)
    }

    override fun onLeaveInputState(state: InputState, onLeave: () -> Unit) {
        observeInputState(state)
        inputStateListeners.putIfAbsent(state, mutableListOf<() -> Unit>() to mutableListOf<() -> Unit>())
        val listeners = inputStateListeners[state]!!
        listeners.second.add(onLeave)
    }

    override fun notifyInputStateChanged(state: InputState, entered: Boolean) {
        notifyInputStateChangedRec(state, entered, 50)
    }

    private fun notifyInputStateChangedRec(state: InputState, entered: Boolean, depthLimit: Int) {
        if (depthLimit <= 0) {
            FortyFiveLogger.warn("InputSystem", "input States for actor $actor did not converge after 50 retries")
            return
        }
        if (entered) {
            val added = currentInputStates.add(state)
            if (!added) return
            if (detailState == state) screen.showHoverDetail(actor as InputActor)
        } else {
            val removed = currentInputStates.remove(state)
            if (!removed) return
            if (detailState == state) screen.hideHoverDetail(actor as InputActor)
        }
        val listeners = inputStateListeners[state]
        if (listeners != null) {
            val toCall = if (entered) listeners.first else listeners.second
            toCall.forEach { it() }
        }
        _observedStates.forEach { state ->
            if (state.causedByStates.isEmpty()) return@forEach
            val shouldBeActive = state.causedByStates.any { isInInputState(it) }
            val isActive = isInInputState(state)
            when {
                isActive == shouldBeActive -> {}
                isActive && !shouldBeActive -> notifyInputStateChangedRec(state, false, depthLimit - 1)
                !isActive && shouldBeActive -> notifyInputStateChangedRec(state, true, depthLimit - 1)
            }
        }
    }

    override fun joinGroup(group: String) {
        screen.inputManager.addActorToGroup(actor as InputActor, group)
        _groups.add(group)
    }

    override fun inGroup(group: String): Boolean = group in _groups

    override fun onDrop(callback: (InputActor) -> Unit) {
        dropCallbacks.add(callback)
    }

    override fun notifyDropped(actor: InputActor) {
        dropCallbacks.forEach { it(actor) }
    }

    override fun enterInputStateManually(state: InputState) {
        if (state.causedByStates.isNotEmpty()) {
            throw RuntimeException("only InputStates without causes can be handled manually")
        }
        notifyInputStateChanged(state, true)
    }

    override fun leaveInputStateManually(state: InputState) {
        if (state.causedByStates.isNotEmpty()) {
            throw RuntimeException("only InputStates without causes can be handled manually")
        }
        notifyInputStateChanged(state, false)
    }

    override fun startDragAndDropOn(input: Input) {
        onInput(input) { screen.inputManager.startKeyboardDragAndDrop(actor as InputActor) }
    }

    override fun isInInputState(state: InputState): Boolean = state in currentInputStates

    override fun bindDetailToInputState(state: InputState?) {
        state?.let { observeInputState(it) }
        detailState = state
    }

    override fun childrenInCorrectOrderOrOriginal(): Iterable<Actor> {
        val group = actor as? Group
            ?: throw RuntimeException("childrenInCorrectOrderOrOriginal can only be called on a group")
        return (actor as InputActor).childrenInCorrectOrder() ?: group.children
    }
}
