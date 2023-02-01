package com.fourinachamber.fourtyfive.game.card

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.screen.gameComponents.CoverStack
import com.fourinachamber.fourtyfive.screen.gameComponents.RevolverSlot
import com.fourinachamber.fourtyfive.screen.general.DragBehaviour
import com.fourinachamber.fourtyfive.screen.general.DropBehaviour
import com.fourinachamber.fourtyfive.utils.obj
import onj.value.OnjNamedObject

/**
 * the DragSource used for dragging a card to the revolver
 */
class CardDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject
) : DragBehaviour(dragAndDrop, actor, onj) {

    private val card: Card

    init {
        if (actor !is CardActor) throw RuntimeException("CardDragSource can only be used on an CardActor")
        card = actor.card
    }

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload? {

        if (!card.isDraggable) return null

        card.actor.isDragged = true
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor

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

    override fun drop(source: DragAndDrop.Source?, payload: DragAndDrop.Payload?, x: Float, y: Float, pointer: Int) {
        if (payload == null || source == null) return

        val obj = payload.obj!! as CardDragAndDropPayload
        obj.loadIntoRevolver(revolverSlot.num)
    }

}

class CoverAreaDropTarget(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject
) : DropBehaviour(dragAndDrop, actor, onj) {

    private val coverStack: CoverStack

    init {
        if (actor !is CoverStack) throw RuntimeException("CoverAreaDropTarget can only be used on a coverStack")
        coverStack = actor
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

    override fun drop(source: DragAndDrop.Source?, payload: DragAndDrop.Payload?, x: Float, y: Float, pointer: Int) {
        if (payload == null || source == null) return

        val obj = payload.obj!! as CardDragAndDropPayload
        obj.addCover(coverStack.num)
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
        FourtyFive.currentGame!!.loadBulletInRevolver(card, slot)
    }

    /**
     * when the drag is stopped, the card will be added to the cover area in slot [slot]
     */
    fun addCover(slot: Int) = tasks.add {
        FourtyFive.currentGame!!.addCover(card, slot)
    }

    /**
     * called when the drag is stopped
     */
    fun onDragStop() {
        for (task in tasks) task()
    }
}
