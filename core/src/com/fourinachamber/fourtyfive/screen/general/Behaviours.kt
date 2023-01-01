package com.fourinachamber.fourtyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.badlogic.gdx.scenes.scene2d.actions.SizeToAction
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.SaveState
import com.fourinachamber.fourtyfive.utils.Either
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.Utils
import ktx.actors.onClick
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.value.OnjNamedObject
import onj.value.OnjObject


/**
 * creates behaviours
 */
object BehaviourFactory {

    private val behaviours: MutableMap<String, BehaviourCreator> = mutableMapOf(
        "MouseHoverBehaviour" to { onj, actor -> MouseHoverBehaviour(onj, actor) },
        "OnClickExitBehaviour" to { _, actor -> OnClickExitBehaviour(actor) },
        "OnHoverChangeSizeBehaviour" to { onj, actor -> OnHoverChangeSizeBehaviour(onj, actor) },
//        "OnClickMaskBehaviour" to { onj, actor -> OnClickMaskBehaviour(onj, actor) },
        "OnClickAbandonRunBehaviour" to { onj, actor -> OnClickAbandonRunBehaviour(onj, actor) },
        "OnClickRemoveActorBehaviour" to { onj, actor -> OnClickRemoveActorBehaviour(onj, actor) },
        "OnClickChangeScreenBehaviour" to { onj, actor -> OnClickChangeScreenBehaviour(onj, actor) },
        "OnHoverChangeFontSizeBehaviour" to { onj, actor -> OnHoverChangeFontSizeBehaviour(onj, actor) },
        "OnHoverChangeTextureBehaviour" to { onj, actor -> OnHoverChangeTextureBehaviour(onj, actor) },
        "OnClickResetSavefileBehaviour" to { onj, actor -> OnClickResetSavefileBehaviour(onj, actor) },
        "OnClickChangePostProcessorBehaviour" to { onj, actor -> OnClickChangePostProcessorBehaviour(onj, actor) },
        "OnHoverPopupBehaviour" to { onj, actor -> OnHoverPopupBehaviour(onj, actor) },
        "ShootButtonBehaviour" to { onj, actor -> ShootButtonBehaviour(onj, actor) },
        "EndTurnButtonBehaviour" to { onj, actor -> EndTurnButtonBehaviour(onj, actor) },
        "DrawBulletButtonBehaviour" to { onj, actor -> DrawBulletButtonBehaviour(onj, actor) },
        "DrawCoverCardButtonBehaviour" to { onj, actor -> DrawCoverCardButtonBehaviour(onj, actor) }
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
    protected open val onHoverEnter: BehaviourCallback? = null

    /**
     * called when the hover has ended
     */
    protected open val onHoverExit: BehaviourCallback? = null

    /**
     * called when the actor is clicked. If the actor is a [DisableActor] and [DisableActor.isDisabled] is set to false,
     * this will not be called
     */
    protected open val onCLick: BehaviourCallback? = null

    /**
     * called when the actor is a [DisableActor], [DisableActor.isDisabled] is set to true and the actor is clicked
     */
    protected open val onDisabledCLick: BehaviourCallback? = null

    /**
     * binds the callbacks to the actor and sets the [onjScreen]
     */
    fun bindCallbacks(onjScreen: OnjScreen) {
        this.onjScreen = onjScreen
        onHoverEnter?.let { actor.onEnter(it) }
        onHoverExit?.let { actor.onExit(it) }
        actor.onClick {
            if (actor is DisableActor) {
                if (actor.isDisabled) onDisabledCLick?.let { it() }
                else onCLick?.let { it() }
            } else onCLick?.let { it() }
        }
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
        FourtyFive.curScreen = ScreenBuilderFromOnj(Gdx.files.internal(screenPath)).build()
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
        FourtyFiveLogger.debug("OnClickAbandonRunBehaviour", "abandoning run")
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

class OnHoverChangeTextureBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val hoverDrawableName = onj.get<String>("hoverTexture")
    private val baseDrawable: Drawable
    private val image: CustomImageActor

    private val hoverDrawable: Drawable by lazy {
        onjScreen.drawableOrError(hoverDrawableName)
    }

    init {
        if (actor !is CustomImageActor) {
            throw RuntimeException("OnHoverChangeTextureBehaviour can only be used on an Image")
        }
        image = actor
        baseDrawable = actor.drawable
    }

    override val onHoverEnter: BehaviourCallback = {
        image.drawable = hoverDrawable
    }


    override val onHoverExit: BehaviourCallback = {
        image.drawable = baseDrawable
    }

}

///**
// * when clicked, the actor will have a mask applied. [actor] needs to implement [Maskable]
// */
//class OnClickMaskBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {
//
//    private val maskTextureName = onj.get<String>("maskTexture")
//    private val invert = onj.getOr("invert", false)
//    private val maskScaleX = onj.getOr("maskScaleX", 1.0).toFloat()
//    private val maskScaleY = onj.getOr("maskScaleY", 1.0).toFloat()
//    private val maskOffsetX = onj.getOr("maskOffsetX", 0.0).toFloat()
//    private val maskOffsetY = onj.getOr("maskOffsetY", 0.0).toFloat()
//
//    init {
//        if (actor !is Maskable) throw RuntimeException("OnClickMaskBehaviour can only be used on a maskable actor")
//    }
//
//    override val onCLick: BehaviourCallback = {
//
//        val mask = onjScreen.drawableOrError(maskTextureName)
//
//        actor as Maskable
//
//        actor.mask = mask.texture
//        actor.maskScaleX = maskScaleX
//        actor.maskScaleY = maskScaleY
//        actor.maskOffsetX = maskOffsetX
//        actor.maskOffsetY = maskOffsetY
//        actor.invert = invert
//
//    }
//
//}

/**
 * when clicked, will change the PostProcessor of the whole screen
 */
class OnClickChangePostProcessorBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor) {

