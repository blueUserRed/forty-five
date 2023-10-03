package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.game.StatusEffect
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.scale
import com.fourinachamber.fortyfive.utils.toIntRange
import onj.value.OnjArray
import onj.value.OnjNamedObject
import kotlin.random.Random

sealed class EnemyAction(val showProbability: Float) {

    var scaleFactor: Float = 1f

    abstract fun getTimeline(controller: GameController, scale: Double): Timeline

    abstract fun applicable(controller: GameController): Boolean

    class DamagePlayer(
        val damage: IntRange,
        showProbability: Float
    ) : EnemyAction(showProbability) {

        override fun getTimeline(controller: GameController, scale: Double): Timeline = Timeline.timeline {
            include(controller.enemyAttackTimeline(damage.scale(scale * scaleFactor).random()))
        }

        override fun applicable(controller: GameController): Boolean = true
    }

    class DestroyCardsInHand(
        val maxCards: Int,
        showProbability: Float,
    ) : EnemyAction(showProbability) {

        override fun applicable(controller: GameController): Boolean = controller.cardHand.cards.isNotEmpty()

        override fun getTimeline(controller: GameController, scale: Double): Timeline = Timeline.timeline {
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
                { controller.confirmationPopupTimeline(text) },
                { true }
            )
            action {
                repeat(amountToDestroy) {
                    val card = cardHand.cards[(0 until cardHand.cards.size).random()]
                    controller.destroyCardInHand(card)
                }
            }
        }
    }

    class RevolverRotation(
        val maxTurnAmount: Int,
        showProbability: Float
    ) : EnemyAction(showProbability) {

        override fun getTimeline(controller: GameController, scale: Double): Timeline = Timeline.timeline {
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
            include(controller.confirmationPopupTimeline(text))
            include(controller.rotateRevolver(rotation))
        }

        override fun applicable(controller: GameController): Boolean = controller.revolver.slots.any { it.card != null }

    }

    class ReturnCardToHand(showProbability: Float) : EnemyAction(showProbability) {

        override fun getTimeline(controller: GameController, scale: Double): Timeline = Timeline.timeline {
            include(controller.confirmationPopupTimeline(
                GraphicsConfig.rawTemplateString("returnCardToHand")
            ))
            action {
                val card = controller
                    .revolver
                    .slots
                    .filter { it.card != null }
                    .random()
                    .card!!
                controller.putCardFromRevolverBackInHand(card)
            }
        }

        override fun applicable(controller: GameController): Boolean =
            controller.revolver.isBulletLoaded() && controller.cardHand.cards.size < controller.hardMaxCards
    }

    class TakeCover(
        val cover: IntRange,
        showProbability: Float
    ) : EnemyAction(showProbability) {

        override fun getTimeline(controller: GameController, scale: Double): Timeline = Timeline.timeline {
            action {
                controller.enemyArea.enemies[0].currentCover += cover.scale(scale * scaleFactor).random()
            }
        }

        override fun applicable(controller: GameController): Boolean = true
    }

    class GivePlayerStatusEffect(
        val statusEffect: StatusEffect,
        showProbability: Float
    ) : EnemyAction(showProbability) {

        override fun getTimeline(controller: GameController, scale: Double): Timeline = Timeline.timeline {
            action {
                controller.applyStatusEffectToPlayer(statusEffect.copy())
            }
        }

        override fun applicable(controller: GameController): Boolean = controller.isStatusEffectApplicable(statusEffect)
    }

    companion object {

        fun fromOnj(obj: OnjNamedObject): EnemyAction = when (obj.name) {

            "DestroyCardsInHand" -> DestroyCardsInHand(
                obj.get<Long>("maxCards").toInt(),
                obj.get<Long>("showProbability").toFloat(),
            )
            "RevolverRotation" -> RevolverRotation(
                obj.get<Long>("maxTurns").toInt(),
                obj.get<Long>("showProbability").toFloat()
            )
            "TakeCover" -> TakeCover(
                obj.get<OnjArray>("cover").toIntRange(),
                obj.get<Long>("showProbability").toFloat()
            )
            "GivePlayerStatusEffect" -> GivePlayerStatusEffect(
                obj.get<StatusEffect>("statusEffect"),
                obj.get<Long>("showProbability").toFloat()
            )
            "ReturnCardToHand" -> ReturnCardToHand(
                obj.get<Long>("showProbability").toFloat()
            )
            "DamagePlayer" -> DamagePlayer(
                obj.get<OnjArray>("damage").toIntRange(),
                obj.get<Long>("showProbability").toFloat()
            )

            else -> throw RuntimeException("unknown enemy action: ${obj.name}")

        }.apply {
            scaleFactor = obj.getOr("scaleFactor", 1f)
        }

    }

}
