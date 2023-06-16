package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.badlogic.gdx.scenes.scene2d.actions.SizeToAction
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.utils.*
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.value.OnjNamedObject
import onj.value.OnjObject
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

    /**
     * binds the callbacks to the actor and sets the [onjScreen]
     */
    @AllThreadsAllowed
    fun bindCallbacks(onjScreen: OnjScreen) {
        this.onjScreen = onjScreen
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

    override val onHoverEnter: BehaviourCallback = callback@ {
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

typealias BehaviourCreator = (onj: OnjNamedObject, actor: Actor) -> Behaviour
typealias BehaviourCallback = Actor.() -> Unit
