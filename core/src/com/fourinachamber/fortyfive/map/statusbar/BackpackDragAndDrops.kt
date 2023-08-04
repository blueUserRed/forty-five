package com.fourinachamber.fortyfive.map.Backpack

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.obj
import onj.value.OnjNamedObject
import kotlin.math.max


class BackpackDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : CenterDragged(dragAndDrop, actor, onj) {
    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload? {
        val actor = this.actor
        if ((actor !is CustomFlexBox)) return null
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)
        payload.dragActor = actor
        val curFlex = actor.parent.parent as CustomScrollableFlexBox
        curFlex.currentlyDraggedChild = actor
        actor.fixedZIndex = 10
        curFlex.resortZIndices()

        val obj = BackpackDragPayload(actor)
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
        target: DragAndDrop.Target?
    ) {
        if (payload == null) return
        actor.zIndex = max(actor.zIndex - 1, 0)
        val obj = payload.obj as BackpackDragPayload
        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = null
        obj.onDragStop()
    }
}

class BackpackDragPayload(val actor: Actor) : ExecutionPayload() {
    fun cardsPlacedOn(card: CustomFlexBox, slot: CustomFlexBox) {
        println("now to deck from: ${card.parent.parent.children.indexOf(card.parent)}")
//        println("now to deck to:   ${slot.parent.parent.children.indexOf(card.parent)}")
        println()
    }

    fun backToBackpack(card: CustomFlexBox) {
        println("now to backpack: ${card.parent.children.indexOf(card.parent)}")
        println()
    }
}

class DeckSlotDropTarget(dragAndDrop: DragAndDrop, actor: Actor, onj: OnjNamedObject) :
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
        if (payload == null || source == null) return
        val obj = payload.obj as BackpackDragPayload
        obj.cardsPlacedOn(source.actor as CustomFlexBox, actor as CustomFlexBox)
    }
}


class BackpackDropTarget(dragAndDrop: DragAndDrop, actor: Actor, onj: OnjNamedObject) :
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
        if (payload == null || source == null) return
        val obj = payload.obj as BackpackDragPayload
        obj.backToBackpack(source.actor as CustomFlexBox)
    }
}
