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

class GameDirector(private val controller: GameController) {

    private var difficulty = 0.0

    private lateinit var enemy: Enemy
    private lateinit var enemyProto: EnemyPrototype

    private var nextAction: NextAction = NextAction.None

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
        enemy.actor.setupForAction(NextAction.None) // make sure current action is cleared
        if (!Utils.coinFlip(enemy.actionProbability) || enemy.actionPrototypes.isEmpty()) {
            nextAction = NextAction.None
            return
        }
        // TODO: this algorithm for choosing enemy actions is not ideal, because it doesn't respect the weights
        // correctly in certain scenarios
        val possibleActions = enemy
            .actionPrototypes
            .filter { (_, action) -> action.showProbability <= 0f || action.applicable(controller) }
            .filter { (_, action) -> action.showProbability > 0f || !action.hasUnlikelyPredicates }
        if (possibleActions.isEmpty()) {
            FortyFiveLogger.debug(logTag, "Wanted to execute enemy action but none was applicable")
            return
        }
        val chosenAction = possibleActions.weightedRandom()
        val isShown = Utils.coinFlip(chosenAction.showProbability)
        nextAction = if (isShown) {
            NextAction.ShownEnemyAction(chosenAction.create(controller, difficulty))
        } else {
            NextAction.HiddenEnemyAction
        }
        enemy.actor.setupForAction(nextAction)
        FortyFiveLogger.debug(logTag,
            "executing enemy action next turn; isShown = $isShown; chosenAction = $chosenAction")
    }

    fun checkActions(): Timeline = when (val nextAction = nextAction) {

        is NextAction.None -> Timeline()

        is NextAction.ShownEnemyAction -> {
            if (!nextAction.action.prototype.applicable(controller)) {
                FortyFiveLogger.warn(logTag, "Enemy action ${nextAction.action} was chosen to be shown " +
                        "but when the applicable() function was checked it returned false; bailing out")
                Timeline()
            } else {
                nextAction.action.getTimeline()
            }
        }

        is NextAction.HiddenEnemyAction -> {
            val possibleActions = enemy
                .actionPrototypes
                .filter { (_, action) -> action.showProbability <= 0f }
                .filter { (_, action) -> action.applicable(controller) }
            if (possibleActions.isEmpty()) {
                FortyFiveLogger.warn(logTag, "encountered issue when executing HiddenEnemyAction: " +
                        "preferred action was not applicable and no replacement action could be found; returning " +
                        "empty timeline")
                Timeline()
            } else {
                possibleActions
                    .weightedRandom()
                    .create(controller, difficulty)
                    .getTimeline()
            }
        }

    }.apply {
        appendAction(Timeline.timeline {
            action {
                enemy.actor.setupForAction(NextAction.None)
            }
        }.asAction())
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

    sealed class NextAction {

        object None : NextAction()

        class ShownEnemyAction(val action: EnemyAction) : NextAction()

        object HiddenEnemyAction : NextAction()

    }

    companion object {

        const val logTag = "director"

        private val enemiesFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/enemies.onjschema").file())
        }

    }
}
