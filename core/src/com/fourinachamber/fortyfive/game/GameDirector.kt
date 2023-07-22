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


    private var turnEstimate by Delegates.notNull<Int>()

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
//        controller.initEnemyArea(enemy)
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
        if (controller.playerLost) return difficulty // Too late to adjust difficulty of enemy lol
        val usedTurns = controller.turnCounter
        val targetTurn = turnEstimate
        val enemyHealth = enemy.health
        val enemyHealthPerTurn = scaleEnemyHealthPerTurn(enemyProto.baseHealthPerTurn, difficulty)
        val enemyBaseHealthPerTurn = enemyProto.baseHealthPerTurn

        val overkillDamage = enemy.currentHealth
        val additionalTurns = abs(overkillDamage) / enemyHealthPerTurn
        val turnDiff = usedTurns - additionalTurns - targetTurn
        val idealHealth = enemyHealth - enemyHealthPerTurn * turnDiff
        val baseHealth = enemyBaseHealthPerTurn * turnEstimate
        val idealDifficultyBasedOnTurns = idealHealth / baseHealth

        val damageEstimate = turnEstimate * 0.5
        val damage = controller.playerLivesAtStart - controller.curPlayerLives
        val damageDiff = damageEstimate - damage
        val baseDamageDiff = damageDiff / turnEstimate
        val idealDifficultyBasedOnDamage = difficulty + baseDamageDiff

        val idealDifficulty = (idealDifficultyBasedOnTurns / 2) + (idealDifficultyBasedOnDamage / 2)
        val difficultyDiff = idealDifficulty - difficulty

        FortyFiveLogger.debug(logTag, "difficulty calculation: " +
                "idealDifficultyBasedOnTurns = $idealDifficultyBasedOnTurns, " +
                "idealDifficultyBasedOnDamage = $idealDifficultyBasedOnDamage, " +
                "idealDifficulty = $idealDifficulty")


        if (difficultyDiff in ((idealDifficulty - 0.2)..(idealDifficulty + 0.2))) {
            return idealDifficulty.coerceAtLeast(0.5)
        }
        return (if (difficultyDiff < 0.0) difficulty - 0.2 else difficulty + 0.2).coerceAtLeast(0.5)
    }

    private fun chooseEnemy(prototypes: List<EnemyPrototype>): EnemyPrototype {
        return prototypes.random()
    }

    private fun scaleAndCreateEnemy(prototype: EnemyPrototype, difficulty: Double): Enemy {
        val health = scaleEnemyHealthPerTurn(prototype.baseHealthPerTurn, difficulty) * turnEstimate
        val damage = scaleEnemyDamage(prototype.baseDamage, difficulty)
        return prototype.create(health, damage)
    }

    private fun scaleEnemyHealthPerTurn(healthPerTurn: Int, difficulty: Double): Int =
        (healthPerTurn * difficulty).toInt()

    private fun scaleEnemyDamage(baseDamage: IntRange, difficulty: Double): IntRange {
        val adjustedDifficulty = if (difficulty > 1.0) {
            1 + ((difficulty - 1) * 0.5) // difficulty scales slower for damage than for health
        } else {
            difficulty
        }
        return IntRange((baseDamage.first * adjustedDifficulty).toInt(), (baseDamage.last * adjustedDifficulty).toInt())
    }

    companion object {

        const val logTag = "director"

        private val enemiesFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/enemies.onjschema").file())
        }

    }
}
