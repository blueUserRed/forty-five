package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.map.shop.ShopScreenController
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.obj
import onj.value.OnjNamedObject
import kotlin.math.max

class BackpackDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : CenterDragged(dragAndDrop, actor, onj) {

    private val toLast: Boolean

    init {
        toLast = onj.getOr("moveToLastIndex", false)
    }

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload? {
        if ((actor !is CustomImageActor) || (actor as CustomImageActor).inActorState("unbuyable")) return null
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor
        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = actor.parent
        if (toLast) actor.toFront()

        val obj = ShopDragPayload(actor)
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
        if (toLast) actor.zIndex = max(actor.zIndex - 1, 0)
        val obj = payload.obj as ShopDragPayload
        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = null
        obj.onDragStop()
    }
}

class ShopDragPayload(val actor: Actor) : ExecutionPayload() {
    /**
     * called when the drag is stopped
     */
    fun onBuy() = tasks.add {
        val scr = (FortyFive.screen as OnjScreen).screenController as ShopScreenController
        scr.buyCard(actor)
    }
}