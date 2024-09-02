package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.map.detailMap.Completable
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWarningParent
import com.fourinachamber.fortyfive.screen.general.customActor.DisableActor
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.utils.*
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.reflect.KClass


/**
 * creates behaviours
 */
object BehaviourFactory {

    private val behaviours: MutableMap<String, BehaviourCreator> = mutableMapOf(
        "OnClickChangeScreenStateBehaviour" to { onj, actor, screen -> OnClickChangeScreenStateBehaviour(onj, actor, screen) },
        "OnClickToggleScreenStateBehaviour" to { onj, actor, screen -> OnClickToggleScreenStateBehaviour(onj, actor, screen) },
        "MouseHoverBehaviour" to { onj, actor, screen -> MouseHoverBehaviour(onj, actor, screen) },
        "OnClickExitBehaviour" to { _, actor, screen -> OnClickExitBehaviour(actor, screen) },
        "OnClickAbandonRunBehaviour" to { onj, actor, screen -> OnClickAbandonRunBehaviour(onj, actor, screen) },
        "OnClickChangeScreenBehaviour" to { onj, actor, screen -> OnClickChangeScreenBehaviour(onj, actor, screen) },
        "OnClickResetSavefileBehaviour" to { onj, actor, screen -> OnClickResetSavefileBehaviour(onj, actor, screen) },
        "CatchEventAndEmitBehaviour" to { onj, actor, screen -> CatchEventAndEmitBehaviour(onj, actor, screen) },
        "OnClickSelectHealOrMaxOptionBehaviour" to { onj, actor, screen -> OnClickSelectHealOrMaxOptionBehaviour(onj, actor, screen) },
        "OnClickSelectHealOptionBehaviour" to { onj, actor, screen -> OnClickSelectHealOptionBehaviour(onj, actor, screen) },
        "OnClickRemoveWarningLabelBehaviour" to { onj, actor, screen -> OnClickRemoveWarningLabelBehaviour(onj, actor, screen) },
        "SpamPreventionBehaviour" to { onj, actor, screen -> SpamPreventionBehaviour(onj, actor, screen) },
        "OnClickSoundSituationBehaviour" to { onj, actor, screen -> OnClickSoundSituationBehaviour(onj, actor, screen) },
        "OnClickChangeToInitialScreenBehaviour" to { onj, actor, screen -> OnClickChangeToInitialScreenBehaviour(onj, actor, screen) },
    )

    /**
     * will return an instance of the behaviour with name [name]
     * @throws RuntimeException when no behaviour with that name exists
     * @param onj the onjObject containing the configuration of the behaviour
     */
    fun behaviorOrError(name: String, onj: OnjNamedObject, actor: Actor, screen: OnjScreen): Behaviour {
        val behaviourCreator = behaviours[name] ?: throw RuntimeException("Unknown behaviour: $name")
        return behaviourCreator(onj, actor, screen)
    }

}

/**
 * represents a behaviour of an [Actor]
 */
abstract class Behaviour(val actor: Actor, val onjScreen: OnjScreen) {

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

    init {
        bindCallbacks()
    }

