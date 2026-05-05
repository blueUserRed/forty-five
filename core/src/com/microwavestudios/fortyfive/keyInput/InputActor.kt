package com.microwavestudios.fortyfive.keyInput

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.screen.commonComponents.DetailWidget
import com.microwavestudios.fortyfive.screen.RenderableScreen

/**
 * Enables an actor to participate in the input system. Works together with [InputManager]
 */
interface InputActor {

    val actor: Actor
    val inputCallbacks: Map<Input, List<() -> Unit>>
    val observedStates: Set<InputState>

    /**
     * the detailWidget is shown to provide additional information, e.g. on hover
     */
    var detailWidget: DetailWidget?

    /**
     * set to true to enable the user to drag the actor around
     */
    var isDraggable: Boolean

    /**
     * set to true to enable the user drop another actor on this one
     */
    var isDropTarget: Boolean
    val groups: List<String>

    /**
     * true when the actor is currently being dragged
     */
    var isDragged: Boolean
    /** the x position of the actor when it is currently dragged */
    var dragX: Float
    /** the y position of the actor when it is currently dragged */
    var dragY: Float

    /**
     * sets in what way the actor participates in the selection hierarchy
     *
     * see [KeyboardFocusable]
     */
    var keyboardFocusable: KeyboardFocusable

    var infoObject: Any?

    var childrenFocusAlignment: FocusAlignment
    var childrenFocusReversed: Boolean

    var partOfFocusGrid: InputManager.FocusGrid?
    var focusGridX: Int
    var focusGridY: Int

    val reusableInputActor: Boolean

    fun <T> initInput(actor: T, screen: RenderableScreen) where T : Actor, T : InputActor

    /**
     * calls [callback] when [input] is triggered
     */
    fun onInput(input: Input, callback: () -> Unit)

    fun observeInputState(inputState: InputState)

    fun observeInputState(state: InputState, onEnter: () -> Unit, onLeave: () -> Unit)

    fun focusShortcut(input: Input)
    fun focusShortcut(input: Input, variableActor: () -> InputActor?)

    /**
     * calls [onEnter] when [state] is entered
     */
    fun onEnterInputState(state: InputState, onEnter: () -> Unit)

    /**
     * calls [onLeave] when [state] is left
     */
    fun onLeaveInputState(state: InputState, onLeave: () -> Unit)

    fun notifyInputStateChanged(state: InputState, entered: Boolean)

    fun isInInputState(state: InputState): Boolean

    /**
     * sets the input state that controls if the [detailWidget] is shown
     */
    fun bindDetailToInputState(state: InputState?)

    fun joinGroup(group: String)

    fun leaveGroup(group: String)

    fun leaveAllGroups()

    fun inGroup(group: String): Boolean

    fun onDrop(callback: (InputActor) -> Unit)

    fun removeOnDropListener(callback: (InputActor) -> Unit)

    fun notifyDropped(actor: InputActor)

    fun enterInputStateManually(state: InputState)

    fun leaveInputStateManually(state: InputState)

    fun startDragAndDropOn(input: Input)

    fun childrenInCorrectOrder(): List<Actor>? = null

    fun childrenInCorrectOrderOrOriginal(): Iterable<Actor>

    /**
     * called when some child of this group was focused using the keyboard
     */
    fun childWasKeyboardFocused(child: InputActor) {}

    fun drawInDrag(batch: Batch, oX: Float, oY: Float)

    fun onRemove()

}

interface ActorWithDragFeatures {

    var alsoDrawOriginalInDrag: Boolean
}

/**
 * what role an actor plays in the selection hierarchy
 *
 * GROUP is used for actors that contain children (direct or not) that can be selected
 *
 * LEAF is used for actors that can be selected themselves
 *
 * NONE is used for actors that don't participate in the selection process and don't have children that
 * can be selected
 */
enum class KeyboardFocusable {
    LEAF, GROUP, NONE
}

enum class FocusAlignment {
    VERTICAL, HORIZONTAL, UNORDERED
}

/**
 * default implementation for the [InputActor] interface
 */
class InputActorImpl : InputActor {

    private val _callbacks: MutableMap<Input, MutableList<() -> Unit>> = mutableMapOf()
    override val inputCallbacks: Map<Input, List<() -> Unit>>
        get() = _callbacks

    override val observedStates: Set<InputState>
        get() = _observedStates

    private val dropCallbacks: MutableList<(InputActor) -> Unit> = mutableListOf()

    private val dropCallbacksBuffer: MutableList<Pair<Boolean, (InputActor) -> Unit>> = mutableListOf()

    override var detailWidget: DetailWidget? = null
    private var detailState: InputState? = null

