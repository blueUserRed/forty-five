package com.microwavestudios.fortyfive.game.enemy

import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.StatusEffectCreator
import com.microwavestudios.fortyfive.game.StatusEffectTarget
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class EnemyAction(
    val indicatorText: String?,
    val descriptionParams: Map<String, Any>,
    val prototype: EnemyActionPrototype,
    val damageChanges: List<Pair<String, Int>> = listOf(),
    private val timelineCreator: Timeline.TimelineBuilderDSL.() -> Unit
) {

    fun getTimeline(): Timeline = Timeline.timeline { timelineCreator(this) }

}

typealias EnemyActionCreator = () -> EnemyAction

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

    abstract fun newCreator(controller: GameController, scale: Double): EnemyActionCreator

    fun getAdditionalDamage(
        originalDamage: Int,
        controller: GameController,
    ): List<Pair<String, Int>> {
        val playerModifiers = controller
            .playerStatusEffects
            .zip { it.additionalEnemyDamage(originalDamage, StatusEffectTarget.PlayerTarget) }
            .filter { it.second != 0 }
            .mapFirst { it.iconHandle }
        val enemyModifiers = enemy
            .statusEffects
            .zip { it.additionalEnemyDamage(originalDamage, StatusEffectTarget.EnemyTarget(enemy)) }
            .filter { it.second != 0 }
            .mapFirst { it.iconHandle }
        return playerModifiers + enemyModifiers
    }

    class DamagePlayer(
        val damage: IntRange,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator {
            val damage = damage.scale(scale * scaleFactor).random(controller.random)
            return {
                val additional = getAdditionalDamage(damage, controller)
                val newDamage = damage + additional.sumOf { it.second }
                EnemyAction(damage.toString(), mapOf("damage" to newDamage), this, additional) {
                    include(controller.enemyAttackTimeline(newDamage, enemy))
                }
            }
        }
    }

    class DestroyCardsInHand(
        val maxCards: Int,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator {
            val cardAmount = controller.cardsInHand.size
            val random = controller.random
            val amountToDestroy = (1..maxCards).random(random).coerceAtMost(cardAmount - 1)
            return {
                EnemyAction(amountToDestroy.toString(), mapOf("amount" to amountToDestroy),this) {
                    repeat(amountToDestroy) {
                        // might cause mismatches when this action is shown instead of hidden
                        later {
                            if (controller.cardsInHand.isEmpty()) return@later
                            val card = controller.cardsInHand[(0 until controller.cardsInHand.size).random(random)]
                            include(controller.destroyCardInHandTimeline(card))
                        }
                    }
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

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator {
            val random = controller.random
            val amount = (1..maxTurnAmount).random(random)
            val rotation = if (forceDirection == null) {
                if (random.nextBoolean()) {
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
            return {
                EnemyAction(amount.toString(), descriptionParams, this) {
                    include(controller.rotateRevolverTimeline(rotation))
                }
            }
        }

    }

    class ReturnCardToHand(
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator = {
            EnemyAction(null, mapOf(),this) {
                later {
                    controller
                        .revolver
                        .slots
                        .mapNotNull { it.card }
                        .randomOrNull(controller.random)
                        ?.let { include(controller.bounceBulletTimeline(it)) }
                }
            }
        }
    }

    class TakeCover(
        val cover: IntRange,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator {
            val cover = cover.scale(scale * scaleFactor).random(controller.random)
            return {
                EnemyAction(cover.toString(), mapOf("cover" to cover),this) {
                    include(enemy.addCoverTimeline(cover))
                }
            }
        }

    }

    class GivePlayerStatusEffect(
        val statusEffectCreator: StatusEffectCreator,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator {
            // the creator will return an action that uses the same status effect every time, this can cause issues
            // when the action was already executed and then a new one is created, but this isn't how this feature is
            // used in practice
            val statusEffect = statusEffectCreator(controller, null, false)
            // TODO: fix this
            statusEffect.start(controller) // start effect here because start() needs to be called before getDisplayText()
            val displayText = statusEffect.getDisplayText()
            return {
                EnemyAction(null, mapOf("statusEffect" to displayText),this) {
                    include(controller.tryApplyStatusEffectToPlayerTimeline(statusEffect))
                }
            }
        }

    }

    class GiveSelfStatusEffect(
        val statusEffectCreator: StatusEffectCreator,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator {
            val statusEffect = statusEffectCreator(controller, null, false)
            statusEffect.start(controller) // start effect here because start() needs to be called before getDisplayText()
            val displayText = statusEffect.getDisplayText()
            return {
                EnemyAction(null, mapOf("statusEffect" to displayText),this) {
                    include(controller.tryApplyStatusEffectToEnemyTimeline(statusEffect, enemy))
                }
            }
        }

    }

    class GivePlayerCard(
        val card: CardType,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator {
            val cardTitle = controller.titleOfCard(card)
            return {
                EnemyAction(null, mapOf("card" to cardTitle), this) {
                    include(controller.tryToPutCardsInHandTimeline(card))
                }
            }
        }

    }

    class MarkCards(
        private val amountToMark: IntRange,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator {
            val amount = amountToMark.random(controller.random)
            return {
                EnemyAction(null, mapOf("amount" to amount), this) {
                    action {
                        controller
                            .cardsInHand
                            .shuffled()
                            .take(amount)
                            .forEach { it.isMarked = true }
                    }
                }
            }
        }
    }

    class PutMarkedCardsUnderDeck(
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(
            controller: GameController,
            scale: Double
        ): EnemyActionCreator = {
            EnemyAction(null, mapOf(), this) {
                includeLater(
                    {
                        controller
                            .cardsInHand
                            .filter { it.isMarked }
                            .map { controller.putBulletFromRevolverUnderTheStackTimeline(it) }
                            .collectTimeline()
                    }
                )
            }
        }
    }

    class PiercingDamage(
        private val damage: IntRange,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator {
            val damage = damage.scale(scale * scaleFactor).random(controller.random)
            return {
                val additional = getAdditionalDamage(damage, controller)
                val newDamage = damage + additional.sumOf { it.second }
                EnemyAction(damage.toString(), mapOf("damage" to newDamage), this, additional) {
                    include(controller.enemyAttackTimeline(newDamage, enemy, isPiercing = true))
                }
            }
        }
    }

    class PutBulletFromRevolverUnderDeck(
        private val possibleSlots: List<Int>,
        enemy: Enemy,
        hasSpecialAnimation: Boolean
    ) : EnemyActionPrototype(enemy, hasSpecialAnimation) {

        override fun newCreator(controller: GameController, scale: Double): EnemyActionCreator {
            val slot = possibleSlots.random(controller.random)
            return {
                EnemyAction(
                    null,
                    mapOf("slot" to Utils.convertSlotRepresentation(slot)),
                    this,
                ) {
                    controller.revolver.getCardInSlot(slot)?.let { card ->
                        include(controller.putBulletFromRevolverUnderTheStackTimeline(card))
                    }
                }
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
                CardType.fromOnj(obj.get<OnjObject>("card")),
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "PiercingDamage" -> PiercingDamage(
                obj.get<OnjArray>("damage").toIntRange(),
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "PutBulletFromRevolverUnderDeck" -> PutBulletFromRevolverUnderDeck(
                obj
                    .get<OnjArray>("possibleSlots")
                    .value
                    .map { (it.value as Long).toInt() }
                    .map { Utils.convertSlotRepresentation(it) },
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "MarkCards" -> MarkCards(
                obj.get<OnjArray>("amountToMark").toIntRange(),
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )
            "PutMarkedCardsUnderDeck" -> PutMarkedCardsUnderDeck(
                forEnemy,
                obj.get<Boolean>("hasSpecialAnimation")
            )

            else -> throw RuntimeException("unknown enemy action: ${obj.name}")

        }.apply {
            iconHandle = obj.get<String>("icon")
            title = obj.get<String>("title")
            descriptionTemplate = obj.get<String>("descriptionTemplate")
            scaleFactor = obj.getOr("scaleFactor", 1f)
            if (!hasSpecialAnimation) return@apply
            commonPanel1 = obj.get<String>("commonPanel1")
            commonPanel2 = obj.get<String>("commonPanel2")
            commonPanel3 = obj.get<String>("commonPanel3")
            specialPanel = obj.get<String>("specialPanel")
        }

    }

}

sealed class NextEnemyAction {

    data object None : NextEnemyAction()

    class ShownEnemyAction(val action: EnemyAction) : NextEnemyAction()

    data object HiddenEnemyAction : NextEnemyAction()

}
