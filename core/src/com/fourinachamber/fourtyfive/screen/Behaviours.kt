package com.fourinachamber.fourtyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.badlogic.gdx.scenes.scene2d.actions.SizeToAction
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.ParticleEffectActor
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.utils.Either
import com.fourinachamber.fourtyfive.utils.Utils
import ktx.actors.onClick
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.OnjNamedObject
import onj.OnjObject


/**
 * creates behaviours
 */
object BehaviourFactory {

    private val behaviours: MutableMap<String, BehaviourCreator> = mutableMapOf(
        "MouseHoverBehaviour" to { onj, actor -> MouseHoverBehaviour(onj, actor) },
        "OnClickExitBehaviour" to { _, actor -> OnClickExitBehaviour(actor) },
        "OnHoverChangeSizeBehaviour" to { onj, actor -> OnHoverChangeSizeBehaviour(onj, actor) },
        "OnClickMaskBehaviour" to { onj, actor -> OnClickMaskBehaviour(onj, actor) },
        "OnClickChangeScreenBehaviour" to { onj, actor -> OnClickChangeScreenBehaviour(onj, actor) },
        "OnHoverChangeTextureBehaviour" to { onj, actor -> OnHoverChangeTextureBehaviour(onj, actor) },
//        "OnClickParticleEffectBehaviour" to { onj, actor -> OnClickParticleEffectBehaviour(onj, actor) },
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

interface GameScreenBehaviour {
    var gameScreenController: GameScreenController
}

/**
 * represents a behaviour of an [Actor]
 */
abstract class Behaviour(val actor: Actor) {

    /**
     * the screenDataProvider; only available after [bindCallbacks] has been called
     */
    lateinit var screenDataProvider: ScreenDataProvider

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
     * binds the callbacks to the actor and sets the [screenDataProvider]
     */
    fun bindCallbacks(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
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
        Utils.loadCursor(useSystemCursor, cursorName, screenDataProvider)
    }

    private val disabledCursor: Either<Cursor, SystemCursor>? by lazy {
        if (disabledUseSystemCursor != null) {
            Utils.loadCursor(disabledUseSystemCursor!!, disabledCursorName!!, screenDataProvider)
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
        Utils.setCursor(screenDataProvider.defaultCursor)
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
            interpolationOrError(onj.get<String>("enterInterpolation"))
        } else null

        exitInterpolation = if (!onj["exitInterpolation"]!!.isNull()) {
            interpolationOrError(onj.get<String>("exitInterpolation"))
        } else null
    }

    private fun interpolationOrError(name: String): Interpolation = when (name) {

        "swing" -> Interpolation.swing
        "swing in" -> Interpolation.swingIn
        "swing out" -> Interpolation.swingOut
        "bounce" -> Interpolation.bounce
        "bounce in" -> Interpolation.bounceIn
        "bounce out" -> Interpolation.bounceOut
        "elastic" -> Interpolation.elastic
        "elastic in" -> Interpolation.elasticIn
        "elastic out" -> Interpolation.elasticOut
        "circle" -> Interpolation.circle
        "circle in" -> Interpolation.circleIn
        "circle out" -> Interpolation.circleOut

        else -> throw RuntimeException("Unknown interpolation: $name")
    }

    override val onHoverEnter: BehaviourCallback = {
        if (cellName != null) {
            val cell = screenDataProvider.namedCells[cellName] ?: run {
                throw RuntimeException("unknown cell name: $cellName")
            }
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
            val cell = screenDataProvider.namedCells[cellName] ?: run {
                throw RuntimeException("unknown cell name: $cellName")
            }
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


class OnHoverChangeTextureBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val hoverTextureName = onj.get<String>("hoverTexture")
    private val baseTexture: TextureRegion
    private val image: CustomImageActor

    private val hoverTexture: TextureRegion by lazy {
        screenDataProvider.textures[hoverTextureName] ?:
            throw RuntimeException("no texture with name $hoverTextureName")
    }

    init {
        if (actor !is CustomImageActor) {
            throw RuntimeException("OnHoverChangeTextureBehaviour can only be used on an Image")
        }
        image = actor
        baseTexture = actor.texture
    }

    override val onHoverEnter: BehaviourCallback = {
        image.texture = hoverTexture
    }


    override val onHoverExit: BehaviourCallback = {
        image.texture = baseTexture
    }

}

/**
 * when clicked, the actor will have a mask applied. [actor] needs to implement [Maskable]
 */
class OnClickMaskBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val maskTextureName = onj.get<String>("maskTexture")
    private val invert = onj.getOr("invert", false)
    private val maskScaleX = onj.getOr("maskScaleX", 1.0).toFloat()
    private val maskScaleY = onj.getOr("maskScaleY", 1.0).toFloat()
    private val maskOffsetX = onj.getOr("maskOffsetX", 0.0).toFloat()
    private val maskOffsetY = onj.getOr("maskOffsetY", 0.0).toFloat()

    init {
        if (actor !is Maskable) throw RuntimeException("OnClickMaskBehaviour can only be used on a maskable actor")
    }

    override val onCLick: BehaviourCallback = {

        val mask = screenDataProvider.textures[maskTextureName] ?: throw RuntimeException(
            "Unknown mask texture: $maskTextureName"
        )

        actor as Maskable

        actor.mask = mask.texture
        actor.maskScaleX = maskScaleX
        actor.maskScaleY = maskScaleY
        actor.maskOffsetX = maskOffsetX
        actor.maskOffsetY = maskOffsetY
        actor.invert = invert

    }

}

///**
// * starts a particle effect when the actor is clicked
// */
//class OnClickParticleEffectBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {
//
//    private val particlePath = onj.get<String>("file")
//    private val textureDir = onj.get<String>("textureDir")
//
//    private val effectScale = onj.getOr("effectScale", 1.0).toFloat()
//    private val useCursorPosition = onj.getOr("useCursorPos", false)
//
//    override val onCLick: BehaviourCallback = {
//
//        val x: Float
//        val y: Float
//
//        if (useCursorPosition) {
//            val cursorPos = Utils.getCursorPos(screenDataProvider.stage.viewport)
//            x = cursorPos.x
//            y = cursorPos.y
//        } else {
//            x = actor.x + actor.width / 2
//            y = actor.y + actor.height / 2
//        }
//
//        Utils.spawnParticle(screenDataProvider, particlePath, textureDir, x, y, effectScale)
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

        if (setOnlyIfPostProcessorIsNull && screenDataProvider.postProcessor != null) return@callback

        val postProcessor = screenDataProvider.postProcessors[postProcessor] ?: run {
            throw RuntimeException("unknown post processor: $postProcessor")
        }

        val prefPostProcessor = screenDataProvider.postProcessor
        screenDataProvider.postProcessor = postProcessor

        if (time != null) screenDataProvider.afterMs(time) {
            screenDataProvider.postProcessor = prefPostProcessor
            // without manually resizing the aspect ratio of the screen breaks, don't ask me why
            screenDataProvider.screen.resize(Gdx.graphics.width, Gdx.graphics.height)
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
        screenDataProvider.namedActors[popupActorName] ?: run {
            throw RuntimeException("unknown named actor: $popupActorName")
        }
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

class ShootButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor), GameScreenBehaviour {

    override lateinit var gameScreenController: GameScreenController

    override val onCLick: BehaviourCallback = {
        gameScreenController.shoot()
    }

}
class EndTurnButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor), GameScreenBehaviour {

    override lateinit var gameScreenController: GameScreenController

    override val onCLick: BehaviourCallback = {
        gameScreenController.endTurn()
    }

}

class DrawBulletButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor), GameScreenBehaviour {

    override lateinit var gameScreenController: GameScreenController

    override val onCLick: BehaviourCallback = {
        gameScreenController.drawBullet()
    }

}

class DrawCoverCardButtonBehaviour(onj: OnjObject, actor: Actor) : Behaviour(actor), GameScreenBehaviour {

    override lateinit var gameScreenController: GameScreenController

    override val onCLick: BehaviourCallback = {
        gameScreenController.drawCover()
    }

}

typealias BehaviourCreator = (onj: OnjNamedObject, actor: Actor) -> Behaviour
typealias BehaviourCallback = Actor.() -> Unit
