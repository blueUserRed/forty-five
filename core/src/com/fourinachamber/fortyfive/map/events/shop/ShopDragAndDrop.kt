package com.fourinachamber.fortyfive.map.events.shop

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.obj
import onj.value.OnjNamedObject
import kotlin.math.max


class ShopDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : CenteredDragSource(dragAndDrop, actor, onj, true) {

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload? {
        val actor = this.actor
        if ((actor !is CardActor) || actor.inActorState("unbuyable")) return null
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor
        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = actor.parent
        actor.toFront()

        val obj = ShopDragPayload(actor)
        payload.obj = obj
        obj.resetTo(actor, Vector2(actor.x, actor.y))
        val controller = ((FortyFive.screen as OnjScreen).screenController as ShopScreenController)
        controller.displayBuyPopups()
        obj.closePopups(controller)
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
        println("now stopped")
        if (payload == null) return
        actor.zIndex = max(actor.zIndex - 1, 0)
        val obj = payload.obj as ShopDragPayload
        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = null
        obj.onDragStop()
    }

    override fun fakeStart(event: InputEvent?, x: Float, y: Float, pointer: Int): Boolean {
        val actor = this.actor
        if ((actor !is CardActor) || actor.inActorState("unbuyable")) return false
        dragAndDrop.setKeepWithinStage(false)

        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = actor
        actor.toFront()
        val controller = ((FortyFive.screen as OnjScreen).screenController as ShopScreenController)
        controller.displayBuyPopups()
        return true
    }

    override fun fakeStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        val controller = ((FortyFive.screen as OnjScreen).screenController as ShopScreenController)
        controller.closeBuyPopups()
        val tempParent = actor.parent.parent
        if (tempParent is CustomScrollableFlexBox) tempParent.currentlyDraggedChild = null
    }
}

class ShopDragPayload(val actor: Actor) : ExecutionPayload() {
    /**
     * called when the drag is stopped
     */
    fun onBuy(addToDeck: Boolean) = tasks.add {
        val scr = (FortyFive.screen as OnjScreen).screenController as ShopScreenController
        scr.buyCard(actor, addToDeck)
    }

    fun closePopups(controller: ShopScreenController) = tasks.add {
        controller.closeBuyPopups()
    }
}

class ShopDropTarget(dragAndDrop: DragAndDrop, actor: Actor, onj: OnjNamedObject) :
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
        val obj = payload.obj as ShopDragPayload
        obj.onBuy(isToDeck)
    }
}


