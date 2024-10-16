package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.gameWidgets.PutCardsUnderDeckWidget
import com.fourinachamber.fortyfive.screen.gameWidgets.RevolverSlot
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.obj
import onj.value.OnjNamedObject


/**
 * the DragSource used for dragging a card to the revolver
 */
open class CardDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
) : CenteredDragSource(dragAndDrop, actor, onj) {

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

        SoundPlayer.situation("card_drag_started", card.actor.screen)

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

class CardDropTarget(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject
) : DropBehaviour(dragAndDrop, actor) {


    private val card: Card

    init {
        if (actor !is CardActor) throw RuntimeException("CardDropTarget can only be used on a cardActor")
        card = actor.card
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
        val revolver = FortyFive.currentGame!!.revolver
        val slot = revolver.slots.find { it.card === card }?.num ?: return
        obj.loadIntoRevolver(slot)
    }

}

/**
 * used for dropping a card into the revolver
 */
class RevolverDropTarget(
    dragAndDrop: DragAndDrop,
    actor: Actor,
) : DropBehaviour(dragAndDrop, actor) {

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
 * used for dropping a card into the revolver
 */
class PutCardsUnderDeckDropTarget(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject
) : DropBehaviour(dragAndDrop, actor) {

    private val putCardsUnderDeckWidget: PutCardsUnderDeckWidget

    init {
        if (actor !is PutCardsUnderDeckWidget) {
            throw RuntimeException("PutCardsUnderDeckDropTarget can only be used on a PutCardsUnderDeckWidget")
        }
        putCardsUnderDeckWidget = actor
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
        obj.putCardUnderDeck(putCardsUnderDeckWidget)
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
        FortyFive.currentGame!!.loadBulletFromHandInRevolver(card, slot)  //TODO ugly
        SoundPlayer.situation("card_drag_finished", card.actor.screen)
    }

    fun putCardUnderDeck(widget: PutCardsUnderDeckWidget) = tasks.add {
        widget.addCard(card)
        SoundPlayer.situation("card_drag_finished", card.actor.screen)
    }
}
