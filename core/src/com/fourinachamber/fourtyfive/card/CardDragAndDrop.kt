package com.fourinachamber.fourtyfive.card

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.game.RevolverSlot
import com.fourinachamber.fourtyfive.screen.DragBehaviour
import com.fourinachamber.fourtyfive.screen.DropBehaviour
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.utils.obj
import onj.OnjNamedObject


interface GameCardDragSource {
    var gameScreenController: GameScreenController
}

class CardDragSource(
    dragAndDrop: DragAndDrop,
    screenDataProvider: ScreenDataProvider,
    actor: Actor,
    onj: OnjNamedObject
) : DragBehaviour(dragAndDrop, screenDataProvider, actor, onj), GameCardDragSource {

    override lateinit var gameScreenController: GameScreenController

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

        val obj = CardDragAndDropPayload(card, gameScreenController)
        payload.obj = obj
        obj.resetTo(Vector2(actor.x, actor.y))

        dragAndDrop.setDragActorPosition(
            actor.width - (actor.width * actor.scaleX / 2),
            -(actor.height * actor.scaleY) / 2
        )
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
        card.actor.isDragged = false
        if (payload == null) return

        val obj = payload.obj as CardDragAndDropPayload
        obj.onDragStop()
    }

}

class RevolverDropTarget(
    dragAndDrop: DragAndDrop,
    screenDataProvider: ScreenDataProvider,
    actor: Actor,
    onj: OnjNamedObject
) : DropBehaviour(dragAndDrop, screenDataProvider, actor, onj) {

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
class CardDragAndDropPayload(val card: Card, val gameScreenController: GameScreenController) {

    private val tasks: MutableList<() -> Unit> = mutableListOf()

    fun resetTo(pos: Vector2) = tasks.add {
        card.actor.setPosition(pos.x, pos.y)
    }

    fun loadIntoRevolver(slot: Int) = tasks.add {
        gameScreenController.moveCardFromHandToRevolver(card, slot)
    }

    fun onDragStop() {
        for (task in tasks) task()
    }
}
