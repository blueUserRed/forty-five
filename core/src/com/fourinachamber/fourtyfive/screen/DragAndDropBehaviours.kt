package com.fourinachamber.fourtyfive.screen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.card.CardDragSource
import com.fourinachamber.fourtyfive.card.CoverAreaDropTarget
import com.fourinachamber.fourtyfive.card.RevolverDropTarget
import com.fourinachamber.fourtyfive.utils.Either
import com.fourinachamber.fourtyfive.utils.eitherLeft
import com.fourinachamber.fourtyfive.utils.eitherRight
import onj.value.OnjNamedObject

object DragAndDropBehaviourFactory {

    private val dragBehaviours: MutableMap<String, DragBehaviourCreator> = mutableMapOf()
    private val dropBehaviours: MutableMap<String, DropBehaviourCreator> = mutableMapOf()

    init {
        dragBehaviours["SimpleDragSource"] = { dragAndDrop, onjScreen, actor, onj ->
            SimpleDragSource(dragAndDrop, onjScreen, actor, onj)
        }
        dropBehaviours["TestDropTarget"] = { dragAndDrop, onjScreen, actor, onj ->
            TestDropTarget(dragAndDrop, onjScreen, actor, onj)
        }
        dragBehaviours["SlotDragSource"] = { dragAndDrop, onjScreen, actor, onj ->
            SlotDragSource(dragAndDrop, onjScreen, actor, onj)
        }
        dropBehaviours["SlotDropTarget"] = { dragAndDrop, onjScreen, actor, onj ->
            SlotDropTarget(dragAndDrop, onjScreen, actor, onj)
        }
        dragBehaviours["CardDragSource"] = { dragAndDrop, onjScreen, actor, onj ->
            CardDragSource(dragAndDrop, onjScreen, actor, onj)
        }
        dropBehaviours["RevolverDropTarget"] = { dragAndDrop, onjScreen, actor, onj ->
            RevolverDropTarget(dragAndDrop, onjScreen, actor, onj)
        }
        dropBehaviours["CoverAreaDropTarget"] = { dragAndDrop, onjScreen, actor, onj ->
            CoverAreaDropTarget(dragAndDrop, onjScreen, actor, onj)
        }
    }

    fun dragBehaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        onjScreen: OnjScreen,
        actor: Actor,
        onj: OnjNamedObject
    ): DragBehaviour {
        return dragBehaviourOrNull(name, dragAndDrop, onjScreen, actor, onj) ?: run {
            throw RuntimeException("unknown drag behaviour $name")
        }
    }

    private fun dragBehaviourOrNull(
        name: String,
        dragAndDrop: DragAndDrop,
        onjScreen: OnjScreen,
        actor: Actor,
        onj: OnjNamedObject
    ): DragBehaviour? = dragBehaviours[name]?.invoke(dragAndDrop, onjScreen, actor, onj)

    fun dropBehaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        onjScreen: OnjScreen,
        actor: Actor,
        onj: OnjNamedObject
    ): DropBehaviour {
        return dropBehaviourOrNull(name, dragAndDrop, onjScreen, actor, onj) ?: run {
            throw RuntimeException("unknown drag behaviour $name")
        }
    }

    private fun dropBehaviourOrNull(
        name: String,
        dragAndDrop: DragAndDrop,
        onjScreen: OnjScreen,
        actor: Actor,
        onj: OnjNamedObject
    ): DropBehaviour? = dropBehaviours[name]?.invoke(dragAndDrop, onjScreen, actor, onj)

    fun behaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        onjScreen: OnjScreen,
        actor: Actor,
        onj: OnjNamedObject
    ): Either<DragBehaviour, DropBehaviour> {
        return dragBehaviourOrNull(name, dragAndDrop, onjScreen, actor, onj)?.eitherLeft() ?:
               dropBehaviourOrNull(name, dragAndDrop, onjScreen, actor, onj)?.eitherRight() ?:
               throw RuntimeException("Unknown drag or drop behaviour: $name")
    }

}

