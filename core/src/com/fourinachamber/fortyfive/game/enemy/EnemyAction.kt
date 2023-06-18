package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import kotlin.random.Random

sealed class EnemyAction {

    abstract val difficultyRange: ClosedFloatingPointRange<Double>

    abstract fun getTimeline(controller: GameController, difficulty: Double): Timeline

    abstract fun applicable(controller: GameController): Boolean

    object DestroyCardsInHand : EnemyAction() {

        override val difficultyRange: ClosedFloatingPointRange<Double> = 0.3..1.0

        private const val rawText: String = "Haha! Now I'm going to destroy {amount} card{s} in your hand!"

        override fun applicable(controller: GameController): Boolean = controller.cardHand.cards.isNotEmpty()

        override fun getTimeline(controller: GameController, difficulty: Double): Timeline = Timeline.timeline {
            var text = ""
            var amountToDestroy = 0
            val cardHand = controller.cardHand
            action {
                val cardAmount = cardHand.cards.size
                amountToDestroy = (cardAmount * difficulty).toInt().coerceAtLeast(1)
                text = TemplateString(
                    rawText,
                    mapOf("amount" to amountToDestroy, "s" to if (amountToDestroy == 1) "s" else "")
                ).string
            }
            includeLater(
                { controller.confirmationPopup(text) },
                { true }
            )
            action {
                repeat(amountToDestroy) {
                    cardHand.removeCard(cardHand.cards[(0..cardHand.cards.size).random()])
                }
            }
        }
    }

    object RevolverRotation : EnemyAction() {

        override val difficultyRange: ClosedFloatingPointRange<Double> = 0.3..0.3

        // TODO: move in GraphicsConfig
        private const val rawText1Rot: String = "Haha! Now I'm going to move you revolver to the {direction}"
        private const val rawTextMoreRot: String =
            "Haha! Now I'm going to move you revolver {number} times to the {direction}"

        override fun getTimeline(controller: GameController, difficulty: Double): Timeline = Timeline.timeline {
            val amount = (1..3).random()
            val rotation = if (Random.nextBoolean()) {
                GameController.RevolverRotation.Right(amount)
            } else {
                GameController.RevolverRotation.Left(amount)
            }
            val text = TemplateString(
                if (amount == 1) rawText1Rot else rawTextMoreRot,
                mapOf("direction" to rotation::class.simpleName!!.lowercase(), "amount" to amount)
            ).string
            include(controller.confirmationPopup(text))
            include(controller.revolver.rotate(rotation))
        }

        override fun applicable(controller: GameController): Boolean = true
    }

}
