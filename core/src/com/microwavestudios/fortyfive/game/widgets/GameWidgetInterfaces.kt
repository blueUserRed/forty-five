package com.microwavestudios.fortyfive.game.widgets

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.microwavestudios.fortyfive.game.Selectable
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline


interface ICardHand {

    val amountOfCards: Int
    val events: EventPipeline

    fun allCards(): List<Card>
    fun addCard(card: Card)
    fun removeCard(card: Card)
    fun triggerPositionForCardActor(card: CardActor): Vector2

    data class CardDraggedOntoSlotEvent(val card: Card, val slot: IRevolverSlot)
}

interface IAfterlife {

    val cards: List<Card>
    val isOpen: Boolean
    val isClosed: Boolean
    val afterlifeIsVisible: Boolean

    fun pushCard(card: Card)
    fun scrollToBeginTimeline(): Timeline
    fun popCardTimeline(): Timeline
    fun toggleTimeline(): Timeline
    fun openTimeline(): Timeline
    fun closeTimeline(): Timeline
}

interface IRevolver {

    val slots: Array<out IRevolverSlot>

    fun setCard(slot: Int, card: Card?)
    fun preAddCard(slot: Int, card: Card)
    fun removeCard(slot: Int)
    fun removeCard(card: Card)
    fun getCardInSlot(slot: Int): Card?

    fun getCardTriggerPosition(): Vector2
    fun getMirroredCardTriggerPosition(): Vector2
    fun getCardOnShotTriggerPosition(): Vector2

    fun rotate(rotation: RevolverRotation): Timeline

    fun forceGetActor(): Actor

    companion object {
        const val revolverSlotGroup = "revolver-slot"
    }
}

interface IRevolverSlot : Selectable<IRevolverSlot> {

    val num: Int
    val card: Card?
    val revolver: IRevolver

    fun cardPosition(): Vector2

    fun forceGetActor(): InputActor
}