abstract class DragBehaviour(
    protected val dragAndDrop: DragAndDrop,
    protected val onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DragAndDrop.Source(actor)

abstract class DropBehaviour(
    protected val dragAndDrop: DragAndDrop,
    protected val onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DragAndDrop.Target(actor)


class SimpleDragSource(
    dragAndDrop: DragAndDrop,
    onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DragBehaviour(dragAndDrop, onjScreen, actor, onj) {

    private val dimensionsCell: Cell<*>? = if (onj.hasKey<String>("useDimensionsOfCell")) {
        onjScreen.namedCellOrError(onj.get<String>("useDimensionsOfCell"))
    } else {
        null
    }

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload {
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor

        payload.setObject(mutableMapOf(
            "resetPosition" to (actor.x to actor.y)
        ))

        val width = dimensionsCell?.actorWidth ?: payload.dragActor.width
        val height = dimensionsCell?.actorHeight ?: payload.dragActor.height
        dragAndDrop.setDragActorPosition(width / 2, -height / 2)
        return payload
    }

    override fun dragStop(
        event: InputEvent?,
        x: Float,
        y: Float,
        pointer: Int,
        payload: DragAndDrop.Payload?,
        target: DragAndDrop.Target?
    ) {
        if (payload == null) return
        val map = payload.`object` as Map<*, *>

        val (actorX, actorY) = (map["resetPosition"] ?: return) as Pair<*, *>
        actor.setPosition(actorX as Float, actorY as Float)
    }

}

class TestDropTarget(
    dragAndDrop: DragAndDrop,
    onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DropBehaviour(dragAndDrop, onjScreen, actor, onj) {

    override fun drag(
        source: DragAndDrop.Source?,
        payload: DragAndDrop.Payload?,
        x: Float,
        y: Float,
        pointer: Int
    ): Boolean {
        actor.color = Color.GREEN
        return true
    }

    override fun drop(source: DragAndDrop.Source?, payload: DragAndDrop.Payload?, x: Float, y: Float, pointer: Int) {
    }

}

class SlotDragSource(
    dragAndDrop: DragAndDrop,
    onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DragBehaviour(dragAndDrop, onjScreen, actor, onj) {

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload {
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor

        payload.setObject(mutableMapOf(
            "resetPosition" to (actor.x to actor.y)
        ))

        dragAndDrop.setDragActorPosition(
            actor.width - (actor.width * actor.scaleX / 2),
            -(actor.height * actor.scaleY) / 2
//            (actor.width * actor.scaleX) / 2,
//            -(actor.height * actor.scaleY) / 2
        )
        return payload
    }

    override fun dragStop(
        event: InputEvent?,
        x: Float,
        y: Float,
        pointer: Int,
        payload: DragAndDrop.Payload?,
        target: DragAndDrop.Target?
    ) {
        if (payload == null) return
        val map = payload.`object` as Map<*, *>

        val (actorX, actorY) = (map["resetPosition"] ?: return) as Pair<*, *>
        actor.setPosition(actorX as Float, actorY as Float)
    }

}

class SlotDropTarget(
    dragAndDrop: DragAndDrop,
    onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DropBehaviour(dragAndDrop, onjScreen, actor, onj) {

    override fun drag(
        source: DragAndDrop.Source?,
        payload: DragAndDrop.Payload?,
        x: Float,
        y: Float,
        pointer: Int
    ): Boolean {
        return true
    }

    override fun drop(source: DragAndDrop.Source?, payload: DragAndDrop.Payload?, x: Float, y: Float, pointer: Int) {
        if (payload == null || source == null) return
        @Suppress("UNCHECKED_CAST")
        val obj = payload.`object`!! as MutableMap<String, Any?>
        val posX = actor.x + actor.width / 2 - source.actor.width / 2
        val posY = actor.y + actor.height / 2 - source.actor.height / 2
        obj["resetPosition"] = posX to posY
    }

}

typealias DragBehaviourCreator = (DragAndDrop, OnjScreen, Actor, OnjNamedObject) -> DragBehaviour
typealias DropBehaviourCreator = (DragAndDrop, OnjScreen, Actor, OnjNamedObject) -> DropBehaviour
