package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.utils.Timeline
import java.lang.Integer.min
import kotlin.random.Random

sealed class EnemyAction {

    abstract fun getTimeline(controller: GameController): Timeline

    abstract fun applicable(controller: GameController): Boolean

    object DestroyCardsInHand : EnemyAction() {

        override fun applicable(controller: GameController): Boolean = controller.cardHand.cards.isNotEmpty()

        override fun getTimeline(controller: GameController): Timeline = Timeline.timeline {
            action {
                val cardHand = controller.cardHand
                val cardAmount = cardHand.cards.size
                val amountToDestroy = (1..min(cardAmount, 4)).random()
                repeat(amountToDestroy) {
                    cardHand.removeCard(cardHand.cards[(0..cardHand.cards.size).random()])
                }
            }
        }
    }

    object RevolverRotation : EnemyAction() {

        override fun getTimeline(controller: GameController): Timeline = Timeline.timeline {
            val rotation = if (Random.nextBoolean()) {
                GameController.RevolverRotation.Right((1..3).random())
            } else {
                GameController.RevolverRotation.Left((1..3).random())
            }
            include(controller.revolver.rotate(rotation))
        }

        override fun applicable(controller: GameController): Boolean = true
    }

}
