package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.game.enemy.EnemyPrototype
import com.fourinachamber.fortyfive.game.enemy.NextEnemyAction
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

class GameDirector(private val controller: GameController) {

    private var difficulty = 0.0

    private lateinit var enemy: Enemy
    private lateinit var enemyProto: EnemyPrototype

    fun init() {
        val enemiesOnj = OnjParser.parseFile(Gdx.files.internal("config/enemies.onj").file())
        enemiesFileSchema.assertMatches(enemiesOnj)
        enemiesOnj as OnjObject

        difficulty = SaveState.currentDifficulty
        FortyFiveLogger.debug(logTag, "difficulty = $difficulty")
        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
        enemyProto = chooseEnemy(enemyPrototypes)
        FortyFiveLogger.debug(logTag, "chose enemy ${enemyProto.name}")
        enemy = scaleAndCreateEnemy(enemyProto, difficulty)
        FortyFiveLogger.debug(logTag, "enemy: health = ${enemy.health}")
        controller.initEnemyArea(enemy)
    }

    fun onNewTurn() {
        val nextAction = enemy.brain.chooseNewAction(controller, enemy, difficulty)
        enemy.actor.setupForAction(NextEnemyAction.None) // make sure current action is cleared
        enemy.actor.setupForAction(nextAction)
    }

    fun checkActions(): Timeline = Timeline.timeline {
        action {
            enemy.actor.setupForAction(NextEnemyAction.None)
        }
        enemy.brain.resolveEnemyAction(controller, enemy, difficulty)?.getTimeline()?.let { include(it) }
    }

    fun end() {
        val newDifficulty = adjustDifficulty()
        FortyFiveLogger.debug(logTag, "adjusted difficulty from $difficulty to $newDifficulty")
        SaveState.currentDifficulty = newDifficulty
    }

    private fun adjustDifficulty(): Double {
        return difficulty
//        if (controller.playerLost) return difficulty // Too late to adjust difficulty of enemy lol
//        val usedTurns = controller.turnCounter
//        val enemyHealth = enemy.health
//        val enemyHealthPerTurn = scaleEnemyHealthPerTurn(enemyProto.baseHealth, difficulty)
//        val enemyBaseHealth = enemyProto.baseHealth
//
//        val overkillDamage = enemy.currentHealth
//
//        val damage = controller.playerLivesAtStart - controller.curPlayerLives
//        val damageDiff = damageEstimate - damage
//        val baseDamageDiff = damageDiff / turnEstimate
//        val idealDifficultyBasedOnDamage = difficulty + baseDamageDiff
//
//        val idealDifficulty = (idealDifficultyBasedOnTurns / 2) + (idealDifficultyBasedOnDamage / 2)
//        val difficultyDiff = idealDifficulty - difficulty
//
//        FortyFiveLogger.debug(logTag, "difficulty calculation: " +
//                "idealDifficultyBasedOnTurns = $idealDifficultyBasedOnTurns, " +
//                "idealDifficultyBasedOnDamage = $idealDifficultyBasedOnDamage, " +
//                "idealDifficulty = $idealDifficulty")
//
//
//        if (difficultyDiff in ((idealDifficulty - 0.2)..(idealDifficulty + 0.2))) {
//            return idealDifficulty.coerceAtLeast(0.5)
//        }
//        return (if (difficultyDiff < 0.0) difficulty - 0.2 else difficulty + 0.2).coerceAtLeast(0.5)
    }

    private fun chooseEnemy(prototypes: List<EnemyPrototype>): EnemyPrototype {
        return prototypes.random()
    }

    private fun scaleAndCreateEnemy(prototype: EnemyPrototype, difficulty: Double): Enemy {
        val health = scaleEnemyHealthPerTurn(prototype.baseHealth, difficulty)
        return prototype.create(health)
    }

    private fun scaleEnemyHealthPerTurn(healthPerTurn: Int, difficulty: Double): Int =
        (healthPerTurn * difficulty).toInt()

    companion object {

        const val logTag = "director"

        private val enemiesFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/enemies.onjschema").file())
        }

    }
}
