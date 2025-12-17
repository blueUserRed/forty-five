package com.microwavestudios.fortyfive.game

import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.widgets.RevolverSlot
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.utils.Promise
import com.microwavestudios.fortyfive.utils.asPromise
import com.microwavestudios.fortyfive.utils.map

abstract class BaseSelector<T, U> where T : Selectable<T> {

    fun startSelect(): Promise<out U?> {
        val selectables = getSelectables()
        if (selectables.isEmpty()) return Promise.nullPromise
        if (selectables.size == 1) return mapSelectable(selectables.first()).asPromise()
        begin(selectables)
        val modal = getModal()
        modal.push()
        val promise: Promise<T> = Promise()
        selectables.forEach { it.enterSelectionMode(promise) }
        promise.then {
            selectables.forEach { it.exitSelectionMode() }
            getModal().finished()
            end(selectables)
        }
        return promise.map(::mapSelectable)
    }

    protected open fun begin(selectables: List<T>) {}
    protected open fun end(selectables: List<T>) {}

    protected abstract fun getModal(): InputManager.Modal

    protected abstract fun getSelectables(): List<T>

    protected abstract fun mapSelectable(selectable: T): U

}

class CardInRevolverSelector(
    val controller: GameController,
    val popupText: String,
    val predicate: (Card) -> Boolean = { true }
) : BaseSelector<CardActor, Card>() {

    override fun begin(selectables: List<CardActor>) {
        selectables.forEach { actor ->
            val card = actor.card
            val slot = controller.revolver.slots.find { it.card == card }
            slot?.joinGroup(RevolverSlot.revolverSlotWithCardInSelectionMode)
        }
        controller.gameEvents.fire(GameControllerImpl.Events.SelectionChangedEvent(popupText))
    }

    override fun end(selectables: List<CardActor>) {
        selectables.forEach { actor ->
            val card = actor.card
            val slot = controller.revolver.slots.find { it.card == card }
            slot?.leaveGroup(RevolverSlot.revolverSlotWithCardInSelectionMode)
        }
        controller.gameEvents.fire(GameControllerImpl.Events.SelectionChangedEvent(null))
    }

    override fun getModal(): InputManager.Modal = InputManager.Modal(
        listOf(RevolverSlot.revolverSlotWithCardInSelectionMode),
        controller.screen
    )

    override fun getSelectables(): List<CardActor> =
        controller.cardsInRevolver().filter { predicate(it) }.map { it.actor }

    override fun mapSelectable(selectable: CardActor): Card = selectable.card
}

interface Selectable<T> {
    fun enterSelectionMode(promise: Promise<T>)
    fun exitSelectionMode()
}
