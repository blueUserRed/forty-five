package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.GameController.RevolverRotation
import com.fourinachamber.fortyfive.game.GamePredicate
import com.fourinachamber.fortyfive.game.StatusEffectCreator
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.scale
import com.fourinachamber.fortyfive.utils.toIntRange
import onj.value.OnjArray
import onj.value.OnjNamedObject
import kotlin.random.Random

class EnemyAction(
    val indicatorText: String?,
    val descriptionParams: Map<String, Any>,
    val prototype: EnemyActionPrototype,
    private val timelineCreator: Timeline.TimelineBuilderDSL.() -> Unit
) {

    fun getTimeline(): Timeline = Timeline.timeline { timelineCreator(this) }

}

sealed class EnemyActionPrototype(
    protected val enemy: Enemy,
    val hasSpecialAnimation: Boolean
) {

    lateinit var iconHandle: ResourceHandle
    lateinit var commonPanel1: ResourceHandle
    lateinit var commonPanel2: ResourceHandle
    lateinit var commonPanel3: ResourceHandle
    lateinit var specialPanel: ResourceHandle
    lateinit var title: String
    lateinit var descriptionTemplate: String

    var scaleFactor: Float = 1f

    private val predicates: MutableList<GamePredicate> = mutableListOf()

    abstract fun create(controller: GameController, scale: Double): EnemyAction

    class DamagePlayer(
        val damage: IntRange,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val damage = damage.scale(scale * scaleFactor).random()
            return EnemyAction(damage.toString(), mapOf("damage" to damage), this) {
                include(controller.enemyAttackTimeline(damage))
            }
        }
    }

    class DestroyCardsInHand(
        val maxCards: Int,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val cardAmount = controller.cardHand.cards.size
            val amountToDestroy = (1..maxCards).random().coerceAtMost(cardAmount - 1)
            return EnemyAction(amountToDestroy.toString(), mapOf("amount" to amountToDestroy),this) {
                repeat(amountToDestroy) {
                    // might cause mismatches when this action is shown instead of hidden
                    if (controller.cardHand.cards.isEmpty()) return@repeat
                    val card = controller.cardHand.cards[(0 until controller.cardHand.cards.size).random()]
                    controller.destroyCardInHand(card)
                }
            }
        }

    }

    class RotateRevolver(
        val maxTurnAmount: Int,
        val forceDirection: RevolverRotation?,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val amount = (1..maxTurnAmount).random()
            val rotation = if (forceDirection == null) {
                if (Random.nextBoolean()) {
                    RevolverRotation.Right(amount)
                } else {
                    RevolverRotation.Left(amount)
                }
            } else {
                when (forceDirection) {
                    is RevolverRotation.Right -> RevolverRotation.Right(amount)
                    is RevolverRotation.Left -> RevolverRotation.Left(amount)
                    else -> RevolverRotation.Right(amount)
                }
            }
            val descriptionParams = mapOf("amount" to amount, "direction" to rotation.directionString)
            return EnemyAction(amount.toString(), descriptionParams, this) {
                include(controller.rotateRevolver(rotation))
            }
        }

    }

    class ReturnCardToHand(
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun create(
            controller: GameController,
            scale: Double
        ): EnemyAction = EnemyAction(null, mapOf(),this) {
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

    }

    class TakeCover(
        val cover: IntRange,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val cover = cover.scale(scale * scaleFactor).random()
            return EnemyAction(cover.toString(), mapOf("cover" to cover),this) {
                include(enemy.addCoverTimeline(cover))
            }
        }

    }

    class GivePlayerStatusEffect(
        val statusEffectCreator: StatusEffectCreator,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val statusEffect = statusEffectCreator(controller, null)
            // TODO: fix this
            statusEffect.start(controller) // start effect here because start() needs to be called before getDisplayText()
            val displayText = statusEffect.getDisplayText()
            return EnemyAction(displayText, mapOf("statusEffect" to displayText),this) {
                action {
                    controller.applyStatusEffectToPlayer(statusEffect)
                }
            }
        }

    }

    class GiveSelfStatusEffect(
        val statusEffectCreator: StatusEffectCreator,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val statusEffect = statusEffectCreator(controller, null)
            statusEffect.start(controller) // start effect here because start() needs to be called before getDisplayText()
            val displayText = statusEffect.getDisplayText()
            return EnemyAction(displayText, mapOf("statusEffect" to displayText),this) {
                action {
                    controller.tryApplyStatusEffectToEnemy(statusEffect, enemy)
                }
            }
        }

    }

    class GivePlayerCard(
        val card: String,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val cardTitle = controller.titleOfCard(card)
            return EnemyAction(null, mapOf("card" to cardTitle), this) {
                include(controller.tryToPutCardsInHandTimeline(card))
            }
        }

    }

    companion object {

        fun fromOnj(obj: OnjNamedObject, forEnemy: Enemy): EnemyActionPrototype = when (obj.name) {

            "DestroyCardsInHand" -> DestroyCardsInHand(
                obj.get<Long>("maxCards").toInt(),
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "RotateRevolver" -> RotateRevolver(
                obj.get<Long>("maxTurns").toInt(),
                when (obj.getOr<String?>("forceDirection", null)) {
                    "left" -> RevolverRotation.Left(0)
                    "right" -> RevolverRotation.Right(0)
                    else -> null
                },
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "TakeCover" -> TakeCover(
                obj.get<OnjArray>("cover").toIntRange(),
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "GivePlayerStatusEffect" -> GivePlayerStatusEffect(
                obj.get<StatusEffectCreator>("statusEffect"),
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "ReturnCardToHand" -> ReturnCardToHand(
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "DamagePlayer" -> DamagePlayer(
                obj.get<OnjArray>("damage").toIntRange(),
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "GiveSelfStatusEffect" -> GiveSelfStatusEffect(
                obj.get<StatusEffectCreator>("statusEffect"),
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "GivePlayerCard" -> GivePlayerCard(
                obj.get<String>("card"),
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )

            else -> throw RuntimeException("unknown enemy action: ${obj.name}")

        }.apply {
            iconHandle = obj.get<String>("icon")
            title = obj.get<String>("title")
            descriptionTemplate = obj.get<String>("descriptionTemplate")
            if (!hasSpecialAnimation) return@apply
            commonPanel1 = obj.get<String>("commonPanel1")
            commonPanel2 = obj.get<String>("commonPanel2")
            commonPanel3 = obj.get<String>("commonPanel3")
            specialPanel = obj.get<String>("specialPanel")
        }

    }

}

sealed class NextEnemyAction {

    object None : NextEnemyAction()

    class ShownEnemyAction(val action: EnemyAction) : NextEnemyAction()

    object HiddenEnemyAction : NextEnemyAction()

}
