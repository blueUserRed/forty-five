package com.blueuserred.testgame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.badlogic.gdx.scenes.scene2d.actions.SizeToAction
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.ParticleEffectActor
import ktx.actors.onClick
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.OnjNamedObject
import onj.OnjObject


object BehaviourFactory {

    private val behaviours: MutableMap<String, BehaviourCreator> = mutableMapOf(
        "MouseHoverBehaviour" to { onj, actor -> MouseHoverBehaviour(onj, actor) },
        "OnClickExitBehaviour" to { _, actor -> OnClickExitBehaviour(actor) },
        "OnHoverChangeSizeBehaviour" to { onj, actor -> OnHoverChangeSizeBehaviour(onj, actor) },
        "OnClickChangeScreenBehaviour" to { onj, actor -> OnClickChangeScreenBehaviour(onj, actor) },
        "OnClickShootBehaviour" to { onj, actor -> OnClickShootBehaviour(onj, actor) },
        "OnClickParticleEffectBehaviour" to { onj, actor -> OnClickParticleEffectBehaviour(onj, actor) },
        "OnClickChangePostProcessorBehaviour" to { onj, actor -> OnClickChangePostProcessorBehaviour(onj, actor) },
        "OnHoverPopupBehaviour" to { onj, actor -> OnHoverPopupBehaviour(onj, actor) }
    )

    fun behaviorOrError(name: String, onj: OnjNamedObject, actor: Actor): Behaviour {
        val behaviourCreator = behaviours[name] ?: throw RuntimeException("Unknown behaviour: $name")
        return behaviourCreator(onj, actor)
    }

}

abstract class Behaviour(val actor: Actor) {

    lateinit var screenDataProvider: ScreenDataProvider

    protected open val onHoverEnter: BehaviourCallback? = null
    protected open val onHoverExit: BehaviourCallback? = null
    protected open val onCLick: BehaviourCallback? = null

    fun bindCallbacks(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
        onHoverEnter?.let { actor.onEnter(it) }
        onHoverExit?.let { actor.onExit(it) }
        onCLick?.let { actor.onClick(it) }
    }

}


class MouseHoverBehaviour(
    onj: OnjNamedObject,
    actor: Actor
) : Behaviour(actor) {

    private val cursorName = onj.get<String>("cursorName")
    private val useSystemCursor = onj.get<Boolean>("useSystemCursor")

    private val cursor: Either<Cursor, SystemCursor> by lazy {
        loadCursor(useSystemCursor, cursorName, screenDataProvider)
    }

    override val onHoverEnter: BehaviourCallback = {
        Utils.setCursor(cursor)
    }

    override val onHoverExit: BehaviourCallback = {
        Utils.setCursor(screenDataProvider.defaultCursor)
    }

    companion object {

        fun loadCursor(
            useSystemCursor: Boolean,
            cursorName: String,
            screenDataProvider: ScreenDataProvider
        ): Either<Cursor, SystemCursor> {

            if (useSystemCursor) {

                return when (cursorName) {

                    "hand" -> SystemCursor.Hand
                    "arrow" -> SystemCursor.Arrow
                    "ibeam" -> SystemCursor.Ibeam
                    "crosshair" -> SystemCursor.Crosshair
                    "horizontal resize" -> SystemCursor.HorizontalResize
                    "vertical resize" -> SystemCursor.VerticalResize
                    "nw se resize" -> SystemCursor.NWSEResize
                    "ne sw resize" -> SystemCursor.NESWResize
                    "all resize" -> SystemCursor.AllResize
                    "not allowed" -> SystemCursor.NotAllowed
                    "none" -> SystemCursor.None
                    else -> throw RuntimeException("unknown system cursor: $cursorName")

                }.eitherRight()

            } else {
                return (screenDataProvider.cursors[cursorName] ?: run {
                    throw RuntimeException("unknown custom cursor: $cursorName")
                }).eitherLeft()
            }
        }

    }

}

class OnClickExitBehaviour(actor: Actor) : Behaviour(actor) {

    override val onCLick: BehaviourCallback = {
        Gdx.app.exit()
    }
}

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

class OnClickChangeScreenBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val screenPath = onj.get<String>("screenPath")

    override val onCLick: BehaviourCallback = {
        TestGame.curScreen = ScreenBuilderFromOnj(Gdx.files.internal(screenPath)).build()
    }
}

class OnClickShootBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val maskTextureName = onj.get<String>("maskTexture")
    private val invert = onj.getOr("invert", false)
    private val maskScaleX = onj.getOr("maskScaleX", 1.0).toFloat()
    private val maskScaleY = onj.getOr("maskScaleY", 1.0).toFloat()
    private val maskOffsetX = onj.getOr("maskOffsetX", 0.0).toFloat()
    private val maskOffsetY = onj.getOr("maskOffsetY", 0.0).toFloat()

    init {
        if (actor !is Maskable) throw RuntimeException("OnClickShootBehaviour can only be used on a maskable actor")
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

class OnClickParticleEffectBehaviour(onj: OnjNamedObject, actor: Actor) : Behaviour(actor) {

    private val particlePath = onj.get<String>("file")
    private val textureDir = onj.get<String>("textureDir")

    private val effectScale = onj.getOr("effectScale", 1.0).toFloat()
    private val useCursorPosition = onj.getOr("useCursorPos", false)

    override val onCLick: BehaviourCallback = {
        val particleActor =

            object : ParticleEffectActor(Gdx.files.internal(particlePath), Gdx.files.internal(textureDir)) {

                override fun remove(): Boolean {
                    // Why does ParticleActor not do this automatically?
                    this.dispose()
                    return super.remove()
                }

            }

        particleActor.isAutoRemove = true
        screenDataProvider.stage.addActor(particleActor)

        if (useCursorPosition) {
            val cursorPos = Utils.getCursorPos(screenDataProvider.stage.viewport)
            particleActor.setPosition(cursorPos.x, cursorPos.y)
        } else {
            particleActor.setPosition(actor.x + actor.width / 2, actor.y + actor.height / 2)
        }

        particleActor.effect.scaleEffect(effectScale)
        particleActor.start()
    }

}

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

typealias BehaviourCreator = (onj: OnjNamedObject, actor: Actor) -> Behaviour
typealias BehaviourCallback = Actor.() -> Unit
