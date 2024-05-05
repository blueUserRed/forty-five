package com.fourinachamber.fortyfive.game.card

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.GamePredicate

data class PassiveEffectPrototype(
    val creator: () -> PassiveEffect
)

sealed class PassiveEffect(val predicate: GamePredicate) {

    private var wasActive: Boolean = false

    lateinit var card: Card

    fun checkActive(controller: GameController) {
        val isActive = predicate.check(controller)
        if (isActive == wasActive) return
        if (isActive) onActivation() else onDeactivation()
    }

    open fun onActivation() {}

    open fun onDeactivation() {}


    class BottomToTopCard(predicate: GamePredicate) : PassiveEffect(predicate) {

        override fun onActivation() {
            card.bottomCardToTopCard()
        }
    }

}