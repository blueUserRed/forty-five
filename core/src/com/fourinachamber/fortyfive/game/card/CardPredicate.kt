package com.fourinachamber.fortyfive.game.card

import com.fourinachamber.fortyfive.game.controller.GameController

fun interface CardPredicate {

    fun check(card: Card, controller: GameController): Boolean

    companion object {

        fun cost(cost: Int) = CardPredicate { card, _ -> card.baseCost == cost }

    }

}
