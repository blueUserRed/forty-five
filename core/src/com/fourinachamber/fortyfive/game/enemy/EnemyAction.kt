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
    val showProbability: Float,
    protected val enemy: Enemy,
    val hasUnlikelyPredicates: Boolean,
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
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates, hasSpecialAnimation) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val damage = damage.scale(scale * scaleFactor).random()
            return EnemyAction(damage.toString(), mapOf("damage" to damage), this) {
                include(controller.enemyAttackTimeline(damage))
            }
        }
    }

    class DestroyCardsInHand(
        val maxCards: Int,
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates, hasSpecialAnimation) {

        override fun applicable(controller: GameController): Boolean =
            checkPredicates(controller) && controller.cardHand.cards.isNotEmpty()

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
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates, hasSpecialAnimation) {

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

        override fun applicable(controller: GameController): Boolean =
            checkPredicates(controller) && controller.revolver.slots.any { it.card != null }

    }

    class ReturnCardToHand(
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates, hasSpecialAnimation) {

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
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates, hasSpecialAnimation) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val cover = cover.scale(scale * scaleFactor).random()
            return EnemyAction(cover.toString(), mapOf("cover" to cover),this) {
                include(enemy.addCoverTimeline(cover))
            }
        }

    }

    class GivePlayerStatusEffect(
        val statusEffectCreator: StatusEffectCreator,
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates, hasSpecialAnimation) {

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


        override fun applicable(controller: GameController): Boolean =
            checkPredicates(controller) && controller.isStatusEffectApplicable(statusEffectCreator(controller, null))
    }

    class GiveSelfStatusEffect(
        val statusEffectCreator: StatusEffectCreator,
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates, hasSpecialAnimation) {

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
        showProbability: Float,
        enemy: Enemy,
        hasUnlikelyPredicates: Boolean,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(showProbability, enemy, hasUnlikelyPredicates, hasSpecialAnimation) {

        override fun create(controller: GameController, scale: Double): EnemyAction {
            val cardTitle = controller.titleOfCard(card)
            return EnemyAction(null, mapOf("card" to cardTitle), this) {
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
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "RotateRevolver" -> RotateRevolver(
                obj.get<Long>("maxTurns").toInt(),
                when (obj.getOr<String?>("forceDirection", null)) {
                    "left" -> RevolverRotation.Left(0)
                    "right" -> RevolverRotation.Right(0)
                    else -> null
                },
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "TakeCover" -> TakeCover(
                obj.get<OnjArray>("cover").toIntRange(),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "GivePlayerStatusEffect" -> GivePlayerStatusEffect(
                obj.get<StatusEffectCreator>("statusEffect"),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "ReturnCardToHand" -> ReturnCardToHand(
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "DamagePlayer" -> DamagePlayer(
                obj.get<OnjArray>("damage").toIntRange(),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "GiveSelfStatusEffect" -> GiveSelfStatusEffect(
                obj.get<StatusEffectCreator>("statusEffect"),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "GivePlayerCard" -> GivePlayerCard(
                obj.get<String>("card"),
                obj.get<Double>("showProbability").toFloat(),
                forEnemy,
                obj.getOr("hasUnlikelyPredicates", false),
                obj.get<Boolean>("hasSpecialAnimation")
            )

            else -> throw RuntimeException("unknown enemy action: ${obj.name}")

        }.apply {
            scaleFactor = obj.getOr("scaleFactor", 1f)
            iconHandle = obj.get<String>("icon")
            title = obj.get<String>("title")
            descriptionTemplate = obj.get<String>("descriptionTemplate")
            val predicates = obj.getOr<OnjArray?>("predicates", null)
            predicates
                ?.value
                ?.map { GamePredicate.fromOnj(it as OnjNamedObject, forEnemy) }
                ?.forEach { addPredicate(it) }
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
