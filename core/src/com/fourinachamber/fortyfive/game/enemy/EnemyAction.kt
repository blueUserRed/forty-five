package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.GamePredicate
import com.fourinachamber.fortyfive.game.StatusEffect
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.scale
import com.fourinachamber.fortyfive.utils.toIntRange
import onj.value.OnjArray
import onj.value.OnjNamedObject
import kotlin.random.Random

class EnemyAction(
    val indicatorText: String?,
    val iconHandle: ResourceHandle?,
    val prototype: EnemyActionPrototype,
    private val timelineCreator: Timeline.TimelineBuilderDSL.() -> Unit
) {

    fun getTimeline(): Timeline = Timeline.timeline { timelineCreator(this) }

}

sealed class EnemyActionPrototype(
    val showProbability: Float,
    protected val enemy: Enemy,
    val hasUnlikelyPredicates: Boolean,
) {

    var scaleFactor: Float = 1f

    private val predicates: MutableList<GamePredicate> = mutableListOf()

    fun addPredicate(predicate: GamePredicate) {
        predicates.add(predicate)
    }

    protected fun checkPredicates(controller: GameController): Boolean = predicates.all { it.check(controller) }

    abstract fun create(controller: GameController, scale: Double): EnemyAction

    open fun applicable(controller: GameController): Boolean = checkPredicates(controller)

    class DamagePlayer(
        val damage: IntRange,
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        private val iconHandle: ResourceHandle?,
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val damage = damage.scale(scale * scaleFactor).random()
            return EnemyAction(damage.toString(), iconHandle, this) {
                include(controller.enemyAttackTimeline(damage))
            }
        }
    }

    class DestroyCardsInHand(
        val maxCards: Int,
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        private val iconHandle: ResourceHandle?,
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates) {

        override fun applicable(controller: GameController): Boolean =
            checkPredicates(controller) && controller.cardHand.cards.isNotEmpty()

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val cardAmount = controller.cardHand.cards.size
            val amountToDestroy = (1..maxCards).random().coerceAtMost(cardAmount - 1)
            return EnemyAction(amountToDestroy.toString(), iconHandle, this) {
                repeat(amountToDestroy) {
                    // might cause mismatches when this action is shown instead of hidden
                    if (controller.cardHand.cards.isEmpty()) return@repeat
                    val card = controller.cardHand.cards[(0 until controller.cardHand.cards.size).random()]
                    controller.destroyCardInHand(card)
                }
            }
        }

        //        override fun getTimeline(controller: GameController, scale: Double): Timeline = Timeline.timeline {
//            var text = ""
//            var amountToDestroy = 0
//            val cardHand = controller.cardHand
//            action {
//                val cardAmount = cardHand.cards.size
//                amountToDestroy = (1..maxCards).random().coerceAtMost(cardAmount - 1)
//                text = TemplateString(
//                    GraphicsConfig.rawTemplateString("destroyCardsInHand"),
//                    mapOf("amount" to amountToDestroy, "s" to if (amountToDestroy == 1) "s" else "")
//                ).string
//            }
//            includeLater(
//                { controller.confirmationPopupTimeline(text) },
//                { true }
//            )
//            action {
//                repeat(amountToDestroy) {
//                    val card = cardHand.cards[(0 until cardHand.cards.size).random()]
//                    controller.destroyCardInHand(card)
//                }
//            }
//        }
    }

    class RevolverRotation(
        val maxTurnAmount: Int,
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        private val iconHandle: ResourceHandle?,
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val amount = (1..maxTurnAmount).random()
            val rotation = if (Random.nextBoolean()) {
                GameController.RevolverRotation.Right(amount)
            } else {
                GameController.RevolverRotation.Left(amount)
            }
            return EnemyAction(amount.toString(), iconHandle, this) {
                include(controller.rotateRevolver(rotation))
            }
        }

        //        override fun getTimeline(controller: GameController, scale: Double): Timeline = Timeline.timeline {
