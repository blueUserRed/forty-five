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
        "OnHoverChangeSizeBehaviour" to { onj, actor -> OnHoverChangeSizeBehaviour(onj, actor) },
        "OnClickAbandonRunBehaviour" to { onj, actor -> OnClickAbandonRunBehaviour(onj, actor) },
        "OnClickRemoveActorBehaviour" to { onj, actor -> OnClickRemoveActorBehaviour(onj, actor) },
        "OnClickChangeScreenBehaviour" to { onj, actor -> OnClickChangeScreenBehaviour(onj, actor) },
        "OnHoverChangeFontSizeBehaviour" to { onj, actor -> OnHoverChangeFontSizeBehaviour(onj, actor) },
        "OnClickResetSavefileBehaviour" to { onj, actor -> OnClickResetSavefileBehaviour(onj, actor) },
        "ShootButtonBehaviour" to { onj, actor -> ShootButtonBehaviour(onj, actor) },
        "EndTurnButtonBehaviour" to { onj, actor -> EndTurnButtonBehaviour(onj, actor) },
        "DrawBulletButtonBehaviour" to { onj, actor -> DrawBulletButtonBehaviour(onj, actor) },
        "DrawCoverCardButtonBehaviour" to { onj, actor -> DrawCoverCardButtonBehaviour(onj, actor) },
        "OnClickOpenPopupBehaviour" to { onj, actor -> OnClickOpenPopupBehaviour(onj, actor) }
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

class OnClickOpenPopupBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val popupName = onj.get<String>("popupName")

    override val onCLick: BehaviourCallback = {
        onjScreen.showPopup(popupName)
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

class OnClickRemoveActorBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        actor.parent.removeActor(actor)
    }

}

/**
 * changes the size of the actor (or of a named cell) when the actor is hovered over
 */
class OnHoverChangeSizeBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val cellName: String? = onj.getOr("cellName", null)
    private val enterDuration: Float =onj.get<Double>("enterDuration").toFloat()
    private val exitDuration: Float = onj.get<Double>("exitDuration").toFloat()
    private val baseX: Float = onj.get<Double>("baseX").toFloat()
    private val baseY: Float = onj.get<Double>("baseY").toFloat()
    private val targetX: Float = onj.get<Double>("targetX").toFloat()
    private val targetY: Float = onj.get<Double>("targetY").toFloat()
    private val enterInterpolation: Interpolation?
    private val exitInterpolation: Interpolation?

    init {
        enterInterpolation = if (!onj["enterInterpolation"]!!.isNull()) {
            Utils.interpolationOrError(onj.get<String>("enterInterpolation"))
        } else null

        exitInterpolation = if (!onj["exitInterpolation"]!!.isNull()) {
            Utils.interpolationOrError(onj.get<String>("exitInterpolation"))
        } else null
    }

    override val onHoverEnter: BehaviourCallback = {
        if (cellName != null) {
            val cell = onjScreen.namedCellOrError(cellName)
            val action = GrowCellAction(cell, targetX, targetY)
            action.duration = enterDuration
            enterInterpolation?.let { action.interpolation = it }
            actor.addAction(action)
        } else {
            val action = SizeToAction()
            action.width = targetX
            action.height = targetY
            action.duration = enterDuration
            enterInterpolation?.let { action.interpolation = it }
            actor.addAction(action)
        }
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override val onHoverExit: BehaviourCallback = {
        if (cellName != null) {
            val cell = onjScreen.namedCellOrError(cellName)
            val action = GrowCellAction(cell, baseX, baseY)
            action.duration = exitDuration
            exitInterpolation?.let { action.interpolation = it }
            actor.addAction(action)
        } else {
            val action = SizeToAction()
            action.width = baseX
            action.height = baseY
            action.duration = exitDuration
            exitInterpolation?.let { action.interpolation = it }
            actor.addAction(action)
        }
        if (actor is Layout) actor.invalidateHierarchy()
    }

    /**
     * Action that grows a cell
     */
    private class GrowCellAction(
        private val cell: Cell<*>,
        private val targetX: Float,
        private val targetY: Float
    ) : RelativeTemporalAction() {

        private val startX = cell.actorWidth
        private val startY = cell.actorHeight

        private var percent: Float = 0.0f

        override fun updateRelative(percentDelta: Float) {
            percent += percentDelta
            cell.width(startX + percent * (targetX - startX))
            cell.height(startY + percent * (targetY - startY))
            cell.table.invalidate()
        }

    }
}

class OnClickResetSavefileBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        SaveState.reset()
    }

}

class OnHoverChangeFontSizeBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val enterDuration: Float =onj.get<Double>("enterDuration").toFloat()
    private val exitDuration: Float = onj.get<Double>("exitDuration").toFloat()
    private val targetFontScale: Float = onj.get<Double>("targetFontScale").toFloat()
    private val baseFontScale: Float = onj.get<Double>("baseFontScale").toFloat()
    private val enterInterpolation: Interpolation?
    private val exitInterpolation: Interpolation?

    private val label: Label

    init {
        enterInterpolation = if (!onj["enterInterpolation"]!!.isNull()) {
            Utils.interpolationOrError(onj.get<String>("enterInterpolation"))
        } else null

        exitInterpolation = if (!onj["exitInterpolation"]!!.isNull()) {
            Utils.interpolationOrError(onj.get<String>("exitInterpolation"))
        } else null

        if (actor !is Label) throw RuntimeException("OnHoverChangeFontSizeBehaviour can only be used on a label!")
        label = actor
    }

    override val onHoverEnter: BehaviourCallback = {
        val action = ChangeFontScaleAction(targetFontScale, label)
        action.duration = enterDuration
        enterInterpolation?.let { action.interpolation = it }
        actor.addAction(action)
    }

    override val onHoverExit: BehaviourCallback = {
        val action = ChangeFontScaleAction(baseFontScale, label)
        action.duration = exitDuration
        exitInterpolation?.let { action.interpolation = it }
        actor.addAction(action)
    }

    private class ChangeFontScaleAction(val targetScale: Float, val label: Label) : TemporalAction() {

        private val startScale = label.fontScaleX

        override fun update(percent: Float) {
            label.setFontScale((targetScale - startScale) * percent + startScale)
        }
    }

}

class ShootButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        FortyFive.currentGame!!.shoot()
    }

}
class EndTurnButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        FortyFive.currentGame!!.endTurn()
    }

}

class DrawBulletButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        FortyFive.currentGame!!.drawBullet()
    }

}

class DrawCoverCardButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        FortyFive.currentGame!!.drawCover()
    }

}

typealias BehaviourCreator = (onj: OnjNamedObject, actor: Actor) -> Behaviour
typealias BehaviourCallback = Actor.() -> Unit