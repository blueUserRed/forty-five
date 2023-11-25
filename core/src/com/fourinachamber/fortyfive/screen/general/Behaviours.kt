package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.map.events.heals.HealOrMaxHPScreenController
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWarningParent
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.utils.*
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis


/**
 * creates behaviours
 */
object BehaviourFactory {

    private val behaviours: MutableMap<String, BehaviourCreator> = mutableMapOf(
        "OnClickChangeScreenStateBehaviour" to { onj, actor -> OnClickChangeScreenStateBehaviour(onj, actor) },
        "MouseHoverBehaviour" to { onj, actor -> MouseHoverBehaviour(onj, actor) },
        "OnClickExitBehaviour" to { _, actor -> OnClickExitBehaviour(actor) },
        "OnClickAbandonRunBehaviour" to { onj, actor -> OnClickAbandonRunBehaviour(onj, actor) },
        "OnClickChangeScreenBehaviour" to { onj, actor -> OnClickChangeScreenBehaviour(onj, actor) },
        "OnClickResetSavefileBehaviour" to { onj, actor -> OnClickResetSavefileBehaviour(onj, actor) },
        "CatchEventAndEmitBehaviour" to { onj, actor -> CatchEventAndEmitBehaviour(onj, actor) },
        "OnClickSelectHealOrMaxOptionBehaviour" to { onj, actor -> OnClickSelectHealOrMaxOptionBehaviour(onj, actor) },
        "OnClickSelectHealOptionBehaviour" to { onj, actor -> OnClickSelectHealOptionBehaviour(onj, actor) },
        "OnClickRemoveWarningLabelBehaviour" to { onj, actor -> OnClickRemoveWarningLabelBehaviour(onj, actor) },
    )

    /**
     * will return an instance of the behaviour with name [name]
     * @throws RuntimeException when no behaviour with that name exists
     * @param onj the onjObject containing the configuration of the behaviour
     */
    fun behaviorOrError(name: String, onj: OnjNamedObject, actor: Actor): Behaviour {
        val behaviourCreator = behaviours[name] ?: throw RuntimeException("Unknown behaviour: $name")
        return behaviourCreator(onj, actor)
    }

}

/**
 * represents a behaviour of an [Actor]
 */
abstract class Behaviour(val actor: Actor) {

    /**
     * the screenDataProvider; only available after [bindCallbacks] has been called
     */
    lateinit var onjScreen: OnjScreen

    /**
     * called when a hover is started
     */
    protected open val onHoverEnter: @MainThreadOnly BehaviourCallback? = null

    /**
     * called when the hover has ended
     */
    protected open val onHoverExit: @MainThreadOnly BehaviourCallback? = null

    /**
     * called when the actor is clicked. If the actor is a [DisableActor] and [DisableActor.isDisabled] is set to false,
     * this will not be called
     */
    protected open val onCLick: @MainThreadOnly BehaviourCallback? = null

    /**
     * called when the actor is a [DisableActor], [DisableActor.isDisabled] is set to true and the actor is clicked
     */
    protected open val onDisabledCLick: @MainThreadOnly BehaviourCallback? = null

    protected open val onEventCapture: @MainThreadOnly ((event: Event) -> Boolean)? = null

    protected open val onDisabledEventCapture: @MainThreadOnly ((event: Event) -> Boolean)? = null

    /**
     * binds the callbacks to the actor and sets the [onjScreen]
     */
    @AllThreadsAllowed
    fun bindCallbacks(onjScreen: OnjScreen) {
        this.onjScreen = onjScreen
        actor.addListener { event ->
            return@addListener if (actor is DisableActor && !actor.isDisabled) {
                onEventCapture?.invoke(event) ?: false
            } else {
                onDisabledEventCapture?.invoke(event) ?: false
            }
        }
        onHoverEnter?.let { actor.onEnter(it) }
        onHoverExit?.let { actor.onExit(it) }
        actor.onButtonClick {
            if (actor is DisableActor) {
                if (actor.isDisabled) onDisabledCLick?.let { it(actor) }
                else onCLick?.let { it(actor) }
            } else onCLick?.let { it(actor) }
        }
    }

}

class OnClickChangeScreenStateBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val stateName = onj.get<String>("state")
    private val enter = onj.get<Boolean>("enter")

    override val onCLick: BehaviourCallback = {
        if (enter) onjScreen.enterState(stateName) else onjScreen.leaveState(stateName)
    }
}

