package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.GameDirector
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.Utils
import com.fourinachamber.fortyfive.utils.weightedRandom
import onj.value.OnjArray
import onj.value.OnjNamedObject

abstract class EnemyBrain {

    abstract fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction?

    abstract fun chooseNewAction(controller: GameController, enemy: Enemy, difficulty: Double): NextEnemyAction

    companion object {

        fun fromOnj(onj: OnjNamedObject, enemy: Enemy): EnemyBrain = when (onj.name) {

            "RandomEnemyBrain" -> RandomEnemyBrain(
                onj
                    .get<OnjArray>("actions")
                    .value
                    .map { it as OnjNamedObject }
                    .map { it.get<Long>("weight").toInt() to EnemyActionPrototype.fromOnj(it, enemy) },
                onj.get<Double>("actionProbability").toFloat()
            )

            else -> throw RuntimeException("unknown EnemyBrain ${onj.name}")
        }

    }

}

class RandomEnemyBrain(
    private val actionPrototypes: List<Pair<Int, EnemyActionPrototype>>,
    private val actionProbability: Float
) : EnemyBrain() {

    private var nextEnemyAction: NextEnemyAction = NextEnemyAction.None

    override fun resolveEnemyAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double
    ): EnemyAction? = when (val nextAction = nextEnemyAction) {

        is NextEnemyAction.None -> null

        is NextEnemyAction.ShownEnemyAction -> {
            if (!nextAction.action.prototype.applicable(controller)) {
                FortyFiveLogger.warn(
                    GameDirector.logTag, "Enemy action ${nextAction.action} was chosen to be shown " +
                            "but when the applicable() function was checked it returned false; bailing out"
                )
                null
            } else {
                nextAction.action
            }
        }

        is NextEnemyAction.HiddenEnemyAction -> {
            val possibleActions = actionPrototypes
                .filter { (_, action) -> action.showProbability <= 0f }
                .filter { (_, action) -> action.applicable(controller) }
            if (possibleActions.isEmpty()) {
                FortyFiveLogger.warn(
                    GameDirector.logTag, "encountered issue when executing HiddenEnemyAction: " +
                            "preferred action was not applicable and no replacement action could be found; returning " +
                            "empty timeline"
                )
                null
            } else {
                possibleActions
                    .weightedRandom()
                    .create(controller, difficulty)
            }
        }
    }

    override fun chooseNewAction(controller: GameController, enemy: Enemy, difficulty: Double): NextEnemyAction = run {
        if (!Utils.coinFlip(actionProbability) || actionPrototypes.isEmpty()) {
            return@run NextEnemyAction.None
        }
        // TODO: this algorithm for choosing enemy actions is not ideal, because it doesn't respect the weights
        // correctly in certain scenarios
        val possibleActions = actionPrototypes
            .filter { (_, action) -> action.showProbability <= 0f || action.applicable(controller) }
            .filter { (_, action) -> action.showProbability > 0f || !action.hasUnlikelyPredicates }
        if (possibleActions.isEmpty()) {
            FortyFiveLogger.debug(GameDirector.logTag, "Wanted to execute enemy action but none was applicable")
            return@run NextEnemyAction.None
        }
        val chosenAction = possibleActions.weightedRandom()
        val isShown = Utils.coinFlip(chosenAction.showProbability)
        return@run if (isShown) {
            NextEnemyAction.ShownEnemyAction(chosenAction.create(controller, difficulty))
        } else {
            NextEnemyAction.HiddenEnemyAction
        }
    }.also {
        nextEnemyAction = it
    }
}

object NoOpEnemyBrain : EnemyBrain() {

    override fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction? = null

    override fun chooseNewAction(controller: GameController, enemy: Enemy, difficulty: Double): NextEnemyAction = NextEnemyAction.None
}
