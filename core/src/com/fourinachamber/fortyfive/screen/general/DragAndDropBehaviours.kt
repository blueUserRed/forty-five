package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.game.card.*
import com.fourinachamber.fortyfive.utils.Either
import com.fourinachamber.fortyfive.utils.eitherLeft
import com.fourinachamber.fortyfive.utils.eitherRight
import com.fourinachamber.fortyfive.utils.obj
import onj.value.OnjNamedObject

object DragAndDropBehaviourFactory {

    private val dragBehaviours: MutableMap<String, DragBehaviourCreator> = mutableMapOf()
    private val dropBehaviours: MutableMap<String, DropBehaviourCreator> = mutableMapOf()

    init {
        dragBehaviours["SlotDragSource"] = { dragAndDrop, actor, onj ->
            SlotDragSource(dragAndDrop, actor, onj)
        }
        dropBehaviours["SlotDropTarget"] = { dragAndDrop, actor, onj ->
            SlotDropTarget(dragAndDrop, actor, onj)
        }
        dragBehaviours["CardDragSource"] = { dragAndDrop, actor, onj ->
            CardDragSource(dragAndDrop, actor, onj)
        }
        dropBehaviours["RevolverDropTarget"] = { dragAndDrop, actor, onj ->
            RevolverDropTarget(dragAndDrop, actor, onj)
        }
        dropBehaviours["ShopDropTarget"] = { dragAndDrop, actor, onj ->
            ShopDropTarget(dragAndDrop, actor, onj)
        }
        dragBehaviours["ShopDragSource"] = { dragAndDrop, actor, onj ->
            ShopDragSource(dragAndDrop, actor, onj)
        }
//        dragBehaviours["BackpackDragSource"] = { dragAndDrop, actor, onj ->
//            BackpackDragSource(dragAndDrop, actor, onj)
//        }
    }

    fun dragBehaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        actor: Actor,
        onj: OnjNamedObject
    ): DragBehaviour {
        return dragBehaviourOrNull(name, dragAndDrop, actor, onj) ?: run {
            throw RuntimeException("unknown drag behaviour $name")
        }
    }

    private fun dragBehaviourOrNull(
        name: String,
        dragAndDrop: DragAndDrop,
        actor: Actor,
        onj: OnjNamedObject
    ): DragBehaviour? = dragBehaviours[name]?.invoke(dragAndDrop, actor, onj)

    fun dropBehaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        actor: Actor,
        onj: OnjNamedObject
    ): DropBehaviour {
        return dropBehaviourOrNull(name, dragAndDrop, actor, onj) ?: run {
            throw RuntimeException("unknown drag behaviour $name")
        }
    }

    private fun dropBehaviourOrNull(
        name: String,
        dragAndDrop: DragAndDrop,
        actor: Actor,
        onj: OnjNamedObject
    ): DropBehaviour? = dropBehaviours[name]?.invoke(dragAndDrop, actor, onj)

    fun behaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        onjScreen: OnjScreen,
        actor: Actor,
        onj: OnjNamedObject
    ): Either<DragBehaviour, DropBehaviour> {
        return dragBehaviourOrNull(name, dragAndDrop, actor, onj)?.eitherLeft() ?: dropBehaviourOrNull(
            name,
            dragAndDrop,
            actor,
            onj
        )?.eitherRight() ?: throw RuntimeException("Unknown drag or drop behaviour: $name")
    }
}

class ShopDropTarget(dragAndDrop: DragAndDrop, actor: Actor, onj: OnjNamedObject) :
    DropBehaviour(dragAndDrop, actor, onj) {
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
        if (payload == null) return
        val obj = payload.obj as ShopDragSource.ShopPayload
        obj.onBuy()
    }
}

abstract class DragBehaviour(
    protected val dragAndDrop: DragAndDrop,
//    protected val onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DragAndDrop.Source(actor)

abstract class DropBehaviour(
    @Suppress("unused") // may be necessary in the future, also for symmetry with DragBehaviour
    protected val dragAndDrop: DragAndDrop,
//    protected val onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DragAndDrop.Target(actor)

class SlotDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject
) : DragBehaviour(dragAndDrop, actor, onj) {

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload {
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor

        payload.setObject(
            mutableMapOf(
                "resetPosition" to (actor.x to actor.y)
            )
        )

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
        if (payload == null) return
        val map = payload.`object` as Map<*, *>

        val (actorX, actorY) = (map["resetPosition"] ?: return) as Pair<*, *>
        actor.setPosition(actorX as Float, actorY as Float)
    }

}

class SlotDropTarget(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject
) : DropBehaviour(dragAndDrop, actor, onj) {

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

typealias DragBehaviourCreator = (DragAndDrop, Actor, OnjNamedObject) -> DragBehaviour
typealias DropBehaviourCreator = (DragAndDrop, Actor, OnjNamedObject) -> DropBehaviour