//            val amount = (1..maxTurnAmount).random()
//            val rotation = if (Random.nextBoolean()) {
//                GameController.RevolverRotation.Right(amount)
//            } else {
//                GameController.RevolverRotation.Left(amount)
//            }
//            val text = TemplateString(
//                if (amount == 1) {
//                    GraphicsConfig.rawTemplateString("revolverRotation1Rot")
//                } else {
//                    GraphicsConfig.rawTemplateString("revolverRotationMoreRot")
//                },
//                mapOf("direction" to rotation::class.simpleName!!.lowercase(), "amount" to amount)
//            ).string
//            include(controller.confirmationPopupTimeline(text))
//            include(controller.rotateRevolver(rotation))
//        }

        override fun applicable(controller: GameController): Boolean =
            checkPredicates(controller) && controller.revolver.slots.any { it.card != null }

    }

    class ReturnCardToHand(
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        private val iconHandle: ResourceHandle?
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates) {

        override fun create(
            controller: GameController,
            scale: Double
        ): EnemyAction = EnemyAction(null, iconHandle, this) {
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

        //        override fun getTimeline(controller: GameController, scale: Double): Timeline = Timeline.timeline {
//            include(controller.confirmationPopupTimeline(
//                GraphicsConfig.rawTemplateString("returnCardToHand")
//            ))
//            action {
//                val card = controller
//                    .revolver
//                    .slots
//                    .filter { it.card != null }
//                    .random()
//                    .card!!
//                controller.putCardFromRevolverBackInHand(card)
//            }
//        }

        override fun applicable(controller: GameController): Boolean =
            checkPredicates(controller) &&
            controller.revolver.isBulletLoaded() &&
            controller.cardHand.cards.size < controller.hardMaxCards
    }

    class TakeCover(
        val cover: IntRange,
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        private val iconHandle: ResourceHandle?,
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val cover = cover.scale(scale * scaleFactor).random()
            return EnemyAction(cover.toString(), iconHandle, this) {
                enemy.currentCover += cover
            }
        }

    }

    class GivePlayerStatusEffect(
        val statusEffect: StatusEffect,
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        private val iconHandle: ResourceHandle?,
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val statusEffect = statusEffect.copy()
            statusEffect.start(controller) // start effect here because start() needs to be called before getDisplayText()
            return EnemyAction(statusEffect.getDisplayText(), iconHandle, this) {
                action {
                    controller.applyStatusEffectToPlayer(statusEffect.copy())
                }
            }
        }

        //        override fun getTimeline(controller: GameController, scale: Double): Timeline = Timeline.timeline {
//            action {
//                controller.applyStatusEffectToPlayer(statusEffect.copy())
//            }
//        }

        override fun applicable(controller: GameController): Boolean =
            checkPredicates(controller) && controller.isStatusEffectApplicable(statusEffect)
    }

    class GiveSelfStatusEffect(
        val statusEffect: StatusEffect,
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        private val iconHandle: ResourceHandle?,
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val statusEffect = statusEffect.copy()
            statusEffect.start(controller) // start effect here because start() needs to be called before getDisplayText()
            return EnemyAction(statusEffect.getDisplayText(), iconHandle, this) {
                action {
                    controller.tryApplyStatusEffectToEnemy(statusEffect, enemy)
                }
            }
        }

    }

    class GivePlayerCard(
        val card: String,
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        private val iconHandle: ResourceHandle?,
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            return EnemyAction(null, iconHandle, this) {
                include(controller.tryToPutCardsInHandTimeline(card))
            }
        }

        override fun applicable(controller: GameController): Boolean =
            checkPredicates(controller) && controller.cardHand.cards.size < controller.hardMaxCards
    }

    companion object {

        fun fromOnj(obj: OnjNamedObject, forEnemy: Enemy): EnemyActionPrototype = when (obj.name) {

            "DestroyCardsInHand" -> DestroyCardsInHand(
                obj.get<Long>("maxCards").toInt(),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.getOr<String?>("icon", null)
            )
            "RevolverRotation" -> RevolverRotation(
                obj.get<Long>("maxTurns").toInt(),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.getOr<String?>("icon", null)
            )
            "TakeCover" -> TakeCover(
                obj.get<OnjArray>("cover").toIntRange(),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.getOr<String?>("icon", null)
            )
            "GivePlayerStatusEffect" -> GivePlayerStatusEffect(
                obj.get<StatusEffect>("statusEffect"),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.getOr<String?>("icon", null)
            )
            "ReturnCardToHand" -> ReturnCardToHand(
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.getOr<String?>("icon", null)
            )
            "DamagePlayer" -> DamagePlayer(
                obj.get<OnjArray>("damage").toIntRange(),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.getOr<String?>("icon", null)
            )
            "GiveSelfStatusEffect" -> GiveSelfStatusEffect(
                obj.get<StatusEffect>("statusEffect"),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.getOr<String?>("icon", null)
            )
            "GivePlayerCard" -> GivePlayerCard(
                obj.get<String>("card"),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.getOr<String?>("icon", null)
            )

            else -> throw RuntimeException("unknown enemy action: ${obj.name}")

        }.apply {
            scaleFactor = obj.getOr("scaleFactor", 1f)
            val predicates = obj.getOr<OnjArray?>("predicates", null) ?: return@apply
            predicates
                .value
                .map { GamePredicate.fromOnj(it as OnjNamedObject, forEnemy) }
                .forEach { addPredicate(it) }
        }

    }

}
