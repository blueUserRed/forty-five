package com.fourinachamber.fourtyfive.card

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.game.CardActor
import com.fourinachamber.fourtyfive.screen.DragBehaviour
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import onj.OnjNamedObject


class CardDragSource(
    dragAndDrop: DragAndDrop,
    screenDataProvider: ScreenDataProvider,
    actor: Actor,
    onj: OnjNamedObject
) : DragBehaviour(dragAndDrop, screenDataProvider, actor, onj) {

    private val card: Card

    init {
        if (actor !is CardActor) throw RuntimeException("CardDragSource can only be used on an CardActor")
        card = actor.card
    }

    override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int): DragAndDrop.Payload {
        card.actor.isDragged = true
        val payload = DragAndDrop.Payload()
        dragAndDrop.setKeepWithinStage(false)

        payload.dragActor = actor

        payload.setObject(mutableMapOf(
            "resetPosition" to (actor.x to actor.y)
        ))

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
        val map = payload.`object` as Map<*, *>

        val (actorX, actorY) = (map["resetPosition"] ?: return) as Pair<*, *>
        actor.setPosition(actorX as Float, actorY as Float)
    }

}
