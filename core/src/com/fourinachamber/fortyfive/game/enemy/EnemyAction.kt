package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.GraphicsConfig
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
                amountToDestroy = (1..maxCards).random().coerceAtMost(cardAmount - 1)
                text = TemplateString(
                    GraphicsConfig.rawTemplateString("destroyCardsInHand"),
                    mapOf("amount" to amountToDestroy, "s" to if (amountToDestroy == 1) "s" else "")
                ).string
            }
            includeLater(
                { controller.confirmationPopup(text) },
                { true }
            )
            action {
                repeat(amountToDestroy) {
                    cardHand.removeCard(cardHand.cards[(0 until cardHand.cards.size).random()])
                }
            }
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
                if (amount == 1) {
                    GraphicsConfig.rawTemplateString("revolverRotation1Rot")
                } else {
                    GraphicsConfig.rawTemplateString("revolverRotationMoreRot")
                },
                mapOf("direction" to rotation::class.simpleName!!.lowercase(), "amount" to amount)
            ).string
            include(controller.confirmationPopup(text))
            include(controller.revolver.rotate(rotation))
        }

        override fun applicable(controller: GameController): Boolean = true

    }

    object ReturnCardToHand : EnemyAction() {

        override fun getTimeline(controller: GameController): Timeline = Timeline.timeline {
            include(controller.confirmationPopup(
                GraphicsConfig.rawTemplateString("returnCardToHand")
            ))
            action {
                val card = controller
                    .revolver
                    .slots
                    .filter { it.card != null }
                    .random()
                    .card!!
                controller.revolver.removeCard(card)
                card.leaveGame()
                controller.cardHand.addCard(card)
            }
        }

        override fun applicable(controller: GameController): Boolean =
            controller.revolver.isBulletLoaded() && controller.cardHand.cards.size < controller.maxCards
    }

    companion object {

        fun fromOnj(obj: OnjNamedObject): EnemyAction = when (obj.name) {

            "DestroyCardsInHand" -> DestroyCardsInHand(obj.get<Long>("maxCards").toInt())
            "RevolverRotation" -> RevolverRotation(obj.get<Long>("maxTurns").toInt())
            "ReturnCardToHand" -> ReturnCardToHand

            else -> throw RuntimeException("unknown enemy action: ${obj.name}")

        }

    }

}