/**
 * changes the mouse when hovering over an actor
 */
class MouseHoverBehaviour(
    onj: OnjNamedObject,
    actor: Actor
) : Behaviour(actor) {

    private val cursorName = onj.get<String>("cursorName")
    private val useSystemCursor = onj.get<Boolean>("useSystemCursor")

    private var disabledCursorName: String? = null
    private var disabledUseSystemCursor: Boolean? = null

    init {
        if (onj.hasKey<OnjObject>("disabled")) {
            val disabledOnj = onj.get<OnjObject>("disabled")
            disabledCursorName = disabledOnj.get<String>("cursorName")
            disabledUseSystemCursor = disabledOnj.get<Boolean>("useSystemCursor")
        }
    }

    private val cursor: Either<Cursor, SystemCursor> by lazy {
        Utils.loadCursor(useSystemCursor, cursorName, onjScreen)
    }

    private val disabledCursor: Either<Cursor, SystemCursor>? by lazy {
        if (disabledUseSystemCursor != null) {
            Utils.loadCursor(disabledUseSystemCursor!!, disabledCursorName!!, onjScreen)
        } else null
    }

    override val onHoverEnter: BehaviourCallback = callback@{
        if (disabledCursor != null && actor is DisableActor && actor.isDisabled) {
            Utils.setCursor(disabledCursor!!)
            return@callback
        }
        Utils.setCursor(cursor)
    }

    override val onHoverExit: BehaviourCallback = {
        Utils.setCursor(onjScreen.defaultCursor)
    }
}

/**
 * changes the screen when clicked
 */
class OnClickChangeScreenBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val screenPath = onj.get<String>("screenPath")

    override val onCLick: BehaviourCallback = {
        val time = measureTimeMillis {
            FortyFive.changeToScreen(screenPath)
        }
//        println(time)
    }
}

/**
 * exits the application when the actor is clicked
 */
class OnClickExitBehaviour(actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        Gdx.app.exit()
    }
}

class OnClickAbandonRunBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        FortyFiveLogger.debug("OnClickAbandonRunBehaviour", "abandoning run")
        SaveState.reset()
    }

}

class OnClickResetSavefileBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        SaveState.reset()
    }

}

class CatchEventAndEmitBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val eventToCatch: KClass<out Event> = EventFactory.eventClass(onj.get<String>("catch"))
    private val eventToEmit: String = onj.get<String>("emit")
    private val blockCaughtEvent: Boolean = onj.getOr("blockCaughtEvent", true)

    override val onEventCapture: ((event: Event) -> Boolean) = lambda@{ event ->
        if (!eventToCatch.isInstance(event)) return@lambda false
        val eventToEmit = EventFactory.createEvent(eventToEmit)
        actor.fire(eventToEmit)
        return@lambda blockCaughtEvent
    }
}

class OnClickSelectHealOrMaxOptionBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {
    val state = onj.get<String>("state")
    override val onCLick: BehaviourCallback = {
        if (this is StyledActor && inActorState(state)) {
            (onjScreen.screenController as HealOrMaxHPScreenController).complete()
        }
    }

}

class OnClickSelectHealOptionBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {
    private val enterStateName = onj.get<String>("enterState")
    private val acceptButtonName = onj.get<String>("acceptButtonName")
    private val newButtonState = onj.get<String>("newButtonState")
    private val otherOptionName = onj.get<String>("otherOptionName")
    override val onCLick: BehaviourCallback = {

        if (this is StyledActor) this.enterActorState(enterStateName)

        val acceptButton = onjScreen.namedActorOrError(acceptButtonName)
        if (acceptButton is StyledActor) acceptButton.enterActorState(newButtonState)

        val otherOption = onjScreen.namedActorOrError(otherOptionName)
        if (otherOption is StyledActor) otherOption.leaveActorState(enterStateName)
    }

}

class OnClickRemoveWarningLabelBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        println("hi")
//        CustomWarningParent.getWarning(onjScreen).removeWarningByClick(this)
    }

    override val onEventCapture: ((event: Event) -> Boolean) = {
        println("event ig")
        false
    }

    init {
        println("this should hihi")
    }
}

typealias BehaviourCreator = (onj: OnjNamedObject, actor: Actor) -> Behaviour
typealias BehaviourCallback = Actor.() -> Unit
