package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWarningParent
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexGroup
import com.fourinachamber.fortyfive.utils.obj
import onj.value.OnjNamedObject
import kotlin.math.max


class BackpackDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : CenteredDragSource(dragAndDrop, actor, onj, true) {
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

        curFlex.fixedZIndex = 10
        (curFlex.parent as ZIndexGroup).resortZIndices()
        obj.resetZIndex(curFlex)
        startReal()

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
        actor.parent.parent.parent.zIndex = max(actor.parent.parent.parent.zIndex - 1, 0)
        obj.invalidateParents(actor)
        obj.onDragStop()
    }

    override fun fakeStart(event: InputEvent?, x: Float, y: Float, pointer: Int): Boolean {
        if ((actor !is CustomFlexBox)) return false
        val curFlex = actor.parent.parent as CustomScrollableFlexBox
        curFlex.currentlyDraggedChild = actor
        actor.parent.parent.parent.toFront() //the side which say if its backpack or deck
        curFlex.fixedZIndex = 10
        (curFlex.parent as ZIndexGroup).resortZIndices()
        return true
    }

    override fun fakeStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        actor.zIndex = max(actor.zIndex - 1, 0)
        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = null
        actor.parent.parent.parent.zIndex = max(actor.parent.parent.parent.zIndex - 1, 0)
        (actor.parent.parent.parent.parent as ZIndexGroup).resortZIndices()
    }
}

class BackpackDragPayload(val actor: Actor) : ExecutionPayload() {
    fun switchOrPlaceCard(card: CustomFlexBox, slot: CustomFlexBox) {
        val dataSource = card.name.split(Backpack.NAME_SEPARATOR_STRING)
        val sourceIndex = dataSource[2].toInt()
        val targetIndex = slot.parent.children.indexOf(slot)
        val curDeck = SaveState.curDeck
        if (dataSource[1] == "deck") curDeck.swapCards(sourceIndex, targetIndex)
        else if (dataSource[1] == "backpack") curDeck.addToDeck(targetIndex, dataSource[0])
    }

    fun backToBackpack(card: CustomFlexBox) {
        val fromDeck = card.name.split(Backpack.NAME_SEPARATOR_STRING)[1] == "deck"
        if (fromDeck) {
            if (SaveState.curDeck.canRemoveCards()) {
                SaveState.curDeck.removeFromDeck(card.parent.parent.children.indexOf(card.parent))
            } else {
                CustomWarningParent.getWarning(card.screen).addPermanentWarning(
                    card.screen,
                    "Not enough cards",
                    "The minimum decksize is ${SaveState.Deck.minDeckSize}. Since you only have ${SaveState.curDeck.cardPositions.size} cards in your Deck, you can't remove a card.",
                    CustomWarningParent.Severity.MIDDLE
                )
            }
        }
    }

    fun invalidateParents(card: Actor) {
        (card as CustomFlexBox).invalidateHierarchy()
    }

    fun resetZIndex(curFlex: CustomScrollableFlexBox) = tasks.add {
        curFlex.fixedZIndex = 0
        (curFlex.parent as ZIndexGroup).resortZIndices()
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