    private val time: Int? = if (!onj["duration"]!!.isNull()) {
        onj.get<Long>("duration").toInt()
    } else null

    private val postProcessor: String? = if (!onj["postProcessor"]!!.isNull()) {
        onj.get<String>("postProcessor")
    } else null

    private val setOnlyIfPostProcessorIsNull: Boolean = onj.get<Boolean>("setOnlyIfNoPostProcessorIsSet")

    override val onCLick: BehaviourCallback = callback@ {

        if (setOnlyIfPostProcessorIsNull && onjScreen.postProcessor != null) return@callback

        val postProcessor = onjScreen.postProcessorOrError(postProcessor!!)

        val prefPostProcessor = onjScreen.postProcessor
        onjScreen.postProcessor = postProcessor

        if (time != null) onjScreen.afterMs(time) {
            onjScreen.postProcessor = prefPostProcessor
            // without manually resizing the aspect ratio of the screen breaks, don't ask me why
            onjScreen.resize(Gdx.graphics.width, Gdx.graphics.height)
        }
    }

}

/**
 * sets the visibility of another actor when this actor is hovered over
 */
class OnHoverPopupBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor) {

    private val popupActorName = onj.get<String>("popupName")
    private val setPopupPosition = onj.get<Boolean>("setPopupPosition")

    private val xOffset = onj.getOr("xOffset", 0.0).toFloat()
    private val yOffset = onj.getOr("yOffset", 0.0).toFloat()

    private val popupActor by lazy {
        onjScreen.namedActorOrError(popupActorName)
    }

    override val onHoverEnter: BehaviourCallback = {
        if (setPopupPosition) {
            popupActor.x = actor.x + xOffset
            popupActor.y = actor.y + yOffset
        }
        popupActor.isVisible = true
    }

    override val onHoverExit: BehaviourCallback = {
        popupActor.isVisible = false
    }
}

class ShootButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        FourtyFive.currentGame!!.shoot()
    }

}
class EndTurnButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        FourtyFive.currentGame!!.endTurn()
    }

}

class DrawBulletButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        FourtyFive.currentGame!!.drawBullet()
    }

}

class DrawCoverCardButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        FourtyFive.currentGame!!.drawCover()
    }

}

typealias BehaviourCreator = (onj: OnjNamedObject, actor: Actor) -> Behaviour
typealias BehaviourCallback = Actor.() -> Unit
