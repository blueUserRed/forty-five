package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.game.enemy.EnemyAction
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
import kotlin.random.Random

class GameDirector(private val controller: GameController) {


    private var turnEstimate by Delegates.notNull<Int>()

//    private var turnRevealTime: Int = -1
//    private var enemyActionTimes: List<Int> = listOf()

    private var difficulty = 0.0

    private lateinit var enemy: Enemy
    private lateinit var enemyProto: EnemyPrototype

    // -1 means EnemyAction, other than -1 means Damage
    private var nextAction: Int? = null

    fun init() {
        val enemiesOnj = OnjParser.parseFile(Gdx.files.internal("config/enemies.onj").file())
        enemiesFileSchema.assertMatches(enemiesOnj)
        enemiesOnj as OnjObject

        difficulty = SaveState.currentDifficulty
        FortyFiveLogger.debug(logTag, "difficulty = $difficulty")
        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
        enemyProto = chooseEnemy(enemyPrototypes)
        FortyFiveLogger.debug(logTag, "chose enemy ${enemyProto.name}")
        turnEstimate = enemyProto.turnCount.random()
        FortyFiveLogger.debug(logTag, "chose $turnEstimate turns")
        enemy = scaleAndCreateEnemy(enemyProto, difficulty)
        FortyFiveLogger.debug(logTag, "enemy: health = ${enemy.health}; damage = ${enemy.damage}")
        controller.initEnemyArea(enemy)
    }

    fun onNewTurn() {
        if (Utils.coinFlip(enemy.attackProbability)) {
            val damage = enemy.damage.random()
            nextAction = damage
            enemy.actor.displayAttackIndicator(damage)
            FortyFiveLogger.debug(logTag, "will attack player with $damage damage the next turn")
        } else if (Utils.coinFlip(enemy.actionProbability)) {
            nextAction = -1
            FortyFiveLogger.debug(logTag, "will execute enemy action the next turn")
        }
    }

    fun checkActions(): Timeline {
        val nextAction = nextAction ?: return Timeline(mutableListOf())
        this.nextAction = null
        if (nextAction != -1) {
            return Timeline.timeline {
                include(controller.enemyAttackTimeline(nextAction))
                action { enemy.actor.hideAttackIndicator() }
            }
        }
        val possibleActions = enemy
            .actions
            .filter { it.second.applicable(controller) }
        if (possibleActions.isEmpty()) {
            FortyFiveLogger.debug(logTag, "wanted to execute enemy action but none where applicable")
            return Timeline(mutableListOf())
        }
        val action = possibleActions.weightedRandom()
        return action.getTimeline(controller)
    }

    fun end() {
        val newDifficulty = adjustDifficulty()
        FortyFiveLogger.debug(logTag, "adjusted difficulty from $difficulty to $newDifficulty")
        SaveState.currentDifficulty = newDifficulty
    }

    private fun adjustDifficulty(): Double {
        // TODO: consider how much damage the player took
        if (controller.playerLost) return difficulty // Too late to adjust difficulty of enemy lol
        val usedTurns = controller.turnCounter
        val targetTurn = turnEstimate
        val enemyHealth = enemy.health
        val enemyHealthPerTurn = enemyProto.baseHealthPerTurn * difficulty
        val enemyBaseHealthPerTurn = enemyProto.baseHealthPerTurn

        val overkillDamage = enemy.currentHealth
        val additionalTurns = floor(abs(overkillDamage) / enemyHealthPerTurn)

        val turnDiff = usedTurns - additionalTurns - targetTurn
        val idealHealth = enemyHealth - enemyHealthPerTurn * turnDiff
        val baseHealth = enemyBaseHealthPerTurn * turnEstimate
        val idealDifficulty = idealHealth / baseHealth
        val difficultyDiff = idealDifficulty - difficulty
        if (difficultyDiff in ((idealDifficulty - 0.2)..(idealDifficulty + 0.2))) {
            return idealDifficulty.coerceAtLeast(0.5)
        }
        return (if (difficultyDiff < 0.0) difficulty - 0.2 else difficulty + 0.2).coerceAtLeast(0.5)
    }

    private fun chooseEnemy(prototypes: List<EnemyPrototype>): EnemyPrototype {
        return prototypes.random()
    }

    private fun scaleAndCreateEnemy(prototype: EnemyPrototype, difficulty: Double): Enemy {
        val health = (prototype.baseHealthPerTurn * turnEstimate * difficulty).toInt()
        // damage is not scaled because there is currently no way for the player to get more health
        val damage = prototype.baseDamage
        return prototype.create(health, damage)
    }

    companion object {

        const val logTag = "director"

        private val enemiesFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/enemies.onjschema").file())
        }

    }
}
