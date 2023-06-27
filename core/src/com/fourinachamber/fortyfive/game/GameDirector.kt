package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.game.enemy.EnemyPrototype
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.properties.Delegates

class GameDirector(private val controller: GameController) {


    private var turns by Delegates.notNull<Int>()

    private var turnRevealTime: Int = -1
    private var enemyActionTimes: List<Int> = listOf()

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
        turns = enemyProto.turnCount.random()
        FortyFiveLogger.debug(logTag, "chose $turns turns")
        if (turns >= 12) {
            turnRevealTime = (turns * (3.0 / 4.0)).toInt()
            enemyActionTimes = listOf((turns * (1.0 / 4.0)).toInt(), (turns * (2.0 / 4.0)).toInt())
        } else if (turns >= 9) {
            turnRevealTime = (turns * (2.0 / 3.0)).toInt()
            enemyActionTimes = listOf((turns * (1.0 / 3.0)).toInt())
        } else {
            controller.remainingTurns = turns
            enemyActionTimes = listOf((turns / 2.0).toInt())
        }
        FortyFiveLogger.debug(logTag, "turnRevealTime = $turnRevealTime; enemyActionTimes = $enemyActionTimes")
        enemy = scaleAndCreateEnemy(enemyProto, difficulty)
        FortyFiveLogger.debug(logTag, "enemy: health = ${enemy.health}; damage = ${enemy.damage}")
        controller.initEnemyArea(enemy)
    }

    fun checkActions(): Timeline {
        if (controller.turnCounter == turnRevealTime) {
            return Timeline.timeline {
                val remainingTurns = turns - controller.turnCounter
                action {
                    controller.remainingTurns = remainingTurns
                }
                include(controller.confirmationPopup("You have $remainingTurns left!"))
            }
        } else if (controller.turnCounter in enemyActionTimes) {
            return doEnemyAction()
        }
        return Timeline(mutableListOf())
    }

    fun end() {
        val newDifficulty = adjustDifficulty()
        FortyFiveLogger.debug(logTag, "adjusted difficulty from $difficulty to $newDifficulty")
        SaveState.currentDifficulty = newDifficulty
    }

    private fun adjustDifficulty(): Double {
        if (controller.playerLost) return difficulty // Too late to adjust difficulty of enemy lol
        val usedTurns = controller.turnCounter
        val targetTurn = floor(turns * (4f / 5f))
        val enemyHealth = enemy.health
        val enemyHealthPerTurn = enemyProto.baseHealthPerTurn * difficulty
        val enemyBaseHealthPerTurn = enemyProto.baseHealthPerTurn

        val overkillDamage = enemy.currentHealth
        val additionalTurns = floor(abs(overkillDamage) / enemyHealthPerTurn)

        val turnDiff = usedTurns - additionalTurns - targetTurn
        val idealHealth = enemyHealth - enemyHealthPerTurn * turnDiff
        val baseHealth = enemyBaseHealthPerTurn * turns
        val idealDifficulty = idealHealth / baseHealth
        val difficultyDiff = idealDifficulty - difficulty
        if (difficultyDiff in ((idealDifficulty - 0.2)..(idealDifficulty + 0.2))) {
            return idealDifficulty.coerceAtLeast(0.5)
        }
        return (if (difficultyDiff < 0.0) difficulty - 0.2 else difficulty + 0.2).coerceAtLeast(0.5)
    }

    private fun doEnemyAction(): Timeline {
        val enemy = controller.enemyArea.enemies[0]
        val possibleActions = enemy
            .actions
            .filter { it.second.applicable(controller) }
        if (possibleActions.isEmpty()) return Timeline(mutableListOf())
        val action = possibleActions.weightedRandom()
        return action.getTimeline(controller)
    }

    private fun chooseEnemy(prototypes: List<EnemyPrototype>): EnemyPrototype {
        return prototypes.random()
    }

    private fun scaleAndCreateEnemy(prototype: EnemyPrototype, difficulty: Double): Enemy {
        val health = (prototype.baseHealthPerTurn * turns * difficulty).toInt()
        val damage = (prototype.baseDamage * difficulty).toInt()
        return prototype.create(health, damage)
    }

    companion object {

        const val logTag = "director"

        private val enemiesFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/enemies.onjschema").file())
        }

    }
}