    private fun bindCallbacks() {
        actor.addListener { event ->
            return@addListener if (actor is DisableActor) {
                if (!actor.isDisabled) onEventCapture?.invoke(event) ?: false
                else onDisabledEventCapture?.invoke(event) ?: false
            } else {
                onEventCapture?.invoke(event) ?: false
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

class OnClickChangeScreenStateBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

    private val stateName = onj.get<String>("state")
    private val enter = onj.get<Boolean>("enter")

    override val onCLick: BehaviourCallback = {
        if (enter) onjScreen.enterState(stateName) else onjScreen.leaveState(stateName)
    }
}

class OnClickToggleScreenStateBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

    private val stateName = onj.get<String>("state")

    override val onCLick: BehaviourCallback = {
        if (stateName in onjScreen.screenState) onjScreen.leaveState(stateName) else onjScreen.enterState(stateName)
    }
}

/**
 * changes the mouse when hovering over an actor
 */
class MouseHoverBehaviour(
    onj: OnjNamedObject,
    actor: Actor,
    screen: OnjScreen
) : Behaviour(actor, screen) {

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

    private val cursor: Promise<Either<Cursor, SystemCursor>> = Utils.loadCursor(useSystemCursor, cursorName, onjScreen)

    private val disabledCursor: Promise<Either<Cursor, SystemCursor>>? = if (disabledUseSystemCursor != null) {
            Utils.loadCursor(disabledUseSystemCursor!!, disabledCursorName!!, onjScreen)
        } else {
            null
        }


    override val onHoverEnter: BehaviourCallback = callback@{
        if (disabledCursor != null && disabledCursor.isResolved && actor is DisableActor && actor.isDisabled) {
            Utils.setCursor(disabledCursor.getOrError())
            return@callback
        }
        cursor.ifResolved { Utils.setCursor(it) }
    }

    override val onHoverExit: BehaviourCallback = {
        Utils.setCursor(onjScreen.defaultCursor)
    }
}

/**
 * changes the screen when clicked
 */
class OnClickChangeScreenBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

    private val nextScreen = onj.get<String>("screen")

    private var changedScreen: Boolean = false

    override val onCLick: BehaviourCallback = lambda@{
        if (changedScreen) return@lambda
        changedScreen = true
        FortyFive.changeToScreen(ConfigFileManager.screenBuilderFor(nextScreen))
    }
}

/**
 * exits the application when the actor is clicked
 */
class OnClickExitBehaviour(actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

    override val onCLick: BehaviourCallback = {
        Gdx.app.exit()
    }
}

class OnClickAbandonRunBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

    override val onCLick: BehaviourCallback = {
        FortyFiveLogger.debug("OnClickAbandonRunBehaviour", "abandoning run")
        SaveState.reset()
    }

}

class OnClickResetSavefileBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

    override val onCLick: BehaviourCallback = {
        SaveState.reset()
    }

}

class CatchEventAndEmitBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

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

class OnClickSelectHealOrMaxOptionBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {
    val state = onj.get<String>("state")
    override val onCLick: BehaviourCallback = {
        if (this is StyledActor && inActorState(state)) {
            onjScreen
                .screenControllers
                .filterIsInstance<Completable>()
                .forEach { it.completed() }
        }
    }

}

class OnClickSelectHealOptionBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {
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

class OnClickRemoveWarningLabelBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

    override val onCLick: BehaviourCallback = {
        CustomWarningParent.getWarning(onjScreen).removeWarningByClick(this.parent)
    }
}

class SpamPreventionBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

    private val eventName = onj.get<String>("event")
    private val eventClass = EventFactory.eventClass(eventName)
    private val blockDuration: Int = onj.get<Long>("blockDuration").toInt()

    private var lastEventTime: Long = -1

    override val onEventCapture: (event: Event) -> Boolean = lambda@{ event ->
        if (!eventClass.isInstance(event)) return@lambda false
        val now = TimeUtils.millis()
        val block = lastEventTime + blockDuration > now
        lastEventTime = now
        if (block) event.cancel()
        block
    }
}

class OnClickSoundSituationBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

    private val situation: String = onj.get<String>("situation")

    override val onCLick: BehaviourCallback = {
        SoundPlayer.situation(situation, onjScreen)
    }
}

class OnClickChangeToInitialScreenBehaviour(onj: OnjNamedObject, actor: Actor, screen: OnjScreen) : Behaviour(actor, screen) {

    override val onCLick: BehaviourCallback = {
        FortyFive.changeToInitialScreen()
    }
}

typealias BehaviourCreator = (onj: OnjNamedObject, actor: Actor, screen: OnjScreen) -> Behaviour
typealias BehaviourCallback = Actor.() -> Unit
