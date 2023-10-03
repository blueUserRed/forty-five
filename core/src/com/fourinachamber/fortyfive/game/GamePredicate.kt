package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.game.enemy.Enemy

fun interface GamePredicate {

    fun check(controller: GameController): Boolean

    companion object {

        val playerHealthLowerThan = { health: Int -> GamePredicate { controller ->
            controller.curPlayerLives < health
        } }

        val enemyHealthLowerThanPercent = { percent: Float, enemy: Enemy -> GamePredicate {
            enemy.currentHealth / enemy.health < percent
        } }

        val enemyCountHigherIn = { range: IntRange -> GamePredicate { controller ->
            controller.enemyArea.enemies.size in range
        } }

    }
}
