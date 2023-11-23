package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.utils.toIntRange
import onj.value.OnjArray
import onj.value.OnjNamedObject
import kotlin.reflect.KClass

fun interface GamePredicate {

    fun check(controller: GameController): Boolean

    companion object {

        val playerHealthLowerThan = { health: Int -> GamePredicate { controller ->
            controller.curPlayerLives < health
        } }

        val enemyHealthLowerThanPercent = { percent: Float, enemy: Enemy -> GamePredicate {
            enemy.currentHealth / enemy.health < percent
        } }

        val aliveEnemyCountIn = { range: IntRange -> GamePredicate { controller ->
            controller.enemyArea.enemies.filter { !it.isDefeated }.size in range
        } }

        val anyEnemyHasStatusEffect = { statusEffect: StatusEffect -> GamePredicate { controller ->
            controller.activeEnemies.any { statusEffect in it.statusEffect }
        } }

        val enemyDoesNotHaveStatusEffect = { statusEffect: StatusEffect, enemy: Enemy -> GamePredicate {
            statusEffect in enemy.statusEffect
        } }

        val playerHasStatusEffect = { statusEffect: StatusEffect -> GamePredicate { controller ->
            statusEffect in controller.playerStatusEffects
        } }

        val negatePredicate = { other: GamePredicate -> GamePredicate { controller ->
            !other.check(controller)
        } }

        val targetedEnemyShieldIsAtLeast = { value: Int -> GamePredicate { controller ->
            controller.enemyArea.getTargetedEnemy().currentCover >= value
        } }


        fun fromOnj(obj: OnjNamedObject, inContextOfEnemy: Enemy? = null): GamePredicate = when (obj.name) {

            "PlayerHealthLowerThan" -> playerHealthLowerThan(obj.get<Long>("value").toInt())
            "EnemyHealthLowerThanPercent" -> enemyHealthLowerThanPercent(
                obj.get<Double>("value").toFloat(),
                inContextOfEnemy ?: throw RuntimeException("EnemyLowerThanPercent Predicate can only be created when" +
                        " an enemy is passed into the fromOnj function")
            )
            "AliveEnemyCountIn" -> aliveEnemyCountIn(obj.get<OnjArray>("value").toIntRange())
            "AnyEnemyHasStatusEffect" -> anyEnemyHasStatusEffect(obj.get<StatusEffectCreator>("value")())
            "EnemyDoesNotHaveStatusEffect" -> enemyDoesNotHaveStatusEffect(
                obj.get<StatusEffectCreator>("value")(),
                inContextOfEnemy ?: throw RuntimeException("EnemyDoesNotHaveStatusEffect Predicate can only be created" +
                        " when an enemy is passed into the fromOnj function")
            )
            "PlayerHasStatusEffect" -> playerHasStatusEffect(obj.get<StatusEffectCreator>("value")())
            "NegatePredicate" -> negatePredicate(fromOnj(obj.get<OnjNamedObject>("value"), inContextOfEnemy))
            "TargetedEnemyShieldIsAtLeast" -> targetedEnemyShieldIsAtLeast(obj.get<Long>("value").toInt())

            else -> throw RuntimeException("unknown gamePredicate ${obj.name}")

        }

    }
}
