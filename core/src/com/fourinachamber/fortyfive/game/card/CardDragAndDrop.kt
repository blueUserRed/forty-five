package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.map.shop.ShopScreenController
import com.fourinachamber.fortyfive.screen.gameComponents.RevolverSlot
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.obj
import onj.value.OnjNamedObject
import kotlin.math.max

/**
 * drags the object in the center
 */
open class CenterDragged(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : DragBehaviour(dragAndDrop, actor, onj) {
    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): Payload? {
        return null
    }

    override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        super.drag(event, x, y, pointer)
        val parentOff = actor.parent.localToStageCoordinates(Vector2(0f, 0f))
        dragAndDrop.setDragActorPosition(
            -parentOff.x + actor.width / 2,
            -parentOff.y - actor.height / 2
        )
        //if there are any errors, it might be because of scaling //see files before commit 17278a0ddd6f821358af53ba331443958292d872
    }

    override fun dragStop(
        event: InputEvent?,
        x: Float,
        y: Float,
        pointer: Int,
        payload: Payload?,
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


/**
 * the DragSource used for dragging a card to the revolver
 */
open class CardDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : CenterDragged(dragAndDrop, actor, onj) {

    private val card: Card
    private val toLast: Boolean

    init {
        if (actor !is CardActor) throw RuntimeException("CardDragSource can only be used on an CardActor")
        card = actor.card
        toLast = onj.getOr("moveToLastIndex", false)
    }

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): Payload? {

        if (!card.isDraggable) return null

        card.actor.isDragged = true
        val payload = Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor

        if (toLast) card.actor.toFront()

        val obj = CardDragAndDropPayload(card)
        payload.obj = obj
        obj.resetTo(card.actor, Vector2(actor.x, actor.y))
        return payload
    }

    override fun dragStop(
        event: InputEvent?,
        x: Float,
        y: Float,
        pointer: Int,
        payload: Payload?,
        target: DragAndDrop.Target?
    ) {
        card.actor.isDragged = false
        super.dragStop(event, x, y, pointer, payload, target)
        if (payload == null) return
        if (toLast) card.actor.zIndex -= 1
    }
}

/**
 * used for dropping a card into the revolver
 */
class RevolverDropTarget(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject
) : DropBehaviour(dragAndDrop, actor, onj) {

    private val revolverSlot: RevolverSlot

    init {
        if (actor !is RevolverSlot) throw RuntimeException("RevolverDropTarget can only be used on a revolverSlot")
        revolverSlot = actor
    }

    override fun drag(
        source: DragAndDrop.Source?,
        payload: Payload?,
        x: Float,
        y: Float,
        pointer: Int
    ): Boolean {
        return true
    }

    override fun drop(source: DragAndDrop.Source?, payload: Payload?, x: Float, y: Float, pointer: Int) {
        if (payload == null || source == null) return

        val obj = payload.obj!! as CardDragAndDropPayload
        obj.loadIntoRevolver(revolverSlot.num)
    }

}

/**
 * used as a payload for [CardDragSource] and [RevolverDropTarget].
 * Automatically resets cards, loads into revolver, etc.
 */
class CardDragAndDropPayload(val card: Card) : ExecutionPayload() {

    /**
     * when the drag is stopped, the card will be loaded into the revolver in [slot]
     */
    fun loadIntoRevolver(slot: Int) = tasks.add {
        FortyFive.currentGame!!.loadBulletInRevolver(card, slot)  //TODO ugly
    }
}

class ShopDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : CenterDragged(dragAndDrop, actor, onj) {

    private val toLast: Boolean

    init {
        toLast = onj.getOr("moveToLastIndex", false)
    }

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): Payload? {
        if ((actor !is CustomImageActor) || (actor as CustomImageActor).inActorState("unbuyable")) return null
        val payload = Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor
        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = actor.parent
        if (toLast) actor.toFront()

        val obj = ShopPayload(actor)
        payload.obj = obj
        obj.resetTo(actor,Vector2(actor.x, actor.y))
        return payload
    }

    override fun dragStop(
        event: InputEvent?,
        x: Float,
        y: Float,
        pointer: Int,
        payload: Payload?,
        target: DragAndDrop.Target?
    ) {
        if (payload == null) return
        if (toLast) actor.zIndex = max(actor.zIndex - 1, 0)
        val obj = payload.obj as ShopPayload
        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = null
        obj.onDragStop()
    }
    class ShopPayload(val actor: Actor) : ExecutionPayload() {

        /**
         * called when the drag is stopped
         */
        fun onBuy() = tasks.add {
            val scr = (FortyFive.screen as OnjScreen).screenController as ShopScreenController
            scr.buyCard(actor)
        }
    }
}