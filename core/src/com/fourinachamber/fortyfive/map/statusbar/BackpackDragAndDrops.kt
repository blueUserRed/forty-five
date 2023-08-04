package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.game.SaveState
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
        if ((actor !is CustomFlexBox)) return null
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)
        payload.dragActor = actor
        val curFlex = actor.parent.parent as CustomScrollableFlexBox
        curFlex.currentlyDraggedChild = actor

        actor.parent.parent.parent.toFront() //the side which say if its backpack or deck

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
        actor.parent.parent.parent.zIndex -= 1
        obj.invalidateParents(actor)
        obj.onDragStop()
    }
}

class BackpackDragPayload(val actor: Actor) : ExecutionPayload() {
    fun switchOrPlaceCard(card: CustomFlexBox, slot: CustomFlexBox) {
        val dataSource = card.name.split(Backpack.nameSeparatorStr)
        val sourceIndex = dataSource[2].toInt()
        val targetIndex = slot.parent.children.indexOf(slot)
        val curDeck = SaveState.curDeck
        if (dataSource[1] == "deck") curDeck.swapCards(sourceIndex, targetIndex)
        else if (dataSource[1] == "backpack") curDeck.addToDeck(targetIndex, dataSource[0])
    }

    fun backToBackpack(card: CustomFlexBox) {
        if (SaveState.curDeck.cards.size > Backpack.minDeckSize) {
            val fromDeck = card.name.split(Backpack.nameSeparatorStr)[1] == "deck"
            if (fromDeck) SaveState.curDeck.removeFromDeck(card.parent.parent.children.indexOf(card.parent))
        }else{
            println("Deck size (${SaveState.curDeck.cardPositions.size} cards) is too small, a min. of ${Backpack.minDeckSize+1} is required to remove cards!")
        }
    }

    fun invalidateParents(card: Actor) {
        (card.parent.parent.parent.parent as Backpack).invalidateHierarchy()
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
        obj.switchOrPlaceCard(source.actor as CustomFlexBox, actor as CustomFlexBox)
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
