package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import onj.value.OnjNamedObject
import kotlin.random.Random

sealed class EnemyAction {

    abstract fun getTimeline(controller: GameController): Timeline

    abstract fun applicable(controller: GameController): Boolean

    class DestroyCardsInHand(val maxCards: Int) : EnemyAction() {

        override fun applicable(controller: GameController): Boolean = controller.cardHand.cards.isNotEmpty()

        override fun getTimeline(controller: GameController): Timeline = Timeline.timeline {
            var text = ""
            var amountToDestroy = 0
            val cardHand = controller.cardHand
            action {
                val cardAmount = cardHand.cards.size
                amountToDestroy = (1..maxCards).random().coerceAtMost(cardAmount)
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

        companion object {
            private const val rawText: String = "Haha! Now I'm going to destroy {amount} card{s} in your hand!"
        }
    }

    class RevolverRotation(val maxTurnAmount: Int) : EnemyAction() {

        override fun getTimeline(controller: GameController): Timeline = Timeline.timeline {
            val amount = (1..maxTurnAmount).random()
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

        companion object {
            // TODO: move in GraphicsConfig
            private const val rawText1Rot: String = "Haha! Now I'm going to move you revolver to the {direction}"
            private const val rawTextMoreRot: String =
                "Haha! Now I'm going to move you revolver {amount} times to the {direction}"
        }

    }

    companion object {

        fun fromOnj(obj: OnjNamedObject): EnemyAction = when (obj.name) {

            "DestroyCardsInHand" -> DestroyCardsInHand(obj.get<Long>("maxCards").toInt())
            "RevolverRotation" -> RevolverRotation(obj.get<Long>("maxTurns").toInt())

            else -> throw RuntimeException("unknown enemy action: ${obj.name}")

        }

    }

}
