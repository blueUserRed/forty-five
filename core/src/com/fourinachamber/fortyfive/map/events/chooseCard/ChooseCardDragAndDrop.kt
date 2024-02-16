package com.fourinachamber.fortyfive.map.events.chooseCard


import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.utils.obj
import onj.value.OnjNamedObject
import kotlin.math.max


class ChooseCardDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : CenteredDragSource(dragAndDrop, actor, onj, true) {

    override fun getActor(): CardActor = super.getActor() as CardActor


    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload {
        val actor = this.actor
        actor.isDragged = true
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)
        payload.dragActor = actor
        actor.toFront()
        val obj = ChooseCardDragPayload(actor)
        payload.obj = obj
        obj.resetTo(actor, Vector2(actor.x, actor.y))
        actor.enterActorState("dragged")
        obj.resetActorState(actor)
        startReal()
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
        actor.isDragged = false
        actor.zIndex = max(actor.zIndex - 1, 0)
        val obj = payload.obj as ChooseCardDragPayload
        obj.onDragStop()
    }

    override fun fakeStart(event: InputEvent?, x: Float, y: Float, pointer: Int): Boolean {
        val actor = this.actor
        dragAndDrop.setKeepWithinStage(false)
        actor.toFront()
        actor.enterActorState("dragged")
        return true
    }

    override fun fakeStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        actor.leaveActorState("dragged")
    }
}

class ChooseCardDragPayload(val actor: Actor) : ExecutionPayload() {
    /**
     * called when the drag is stopped
     */
    fun onDrop(addToDeck: Boolean) = tasks.add {
        val scr = (FortyFive.screen as OnjScreen).screenController as ChooseCardScreenController
        scr.getCard((actor as CardActor).name!!, addToDeck)
    }

    fun resetActorState(actor: CardActor) = tasks.add { actor.leaveActorState("dragged") }
}

class ChooseCardDropTarget(dragAndDrop: DragAndDrop, actor: Actor, onj: OnjNamedObject) :
    DropBehaviour(dragAndDrop, actor, onj) {

    private val isToDeck: Boolean = onj.get<Boolean>("isToDeck")

    override fun getActor(): CustomImageActor = super.getActor() as CustomImageActor

    override fun drag(
        source: DragAndDrop.Source?,
        payload: DragAndDrop.Payload?,
        x: Float,
        y: Float,
        pointer: Int
    ): Boolean = if (actor.inActorState("disabled")) {
        false
    } else {
        actor.enterActorState("draggedHover")
        true
    }

    override fun reset(source: DragAndDrop.Source?, payload: DragAndDrop.Payload?) =
        actor.leaveActorState("draggedHover")

    override fun drop(source: DragAndDrop.Source?, payload: DragAndDrop.Payload?, x: Float, y: Float, pointer: Int) {
        if (payload == null) return
        val obj = payload.obj as ChooseCardDragPayload
        obj.onDrop(isToDeck)
    }
}


