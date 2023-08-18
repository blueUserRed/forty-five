package com.fourinachamber.fortyfive.map.events.chooseCard


import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.obj
import onj.value.OnjNamedObject
import kotlin.math.max


class ChooseCardDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : CenteredDragSource(dragAndDrop, actor, onj) {

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload? {
        val actor = this.actor
        if ((actor !is CustomImageActor)) return null
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)
        payload.dragActor = actor
        actor.toFront()
        val obj = ChooseCardDragPayload(actor)
        payload.obj = obj
        obj.resetTo(actor, Vector2(actor.x, actor.y))
        return payload
    }

    override fun dragStop(
        event: InputEvent?,
        x: Float,
        y: Float,
        pointer: Int,
        payload: DragAndDrop.Payload?,
        target: DragAndDrop.Target?,
    ) {
        if (payload == null) return
        actor.zIndex = max(actor.zIndex - 1, 0)
        val obj = payload.obj as ChooseCardDragPayload
        obj.onDragStop()
    }
}

class ChooseCardDragPayload(val actor: Actor) : ExecutionPayload() {
    /**
     * called when the drag is stopped
     */
    fun onBuy(addToDeck: Boolean) = tasks.add {
        val scr = (FortyFive.screen as OnjScreen).screenController as ChooseCardScreenController
        scr.getCard((actor as CustomImageActor).name!!, addToDeck)
    }
}

class ChooseCardDropTarget(dragAndDrop: DragAndDrop, actor: Actor, onj: OnjNamedObject) :
    DropBehaviour(dragAndDrop, actor, onj) {

    private val isToDeck: Boolean = onj.get<Boolean>("isToDeck")

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
        val obj = payload.obj as ChooseCardDragPayload
        val actor = this.actor
        if (actor is CustomImageActor && !actor.inActorState("disabled")) obj.onBuy(isToDeck)
    }
}