    override var isDraggable: Boolean = false

    override var isDropTarget: Boolean = false
        set(value) {
            // make sure the actors listens to the confirmDragAndDrop Input when it is droppable, or else
            // it wouldn't react when the user selects this as the drop target
            _callbacks.putIfAbsent(GameInputs.confirmDragAndDrop, mutableListOf())
            observeInputState(GameInputs.States.trueFocused)
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
    private lateinit var screen: RenderableScreen

    private var wasAdded: Boolean = false

    private val inputStateListeners: MutableMap<InputState, Pair<MutableList<() -> Unit>, MutableList<() -> Unit>>> =
        mutableMapOf()

    private val currentInputStates: MutableSet<InputState> = mutableSetOf()

    override var infoObject: Any? = null

    override var childrenFocusAlignment: FocusAlignment = FocusAlignment.UNORDERED
    override var childrenFocusReversed: Boolean = false

    override var partOfFocusGrid: InputManager.FocusGrid? = null
    override var focusGridX: Int = 0
    override var focusGridY: Int = 0

    override val reusableInputActor: Boolean = false

    override fun <T> initInput(actor: T, screen: RenderableScreen) where T : Actor, T : InputActor {
        this._actor = actor
        this.screen = screen
    }

    override fun onInput(input: Input, callback: () -> Unit) {
        _callbacks.putIfAbsent(input, mutableListOf())
        _callbacks[input]!!.add(callback)
        addToInputManagerIfNecessary()
        input.causes.forEach { cause ->
            if (cause !is Input.Cause.RequiresStates) return@forEach
            cause.requireStates.forEach { observeInputState(it) }
        }
    }

    override fun focusShortcut(input: Input) {
        val inputManager = screen.inputManager
        inputManager.onInput(input) {
            inputManager.changeKeyboardFocusedActor(actor as InputActor)
        }
    }

    override fun focusShortcut(
        input: Input,
        variableActor: () -> InputActor?
    ) {
        val inputManager = screen.inputManager
        inputManager.onInput(input) {
            val actor = variableActor() ?: return@onInput
            inputManager.changeKeyboardFocusedActor(actor.actor as InputActor)
        }
    }

    private fun addToInputManagerIfNecessary() {
        if (wasAdded) return
        screen.inputManager.addActor(actor as InputActor)
        wasAdded = true
    }

    override fun observeInputState(inputState: InputState) {
        val added = _observedStates.add(inputState)
        if (!added) return
        inputState.causedByStates.forEach { states ->
            states.forEach { state ->
                observeInputState(state)
            }
        }
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
            FortyFive.logger.warn("InputSystem", "input States for actor $actor did not converge after 50 retries")
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
            val shouldBeActive = state.causedByStates.any { row ->
                row.all { isInInputState(it) }
            }
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

    override fun leaveGroup(group: String) {
        screen.inputManager.removeActorFromGroup(actor as InputActor, group)
        _groups.remove(group)
    }

    override fun leaveAllGroups() {
        _groups.forEach { group ->
            screen.inputManager.removeActorFromGroup(actor as InputActor, group)
        }
        _groups.clear()
    }

    override fun inGroup(group: String): Boolean = group in _groups

    override fun onDrop(callback: (InputActor) -> Unit) {
        dropCallbacksBuffer.add(true to callback)
    }

    override fun removeOnDropListener(callback: (InputActor) -> Unit) {
        dropCallbacksBuffer.add(false to callback)
    }

    override fun notifyDropped(actor: InputActor) {
        dropCallbacksBuffer.forEach { (add, callback) ->
            if (add) {
                dropCallbacks.add(callback)
            } else {
                dropCallbacks.remove(callback)
            }
        }
        dropCallbacksBuffer.clear()
        dropCallbacks.forEach { it(actor) }
    }

    override fun enterInputStateManually(state: InputState) {
        if (state.causedByStates.isNotEmpty()) {
            throw RuntimeException("only InputStates without causes can be handled manually")
        }
        (actor as InputActor).notifyInputStateChanged(state, true)
    }

    override fun leaveInputStateManually(state: InputState) {
        if (state.causedByStates.isNotEmpty()) {
            throw RuntimeException("only InputStates without causes can be handled manually")
        }
        (actor as InputActor).notifyInputStateChanged(state, false)
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

    override fun drawInDrag(batch: Batch, oX: Float, oY: Float) {
        actor.draw(batch, 1f)
    }

    override fun onRemove() {
        if ((actor as InputActor).reusableInputActor) return
        screen.inputManager.removeActor(actor as InputActor)
        leaveAllGroups()
    }
}
