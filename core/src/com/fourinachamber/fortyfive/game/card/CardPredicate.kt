package com.fourinachamber.fortyfive.game.card

import com.fourinachamber.fortyfive.game.controller.GameController
import com.fourinachamber.fortyfive.game.controller.NewGameController.Zone

fun interface CardPredicate {

    fun check(cardToCheck: Card, controller: GameController, effectCard: Card?): Boolean

    companion object {

        fun cost(cost: Int) = CardPredicate { card, _, _ -> card.baseCost == cost }

        fun inZone(vararg zone: Zone) = CardPredicate { card, _, _ -> card.inZone(*zone) }

        fun hasName(name: String) = CardPredicate { card, _, _ -> card.name == name }

        fun isSelf() = CardPredicate { card, _, effectCard -> card === effectCard }

        fun inRevolverSlot(slot: Int) = CardPredicate { card, controller, _ ->
            controller.revolver.slots.find { it.card === card }?.num == slot
        }

        fun not(predicate: CardPredicate) = CardPredicate { card, controller, effectCard ->
            !predicate.check(card, controller, effectCard)
        }

        fun and(first: CardPredicate, second: CardPredicate) = CardPredicate { card, controller, effectCard ->
            first.check(card, controller, effectCard) && second.check(card, controller, effectCard)
        }

        fun or(first: CardPredicate, second: CardPredicate) = CardPredicate { card, controller, effectCard ->
            first.check(card, controller, effectCard) || second.check(card, controller, effectCard)
        }

    }

}
