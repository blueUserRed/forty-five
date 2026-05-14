package com.microwavestudios.fortyfive.game.enemy

import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

abstract class EnemyBrain {

    abstract fun onNewTurn(controller: GameController, enemy: Enemy)

    abstract fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<Pair<EnemyActionPrototype, Boolean>>
    ): Pair<EnemyActionPrototype, Boolean>?

    companion object {

        fun fromOnj(onj: OnjNamedObject, enemy: Enemy): EnemyBrain = when (onj.name) {

            "NewEnemyBrain" -> NewEnemyBrain(onj, enemy)

            "ScriptedEnemyBrain" -> ScriptedEnemyBrain(
                onj.get<OnjArray>("actions"),
                enemy
            )

//            "SniperEnemyBrain" -> SniperEnemyBrain(
//                onj,
//                enemy
//            )

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

    private val aggressionHealthPercent: Float = onj.access<Double>(".aggressionBoostConfig.healthPercent").toFloat()
    private val aggressionNormalSpecialWeightChange: Float = onj.access<Double>(".aggressionBoostConfig.normalSpecialWeightChange").toFloat()
    private val aggressionDamageShieldWeightChange: Float = onj.access<Double>(".aggressionBoostConfig.damageShieldWeightChange").toFloat()
    private val aggressionDamageIncrease: Int = onj.access<Long>(".aggressionBoostConfig.damageIncrease").toInt()

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

    open fun prioritizeAction(controller: GameController, scale: Double): Pair<EnemyActionPrototype, Boolean>? = null

    override fun onNewTurn(controller: GameController, enemy: Enemy) {
        currentScale += scaleIncreasePerTurn
    }

    override fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<Pair<EnemyActionPrototype, Boolean>>
    ): Pair<EnemyActionPrototype, Boolean> {
        prioritizeAction(controller, difficulty * currentScale)?.let {
            return it
        }
        val aggressionHealth = enemy.health * aggressionHealthPercent
        val aggressive = aggressionHealth >= enemy.currentHealth

        val normalSpecialActionWeight = if (aggressive) {
            (normalSpecialActionWeight + aggressionNormalSpecialWeightChange).between(0f, 1f)
        } else {
            normalSpecialActionWeight
        }
        val damageShieldWeight = if (aggressive) {
            (damageShieldWeight + aggressionDamageShieldWeightChange).between(0f, 1f)
        } else {
            damageShieldWeight
        }
        val doNormalAction = Utils.coinFlip(normalSpecialActionWeight, controller.random)
        if (doNormalAction) {
            val actionProto = if (Utils.coinFlip(damageShieldWeight, controller.random)) {
                val damage = if (aggressive) {
                    baseDamage shift aggressionDamageIncrease
                } else {
                    baseDamage
                }
                damagePlayer(damage, enemy)
            } else {
                takeCover(baseShield, enemy)
            }
            return actionProto to true
        }
        val actionConfig = actions
            .zipToFirst { it.weight }
            .weightedRandom(controller.random)
        val (_, showProb, actionProto) = actionConfig
        actionConfig.executionCount++
        if (actionConfig.maxExecutions > 0 && actionConfig.executionCount >= actionConfig.maxExecutions) {
            actions.remove(actionConfig)
        }
        return actionProto to Utils.coinFlip(showProb, controller.random)
    }

    private data class EnemyActionConfig(
        val weight: Int,
        val showProbability: Float,
        val prototype: EnemyActionPrototype,
        val maxExecutions: Int,
        var executionCount: Int = 0
    )

}

//class SniperEnemyBrain(
//    config: OnjObject,
//    enemy: Enemy
//) : NewEnemyBrain(config, enemy) {
//
//    private val goodbyesAction = EnemyActionPrototype.fromOnj(config.get<OnjNamedObject>("goodbyesAction"), enemy)
//
//    private var justExecutedHeadsUp: Boolean = false
//
//    override fun prioritizeAction(controller: GameController, scale: Double): Pair<EnemyAction, Boolean>? {
//        if (!justExecutedHeadsUp) return null
//        justExecutedHeadsUp = false
//        val action = goodbyesAction.create(controller, scale)
//        return action to true
//    }
//
//    override fun onActionResolution(action: EnemyAction?) {
//        action ?: return
//        justExecutedHeadsUp = action.prototype is EnemyActionPrototype.MarkCards
//    }
//}

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

    override fun onNewTurn(
        controller: GameController,
        enemy: Enemy
    ) {
    }

    override fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<Pair<EnemyActionPrototype, Boolean>>
    ): Pair<EnemyActionPrototype, Boolean>? {
        val (_, actionProto, show) = actions
            .find { (turn, _, _) -> turn == controller.turnCounter }
            ?: return null
        return actionProto to show
    }
}

object NoOpEnemyBrain : EnemyBrain() {

    override fun onNewTurn(
        controller: GameController,
        enemy: Enemy
    ) {}

    override fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<Pair<EnemyActionPrototype, Boolean>>
    ): Pair<EnemyActionPrototype, Boolean>? = null
}

