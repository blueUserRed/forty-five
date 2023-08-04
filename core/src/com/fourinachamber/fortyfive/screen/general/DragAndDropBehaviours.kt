package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.game.card.*
import com.fourinachamber.fortyfive.map.shop.ShopDragSource
import com.fourinachamber.fortyfive.map.shop.ShopDropTarget
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


abstract class DragBehaviour(
    protected val dragAndDrop: DragAndDrop,
//    protected val onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DragAndDrop.Source(actor)

@Suppress("unused", "UNUSED_PARAMETER") // may be necessary in the future, also for symmetry with DragBehaviour
abstract class DropBehaviour(
    protected val dragAndDrop: DragAndDrop,
//    protected val onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DragAndDrop.Target(actor)

/**
 * drags the object in the center
 */
abstract class CenterDragged(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : DragBehaviour(dragAndDrop, actor, onj) {

    override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        super.drag(event, x, y, pointer)
        val parentOff = actor.parent.localToStageCoordinates(Vector2(0f, 0f))
        dragAndDrop.setDragActorPosition(
            -parentOff.x + actor.width / 2,
            -parentOff.y - actor.height / 2
        )
        //if there are any errors, it might be because of scaling //see files at commit 17278a0ddd6f821358af53ba331443958292d872
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
        val obj = payload.obj as ExecutionPayload
        obj.onDragStop()
    }
}

open class ExecutionPayload { //TODO maybe interface, but idk how to set "tasks" default value to mutableListOf()

    /**
     * get executed on DragStop
     */
    val tasks: MutableList<() -> Unit> = mutableListOf()

    /**
     * called when the drag is stopped
     */
    fun onDragStop() {
        for (task in tasks) task()
    }

    /**
     * when the drag is stopped, the actor will be reset to [pos]
     */
    fun resetTo(actor: Actor, pos: Vector2) = tasks.add {
        actor.setPosition(pos.x, pos.y)
    }
}



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

