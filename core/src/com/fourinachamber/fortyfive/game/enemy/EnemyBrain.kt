package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.controller.GameController
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

abstract class EnemyBrain {

    abstract fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction?

    abstract fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<NextEnemyAction>
    ): NextEnemyAction

    companion object {

        fun fromOnj(onj: OnjNamedObject, enemy: Enemy): EnemyBrain = when (onj.name) {

            "NewEnemyBrain" -> NewEnemyBrain(onj, enemy)

            "ScriptedEnemyBrain" -> ScriptedEnemyBrain(
                onj.get<OnjArray>("actions"),
                enemy
            )

            "SniperEnemyBrain" -> SniperEnemyBrain(
                onj,
                enemy
            )

            else -> throw RuntimeException("unknown EnemyBrain ${onj.name}")
        }
    }

    protected fun damagePlayer(range: IntRange, enemy: Enemy) = EnemyActionPrototype.DamagePlayer(
        range, enemy, false
    ).apply {
        iconHandle = "enemy_action_damage"
        title = "Damage"
        descriptionTemplate = "This attack deals {damage} damage"
    }

    protected fun takeCover(range: IntRange, enemy: Enemy) = EnemyActionPrototype.TakeCover(
        range, enemy, false
    ).apply {
        iconHandle = "enemy_action_cover"
        title = "Cover"
        descriptionTemplate = "The enemy adds {cover} shield"
    }

    protected fun List<NextEnemyAction>.containsAttack(): Boolean =
        any { it is NextEnemyAction.ShownEnemyAction && it.action.prototype is EnemyActionPrototype.DamagePlayer }

}

open class NewEnemyBrain(onj: OnjObject, private val enemy: Enemy) : EnemyBrain() {

    private val damageShieldWeight: Float = onj.get<Double>("damageShieldWeight").toFloat()
    private val normalSpecialActionWeight: Float = onj.get<Double>("normalSpecialActionWeight").toFloat()
    private val baseDamage: IntRange = onj.get<OnjArray>("baseDamage").toIntRange()
    private val baseShield: IntRange = onj.get<OnjArray>("baseShield").toIntRange()
    private val scaleIncreasePerTurn: Float = onj.get<Double>("scaleIncreasePerTurn").toFloat()

    private val actions: MutableList<EnemyActionConfig> = onj
        .get<OnjArray>("actions")
        .value
        .map {
            it as OnjObject
            EnemyActionConfig(
                it.get<Long>("weight").toInt(),
                it.get<Double>("showProbability").toFloat(),
                EnemyActionPrototype.fromOnj(it.get<OnjNamedObject>("action"), enemy),
                it.getOr("maxExecutions", 0L).toInt()
            )
        }
        .toMutableList()

    private var currentScale: Float = 1.0f
    private var nextAction: EnemyAction? = null

    open fun prioritizeAction(controller: GameController, scale: Double): Pair<EnemyAction, Boolean>? = null
    open fun onActionResolution(action: EnemyAction?) {}

    override fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction? {
        val action = nextAction
        nextAction = null
        currentScale += scaleIncreasePerTurn
        onActionResolution(action)
        return action
    }

    override fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<NextEnemyAction>
    ): NextEnemyAction {
        prioritizeAction(controller, difficulty * currentScale)?.let { (action, show) ->
            nextAction = action
            return if (show) NextEnemyAction.ShownEnemyAction(action) else NextEnemyAction.HiddenEnemyAction
        }
        val doNormalAction = Utils.coinFlip(normalSpecialActionWeight)
        if (doNormalAction) {
            val actionProto = if (Utils.coinFlip(damageShieldWeight)) {
                damagePlayer(baseDamage, enemy)
            } else {
                takeCover(baseShield, enemy)
            }
            val action = actionProto.create(controller, difficulty * currentScale)
            nextAction = action
            return NextEnemyAction.ShownEnemyAction(action)
        }
        val actionConfig = actions
            .zipToFirst { it.weight }
            .weightedRandom()
        val (_, showProb, actionProto) = actionConfig
        val action = actionProto.create(controller, difficulty * currentScale)
        nextAction = action
        actionConfig.executionCount++
        if (actionConfig.maxExecutions > 0 && actionConfig.executionCount >= actionConfig.maxExecutions) {
            actions.remove(actionConfig)
        }
        return if (Utils.coinFlip(showProb)) {
            NextEnemyAction.ShownEnemyAction(action)
        } else {
            NextEnemyAction.HiddenEnemyAction
        }
    }

    private data class EnemyActionConfig(
        val weight: Int,
        val showProbability: Float,
        val prototype: EnemyActionPrototype,
        val maxExecutions: Int,
        var executionCount: Int = 0
    )

}

class SniperEnemyBrain(
    config: OnjObject,
    enemy: Enemy
) : NewEnemyBrain(config, enemy) {

    private val goodbyesAction = EnemyActionPrototype.fromOnj(config.get<OnjNamedObject>("goodbyesAction"), enemy)

    private var justExecutedHeadsUp: Boolean = false

    override fun prioritizeAction(controller: GameController, scale: Double): Pair<EnemyAction, Boolean>? {
        if (!justExecutedHeadsUp) return null
        justExecutedHeadsUp = false
        val action = goodbyesAction.create(controller, scale)
        return action to true
    }

    override fun onActionResolution(action: EnemyAction?) {
        action ?: return
        justExecutedHeadsUp = action.prototype is EnemyActionPrototype.MarkCards
    }
}

class ScriptedEnemyBrain(actions: OnjArray, private val enemy: Enemy) : EnemyBrain() {

    private val actions: List<Triple<Int, EnemyActionPrototype, Boolean>> = actions
        .value
        .map { it as OnjObject }
        .map {
            Triple(
                it.get<Long>("turn").toInt() - 1, // controller counts from 0
                EnemyActionPrototype.fromOnj(it.get<OnjNamedObject>("action"), enemy),
                it.get<Boolean>("show")
            )
        }

    private var createdAction: EnemyAction? = null

    override fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction? {
        createdAction?.let {
            createdAction = null
            return it
        }
        val (_, actionProto, _) = actions
            .find { (turn, _, _) -> turn == controller.turnCounter }
            ?: return null
        return actionProto.create(controller, difficulty)
    }

    override fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<NextEnemyAction>
    ): NextEnemyAction {
        val (_, actionProto, show) = actions
            .find { (turn, _, _) -> turn == controller.turnCounter }
            ?: return NextEnemyAction.None
        return if (show) {
            val action = actionProto.create(controller, difficulty)
            createdAction = action
            NextEnemyAction.ShownEnemyAction(action)
        } else {
            createdAction = null
            NextEnemyAction.HiddenEnemyAction
        }
    }
}

object NoOpEnemyBrain : EnemyBrain() {

    override fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction? = null

    override fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<NextEnemyAction>
    ): NextEnemyAction = NextEnemyAction.None
}

