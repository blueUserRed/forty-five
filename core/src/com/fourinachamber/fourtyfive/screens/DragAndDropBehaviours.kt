package com.fourinachamber.fourtyfive.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.cards.Card
import com.fourinachamber.fourtyfive.utils.Either
import com.fourinachamber.fourtyfive.utils.eitherLeft
import com.fourinachamber.fourtyfive.utils.eitherRight
import onj.OnjNamedObject

object DragAndDropBehaviourFactory {

    private val dragBehaviours: MutableMap<String, DragBehaviourCreator> = mutableMapOf()
    private val dropBehaviours: MutableMap<String, DropBehaviourCreator> = mutableMapOf()

    init {
        dragBehaviours["SimpleDragSource"] = { dragAndDrop, screenDataProvider, actor, onj ->
            SimpleDragSource(dragAndDrop, screenDataProvider, actor, onj)
        }
        dropBehaviours["TestDropTarget"] = { dragAndDrop, screenDataProvider, actor, onj ->
            TestDropTarget(dragAndDrop, screenDataProvider, actor, onj)
        }
        dragBehaviours["SlotDragSource"] = { dragAndDrop, screenDataProvider, actor, onj ->
            SlotDragSource(dragAndDrop, screenDataProvider, actor, onj)
        }
        dropBehaviours["SlotDropTarget"] = { dragAndDrop, screenDataProvider, actor, onj ->
            SlotDropTarget(dragAndDrop, screenDataProvider, actor, onj)
        }
        dragBehaviours["CardDragSource"] = { dragAndDrop, screenDataProvider, actor, onj ->
            CardDragSource(dragAndDrop, screenDataProvider, actor, onj)
        }
    }

    fun dragBehaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        screenDataProvider: ScreenDataProvider,
        actor: Actor,
        onj: OnjNamedObject
    ): DragBehaviour {
        return dragBehaviourOrNull(name, dragAndDrop, screenDataProvider, actor, onj) ?: run {
            throw RuntimeException("unknown drag behaviour $name")
        }
    }

    private fun dragBehaviourOrNull(
        name: String,
        dragAndDrop: DragAndDrop,
        screenDataProvider: ScreenDataProvider,
        actor: Actor,
        onj: OnjNamedObject
    ): DragBehaviour? = dragBehaviours[name]?.invoke(dragAndDrop, screenDataProvider, actor, onj)

    fun dropBehaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        screenDataProvider: ScreenDataProvider,
        actor: Actor,
        onj: OnjNamedObject
    ): DropBehaviour {
        return dropBehaviourOrNull(name, dragAndDrop, screenDataProvider, actor, onj) ?: run {
            throw RuntimeException("unknown drag behaviour $name")
        }
    }

    private fun dropBehaviourOrNull(
        name: String,
        dragAndDrop: DragAndDrop,
        screenDataProvider: ScreenDataProvider,
        actor: Actor,
        onj: OnjNamedObject
    ): DropBehaviour? = dropBehaviours[name]?.invoke(dragAndDrop, screenDataProvider, actor, onj)

    fun behaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        screenDataProvider: ScreenDataProvider,
        actor: Actor,
        onj: OnjNamedObject
    ): Either<DragBehaviour, DropBehaviour> {
        return dragBehaviourOrNull(name, dragAndDrop, screenDataProvider, actor, onj)?.eitherLeft() ?:
               dropBehaviourOrNull(name, dragAndDrop, screenDataProvider, actor, onj)?.eitherRight() ?:
               throw RuntimeException("Unknown drag or drop behaviour: $name")
    }

}

abstract class DragBehaviour(
    protected val dragAndDrop: DragAndDrop,
    protected val screenDataProvider: ScreenDataProvider,
    actor: Actor,
    onj: OnjNamedObject
) : DragAndDrop.Source(actor)

abstract class DropBehaviour(
    protected val dragAndDrop: DragAndDrop,
    protected val screenDataProvider: ScreenDataProvider,
    actor: Actor,
    onj: OnjNamedObject
) : DragAndDrop.Target(actor)


class SimpleDragSource(
    dragAndDrop: DragAndDrop,
    screenDataProvider: ScreenDataProvider,
    actor: Actor,
    onj: OnjNamedObject
) : DragBehaviour(dragAndDrop, screenDataProvider, actor, onj) {

    private val dimensionsCell: Cell<*>? = if (onj.hasKey<String>("useDimensionsOfCell")) {
        screenDataProvider.namedCells[onj.get<String>("useDimensionsOfCell")] ?: run {
            throw RuntimeException("No cell named ${onj.get<String>("useDimensionsOfCell")}")
        }
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
    screenDataProvider: ScreenDataProvider,
    actor: Actor,
    onj: OnjNamedObject
) : DropBehaviour(dragAndDrop, screenDataProvider, actor, onj) {

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
    screenDataProvider: ScreenDataProvider,
    actor: Actor,
    onj: OnjNamedObject
) : DragBehaviour(dragAndDrop, screenDataProvider, actor, onj) {

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
    screenDataProvider: ScreenDataProvider,
    actor: Actor,
    onj: OnjNamedObject
) : DropBehaviour(dragAndDrop, screenDataProvider, actor, onj) {

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


class CardDragSource(
    dragAndDrop: DragAndDrop,
    screenDataProvider: ScreenDataProvider,
    actor: Actor,
    onj: OnjNamedObject
) : DragBehaviour(dragAndDrop, screenDataProvider, actor, onj) {

    private val card: Card

    init {
        if (actor !is CardActor) throw RuntimeException("CardDragSource can only be used on an CardActor")
        card = actor.card
    }

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload {
        card.actor.isDragged = true
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor

        payload.setObject(mutableMapOf(
            "resetPosition" to (actor.x to actor.y)
        ))

        dragAndDrop.setDragActorPosition(
            actor.width - (actor.width * actor.scaleX / 2),
            -(actor.height * actor.scaleY) / 2
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
        card.actor.isDragged = false
        if (payload == null) return
        val map = payload.`object` as Map<*, *>

        val (actorX, actorY) = (map["resetPosition"] ?: return) as Pair<*, *>
        actor.setPosition(actorX as Float, actorY as Float)
    }

}

typealias DragBehaviourCreator = (DragAndDrop, ScreenDataProvider, Actor, OnjNamedObject) -> DragBehaviour
typealias DropBehaviourCreator = (DragAndDrop, ScreenDataProvider, Actor, OnjNamedObject) -> DropBehaviour
