package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.utils.toIntRange
import onj.value.OnjArray
import onj.value.OnjNamedObject

fun interface GamePredicate {

    fun check(controller: GameController): Boolean

    companion object {

        val playerHealthLowerThan = { health: Int -> GamePredicate { controller ->
            controller.curPlayerLives < health
        } }

        val enemyHealthLowerThanPercent = { percent: Float, enemy: Enemy -> GamePredicate {
            enemy.currentHealth / enemy.health < percent
        } }

        val enemyCountIn = { range: IntRange -> GamePredicate { controller ->
            controller.enemyArea.enemies.size in range
        } }


        fun fromOnj(obj: OnjNamedObject, inContextOfEnemy: Enemy? = null) = when (obj.name) {

            "PlayerHealthLowerThan" -> playerHealthLowerThan(obj.get<Long>("value").toInt())
            "EnemyLowerThanPercent" -> enemyHealthLowerThanPercent(
                obj.get<Double>("value").toFloat(),
                inContextOfEnemy ?: throw RuntimeException("EnemyLowerThanPercent Predicate can only be created when" +
                        " an enemy is passed into the fromOnj function")
            )
            "EnemyCountIn" -> enemyCountIn(obj.get<OnjArray>("value").toIntRange())

            else -> throw RuntimeException("unknown gamePredicate ${obj.name}")

        }

    }
}
