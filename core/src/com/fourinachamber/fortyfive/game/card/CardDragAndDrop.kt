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
 * the DragSource used for dragging a card to the revolver
 */
open class CardDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : DragBehaviour(dragAndDrop, actor, onj) {

    private val card: Card
    private val toLast: Boolean

    init {
        if (actor !is CardActor) throw RuntimeException("CardDragSource can only be used on an CardActor")
        card = actor.card
        toLast = onj.getOr("moveToLastIndex", false)
    }

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload? {

        if (!card.isDraggable) return null

        card.actor.isDragged = true
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor

        if (toLast) card.actor.toFront()

        val obj = CardDragAndDropPayload(card)
        payload.obj = obj
        obj.resetTo(Vector2(actor.x, actor.y))
        return payload
    }

    override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        super.drag(event, x, y, pointer)
        val parentOff = actor.parent.localToStageCoordinates(Vector2(0f, 0f))
        dragAndDrop.setDragActorPosition(
            -parentOff.x + actor.width - (actor.width * actor.scaleX) / 2,
            -parentOff.y - (actor.height * actor.scaleY) / 2
        )
    }

    override fun dragStop(
        event: InputEvent?,
        x: Float,
        y: Float,
        pointer: Int,
        payload: DragAndDrop.Payload?,
        target: DragAndDrop.Target?
    ) {
        card.actor.isDragged = false
        if (payload == null) return
        if (toLast) card.actor.zIndex -= 1
        val obj = payload.obj as CardDragAndDropPayload
        obj.onDragStop()
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
        payload: DragAndDrop.Payload?,
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
class CardDragAndDropPayload(val card: Card) {

    private val tasks: MutableList<() -> Unit> = mutableListOf()

    /**
     * when the drag is stopped, the card will be reset to [pos]
     */
    fun resetTo(pos: Vector2) = tasks.add {
        card.actor.setPosition(pos.x, pos.y)
    }

    /**
     * when the drag is stopped, the card will be loaded into the revolver in [slot]
     */
    fun loadIntoRevolver(slot: Int) = tasks.add {
        FortyFive.currentGame!!.loadBulletInRevolver(card, slot)  //TODO ugly
    }


    /**
     * called when the drag is stopped
     */
    fun onDragStop() {
        for (task in tasks) task()
    }

}

class ShopDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : DragBehaviour(dragAndDrop, actor, onj) {

    private val toLast: Boolean

    private var startPos = Vector2()

    init {
        toLast = onj.getOr("moveToLastIndex", false)
    }

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): Payload? {
        if ((actor !is CustomImageActor) || (actor as CustomImageActor).inActorState("unbuyable")) return null
        startPos = Vector2(x * actor.scaleX, y * actor.scaleY)
        val payload = Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor
        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = actor.parent
        if (toLast) actor.toFront()

        val obj = DragAndDropPayload(actor)
        payload.obj = obj
        obj.resetTo(Vector2(actor.x, actor.y))
        return payload
    }


    override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        super.drag(event, x, y, pointer)
        val parentOff = actor.parent.localToStageCoordinates(Vector2(0f, 0f))
        dragAndDrop.setDragActorPosition(
            -parentOff.x + actor.width - startPos.x,
            -parentOff.y - startPos.y
        )
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
        val obj = payload.obj as DragAndDropPayload
        (actor.parent.parent as CustomScrollableFlexBox).currentlyDraggedChild = null
        obj.onDragStop()
    }


    class DragAndDropPayload(val actor: Actor) {

        private val tasks: MutableList<() -> Unit> = mutableListOf()

        fun resetTo(pos: Vector2) = tasks.add {
            actor.x = pos.x
            actor.y = pos.y
        }

        fun onDragStop() {
            for (task in tasks) task()
        }


        /**
         * called when the drag is stopped
         */
        fun onBuy() = tasks.add {
            val scr=(FortyFive.screen as OnjScreen).screenController as ShopScreenController
            scr.buyCard(actor)
            println("now buy stuff") //TODO hier weitermachen
        }

/*        fun change(bought: Boolean) = tasks.add {
            actor as CustomImageActor
            if (bought) {
                actor.styleManager?.enterActorState("unbuyable")
            } else {
                actor.styleManager?.leaveActorState("unbuyable")
            }
        }*/
    }
}