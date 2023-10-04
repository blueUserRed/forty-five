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
            controller.enemyArea.enemies.any { statusEffect in it.statusEffect }
        } }

        val enemyDoesNotHaveStatusEffect = { statusEffect: StatusEffect, enemy: Enemy -> GamePredicate {
            statusEffect in enemy.statusEffect
        } }


        fun fromOnj(obj: OnjNamedObject, inContextOfEnemy: Enemy? = null) = when (obj.name) {

            "PlayerHealthLowerThan" -> playerHealthLowerThan(obj.get<Long>("value").toInt())
            "EnemyHealthLowerThanPercent" -> enemyHealthLowerThanPercent(
                obj.get<Double>("value").toFloat(),
                inContextOfEnemy ?: throw RuntimeException("EnemyLowerThanPercent Predicate can only be created when" +
                        " an enemy is passed into the fromOnj function")
            )
            "AliveEnemyCountIn" -> aliveEnemyCountIn(obj.get<OnjArray>("value").toIntRange())
            "AnyEnemyHasStatusEffect" -> anyEnemyHasStatusEffect(obj.get<StatusEffect>("value"))
            "EnemyDoesNotHaveStatusEffect" -> enemyDoesNotHaveStatusEffect(
                obj.get<StatusEffect>("value"),
                inContextOfEnemy ?: throw RuntimeException("EnemyDoesNotHaveStatusEffect Predicate can only be created" +
                        " when an enemy is passed into the fromOnj function")
            )

            else -> throw RuntimeException("unknown gamePredicate ${obj.name}")

        }

    }
}
