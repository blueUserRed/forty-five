package com.microwavestudios.fortyfive.game

import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.widgets.IRevolverSlot
import com.microwavestudios.fortyfive.game.widgets.RevolverSlot
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.utils.Promise
import com.microwavestudios.fortyfive.utils.asPromise
import com.microwavestudios.fortyfive.utils.map
import com.microwavestudios.fortyfive.utils.requireRenderableScreen

object SelectorFactory {

    var cardInHandSelectorCreator = SelectorCreator { controller, popupText, predicate ->
        CardInHandSelector(controller, popupText, predicate)
    }

    var cardInRevolverSelectorCreator = SelectorCreator { controller, popupText, predicate ->
        CardInRevolverSelector(controller, popupText, predicate)
    }

    var revolverSlotSelectorCreator = SelectorCreator { controller, popupText, predicate ->
        RevolverSlotSelector(controller, popupText, predicate)
    }

    fun getCardInHandSelector(
        controller: GameController,
        popupText: String,
        predicate: (Card) -> Boolean = { true }
    ): ISelector<Card> = cardInHandSelectorCreator.create(controller, popupText, predicate)

    fun getCardInRevolverSelector(
        controller: GameController,
        popupText: String,
        predicate: (Card) -> Boolean = { true }
    ): ISelector<Card> = cardInHandSelectorCreator.create(controller, popupText, predicate)

    fun getRevolverSlotSelector(
        controller: GameController,
        popupText: String,
        predicate: (IRevolverSlot) -> Boolean = { true }
    ): ISelector<IRevolverSlot> = revolverSlotSelectorCreator.create(controller, popupText, predicate)


    fun interface SelectorCreator<T> {

        fun create(controller: GameController, popupText: String, predicate: (T) -> Boolean): ISelector<T>
    }

}

interface ISelector<T> {

    fun startSelect(): Promise<out T?>
}

abstract class BaseSelector<T, U> : ISelector<U> where T : Selectable<T> {

    override fun startSelect(): Promise<out U?> {
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

class CardInHandSelector(
    val controller: GameController,
    val popupText: String,
    val predicate: (Card) -> Boolean = { true }
) : BaseSelector<CardActor, Card>() {

    override fun begin(selectables: List<CardActor>) {
        controller.gameEvents.fire(GameControllerImpl.Events.SelectionChangedEvent(popupText))
    }

    override fun end(selectables: List<CardActor>) {
        controller.gameEvents.fire(GameControllerImpl.Events.SelectionChangedEvent(null))
    }

    override fun getModal(): InputManager.Modal {
        val screen = controller.screen
        requireRenderableScreen(screen)
        return InputManager.Modal(listOf(CardActor.selectableCardGroup), screen)
    }

    override fun getSelectables(): List<CardActor> =
        controller.cardsInHand.filter { predicate(it) }.map { it.presentation.forceGetActor() }

    override fun mapSelectable(selectable: CardActor): Card = selectable.card
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
            slot?.forceGetActor()?.joinGroup(RevolverSlot.revolverSlotWithCardInSelectionMode)
        }
        controller.gameEvents.fire(GameControllerImpl.Events.SelectionChangedEvent(popupText))
    }

    override fun end(selectables: List<CardActor>) {
        selectables.forEach { actor ->
            val card = actor.card
            val slot = controller.revolver.slots.find { it.card == card }
            slot?.forceGetActor()?.leaveGroup(RevolverSlot.revolverSlotWithCardInSelectionMode)
        }
        controller.gameEvents.fire(GameControllerImpl.Events.SelectionChangedEvent(null))
    }

    override fun getModal(): InputManager.Modal {
        val screen = controller.screen
        requireRenderableScreen(screen)
        return InputManager.Modal(listOf(RevolverSlot.revolverSlotWithCardInSelectionMode), screen)
    }

    override fun getSelectables(): List<CardActor> =
        controller.cardsInRevolver().filter { predicate(it) }.map { it.presentation.forceGetActor() }

    override fun mapSelectable(selectable: CardActor): Card = selectable.card
}

class RevolverSlotSelector(
    val controller: GameController,
    val popupText: String,
    val predicate: (IRevolverSlot) -> Boolean
) : BaseSelector<IRevolverSlot, IRevolverSlot>() {

    override fun begin(selectables: List<IRevolverSlot>) {
        controller.gameEvents.fire(GameControllerImpl.Events.SelectionChangedEvent(popupText))
    }

    override fun end(selectables: List<IRevolverSlot>) {
        controller.gameEvents.fire(GameControllerImpl.Events.SelectionChangedEvent(null))
    }

    override fun getModal(): InputManager.Modal {
        val screen = controller.screen
        requireRenderableScreen(screen)
        return InputManager.Modal(listOf(RevolverSlot.revolverSlotGroup), screen)
    }

    override fun getSelectables(): List<IRevolverSlot> = controller.revolver.slots.filter { predicate(it) }

    override fun mapSelectable(selectable: IRevolverSlot): IRevolverSlot = selectable
}

interface Selectable<T> {
    fun enterSelectionMode(promise: Promise<T>)
    fun exitSelectionMode()
}
