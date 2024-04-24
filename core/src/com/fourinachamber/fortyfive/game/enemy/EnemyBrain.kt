package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.*
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

            "OutlawBrain" -> OutlawEnemyBrain(onj)
            "PyroBrain" -> PyroEnemyBrain(onj)
            "WitchBrain" -> WitchEnemyBrain(onj)

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

class NewEnemyBrain(onj: OnjObject, private val enemy: Enemy) : EnemyBrain() {

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

    override fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction? {
        val action = nextAction
        nextAction = null
        currentScale += scaleIncreasePerTurn
        return action
    }

    override fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<NextEnemyAction>
    ): NextEnemyAction {
        val doNormalAction = Utils.coinFlip(normalSpecialActionWeight)
        if (doNormalAction) {
            val actionProto = if (Utils.coinFlip(damageShieldWeight)) {
                val damage = baseDamage.scale(currentScale.toDouble())
                damagePlayer(damage, enemy)
            } else {
                takeCover(baseShield.scale(currentScale.toDouble()), enemy)
            }
            val action = actionProto.create(controller, difficulty)
            nextAction = action
            return NextEnemyAction.ShownEnemyAction(action)
        }
        val actionConfig = actions
            .zipToFirst { it.weight }
            .weightedRandom()
        val (_, showProb, actionProto) = actionConfig
        val action = actionProto.create(controller, difficulty)
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

class OutlawEnemyBrain(onj: OnjObject) : EnemyBrain() {

    private val phase1End = onj.get<Long>("phase1End").toInt()
    private val phase2End = onj.get<Long>("phase2End").toInt()

    private val damageValues: Array<IntRange> = arrayOf(
        onj.get<OnjArray>("damagePhase1").toIntRange(),
        onj.get<OnjArray>("damagePhase2").toIntRange(),
        onj.get<OnjArray>("damagePhase3").toIntRange(),
    )

    private val coverValues: Array<IntRange> = arrayOf(
        onj.get<OnjArray>("coverPhase1").toIntRange(),
        onj.get<OnjArray>("coverPhase2").toIntRange(),
        onj.get<OnjArray>("coverPhase3").toIntRange(),
    )

    private val actionProb = onj.get<Double>("actionProbability").toFloat()
    private val attackProb = onj.get<Double>("attackProbability").toFloat()

    private var chosenAction: EnemyAction? = null
    private var didSomethingLastTurn: Boolean = false

    override fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction? = chosenAction

    override fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<NextEnemyAction>
    ): NextEnemyAction {
        if (didSomethingLastTurn || !Utils.coinFlip(actionProb)) {
            didSomethingLastTurn = false
            chosenAction = null
            return NextEnemyAction.None
        }
        val phase = when {
            controller.turnCounter + 1 > phase2End -> 2
            controller.turnCounter + 1 > phase1End -> 1
            else -> 0
        }
        val action = if (Utils.coinFlip(attackProb)) {
            var damage = damageValues[phase]
            if (otherChosenActions.containsAttack()) {
                damage = (damage.first / 2.0 + 0.5).toInt()..(damage.last / 2.0 + 0.5).toInt()
            }
            damagePlayer(damage, enemy)
        } else {
            takeCover(coverValues[phase], enemy)
        }.create(controller, difficulty)
        chosenAction = action
        didSomethingLastTurn = true
        return NextEnemyAction.ShownEnemyAction(action)
    }
}

class PyroEnemyBrain(onj: OnjObject) : EnemyBrain() {

    private var chosenAction: EnemyAction? = null

    private val burningTurns: IntRange = onj.get<OnjArray>("possibleBurningTurns").toIntRange()
    private val burningTurn: Int = burningTurns.random()
    private val burningRotations: IntRange = onj.get<OnjArray>("burningRotations").toIntRange()
    private val hideBurningProb: Float = onj.get<Double>("hideBurningProbability").toFloat()

    private val hotPotatoProb: Float = onj.get<Double>("hotPotatoProbability").toFloat()
    private val hideHotPotatoProb: Float = onj.get<Double>("hideHotPotatoProbability").toFloat()

    private val infernoHealthPercentage: Float = onj.get<Double>("infernoHealthPercentage").toFloat()

    private val damage: IntRange = onj.get<OnjArray>("damage").toIntRange()
    private val cover: IntRange = onj.get<OnjArray>("cover").toIntRange()

    private val actionProb = onj.get<Double>("actionProbability").toFloat()
    private val attackProb = onj.get<Double>("attackProbability").toFloat()

    override fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction? = chosenAction

    override fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<NextEnemyAction>
    ): NextEnemyAction {
        if (controller.turnCounter + 1 == burningTurn) {
            val action = burning(burningRotations.random(), enemy).create(controller, difficulty)
            chosenAction = action
            return if (Utils.coinFlip(hideBurningProb)) {
                NextEnemyAction.HiddenEnemyAction
            } else {
                NextEnemyAction.ShownEnemyAction(action)
            }
        }
        if (controller.turnCounter + 1 > burningTurns.last && Utils.coinFlip(hotPotatoProb)) {
            val action = hotPotato(enemy).create(controller, difficulty)
            chosenAction = action
            return if (Utils.coinFlip(hideHotPotatoProb)) {
                NextEnemyAction.HiddenEnemyAction
            } else {
                NextEnemyAction.ShownEnemyAction(action)
            }
        }
        if ((enemy.currentHealth.toFloat() / enemy.health.toFloat()) < infernoHealthPercentage) {
            val action = inferno(enemy).create(controller, difficulty)
            chosenAction = action
            return NextEnemyAction.ShownEnemyAction(action)
        }
        if (!Utils.coinFlip(actionProb)) {
            chosenAction = null
            return NextEnemyAction.None
        }
        val action = if (Utils.coinFlip(attackProb)) {
            var damage = damage
            if (otherChosenActions.containsAttack()) {
                damage = (damage.first / 2.0 + 0.5).toInt()..(damage.last / 2.0 + 0.5).toInt()
            }
            damagePlayer(damage, enemy)
        } else {
            takeCover(cover, enemy)
        }.create(controller, difficulty)
        chosenAction = action
        return NextEnemyAction.ShownEnemyAction(action)
    }

    private fun hotPotato(enemy: Enemy) = EnemyActionPrototype.GivePlayerCard(
        "scorchingBullet",
        enemy,
        true
    ).apply {
        iconHandle = "enemy_action_hot_potato"
        title = "Hot Potato"
        descriptionTemplate = "A [Scorching Bullet] is being put in your hand!"
        commonPanel1 = "enemy_pyro_action_comic_common_panel_1"
        commonPanel2 = "enemy_pyro_action_comic_common_panel_2"
        commonPanel3 = "enemy_pyro_action_comic_common_panel_3"
        specialPanel = "enemy_pyro_action_comic_panel_hot_potato"
    }

    private fun burning(rotations: Int, enemy: Enemy) = EnemyActionPrototype.GivePlayerStatusEffect(
        { _, _, skipFirstRotation -> BurningPlayer(rotations, 0.5f, false, skipFirstRotation) },
        enemy,
        true
    ).apply {
        iconHandle = "enemy_action_burning"
        title = "Burning"
        descriptionTemplate = "You get Burning!"
        commonPanel1 = "enemy_pyro_action_comic_common_panel_1"
        commonPanel2 = "enemy_pyro_action_comic_common_panel_2"
        commonPanel3 = "enemy_pyro_action_comic_common_panel_3"
        specialPanel = "enemy_pyro_action_comic_panel_burning"
    }

    private fun inferno(enemy: Enemy) = EnemyActionPrototype.GivePlayerStatusEffect(
        { _, _, skipFirstRotation -> BurningPlayer(0, 0.5f, true, skipFirstRotation) },
        enemy,
        true
    ).apply {
        iconHandle = "enemy_action_inferno"
        title = "Inferno"
        descriptionTemplate = "You get Burning for the rest of the fight!"
        commonPanel1 = "enemy_pyro_action_comic_common_panel_1"
        commonPanel2 = "enemy_pyro_action_comic_common_panel_2"
        commonPanel3 = "enemy_pyro_action_comic_common_panel_3"
        specialPanel = "enemy_pyro_action_comic_panel_inferno"
    }
}

class WitchEnemyBrain(onj: OnjObject) : EnemyBrain() {

    private var doLeftTurn: Boolean = false
    private var chosenAction: EnemyAction? = null

    private val bewitchedProb = onj.get<Double>("bewitchedProbability").toFloat()
    private val bewitchedTurns: IntRange = onj.get<OnjArray>("bewitchedTurns").toIntRange()
    private val bewitchedRotations: IntRange = onj.get<OnjArray>("bewitchedRotations").toIntRange()
    private val hideBewitchedProb = onj.get<Double>("hideBewitchedProbability").toFloat()

    private val leftTurnProb = onj.get<Double>("leftTurnProbability").toFloat()

    private val damage: IntRange = onj.get<OnjArray>("damage").toIntRange()
    private val cover: IntRange = onj.get<OnjArray>("cover").toIntRange()

    private val actionProb = onj.get<Double>("actionProbability").toFloat()
    private val attackProb = onj.get<Double>("attackProbability").toFloat()

    override fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction? {
        if (doLeftTurn) {
            doLeftTurn = false
            return if (controller.revolver.slots.any { it.card != null }) {
                leftTurn(enemy).create(controller, difficulty)
            } else {
                bewitched(bewitchedTurns.random(), bewitchedRotations.random(), enemy)
                    .create(controller, difficulty)
            }
        }
        return chosenAction
    }

    override fun chooseNewAction(
        controller: GameController,
        enemy: Enemy,
        difficulty: Double,
        otherChosenActions: List<NextEnemyAction>
    ): NextEnemyAction {
        if (Utils.coinFlip(leftTurnProb)) {
            doLeftTurn = true
            chosenAction = null
            return NextEnemyAction.HiddenEnemyAction
        }
        if (Utils.coinFlip(bewitchedProb)) {
            val action = bewitched(bewitchedTurns.random(), bewitchedRotations.random(), enemy)
                .create(controller, difficulty)
            chosenAction = action
            return if (Utils.coinFlip(hideBewitchedProb)) {
                NextEnemyAction.HiddenEnemyAction
            } else {
                NextEnemyAction.ShownEnemyAction(action)
            }
        }
        if (!Utils.coinFlip(actionProb)) {
            chosenAction = null
            return NextEnemyAction.None
        }
        val action = if (Utils.coinFlip(attackProb)) {
            var damage = damage
            if (otherChosenActions.containsAttack()) {
                damage = (damage.first / 2.0 + 0.5).toInt()..(damage.last / 2.0 + 0.5).toInt()
            }
            damagePlayer(damage, enemy)
        } else {
            takeCover(cover, enemy)
        }.create(controller, difficulty)
        chosenAction = action
        return NextEnemyAction.ShownEnemyAction(action)
    }

    private fun bewitched(turns: Int, rotations: Int, enemy: Enemy): EnemyActionPrototype = EnemyActionPrototype.GivePlayerStatusEffect(
        { _, _, skipFirstRotation -> Bewitched(turns, rotations, skipFirstRotation) },
        enemy,
        true
    ).apply {
        iconHandle = "enemy_action_bewitched"
        title = "Bewitched"
        descriptionTemplate = "The player gets the Bewitched status effect!"
        commonPanel1 = "enemy_witch_action_comic_common_panel_1"
        commonPanel2 = "enemy_witch_action_comic_common_panel_2"
        commonPanel3 = "enemy_witch_action_comic_common_panel_3"
        specialPanel = "enemy_witch_action_comic_panel_bewitched"
    }

    private fun leftTurn(enemy: Enemy): EnemyActionPrototype = EnemyActionPrototype.RotateRevolver(
        1,
        GameController.RevolverRotation.Left(1),
        enemy,
        true
    ).apply {
        iconHandle = "enemy_action_left_turn"
        title = "Bewitched"
        descriptionTemplate = "The revolver rotates to the left!"
        commonPanel1 = "enemy_witch_action_comic_common_panel_1"
        commonPanel2 = "enemy_witch_action_comic_common_panel_2"
        commonPanel3 = "enemy_witch_action_comic_common_panel_3"
        specialPanel = "enemy_witch_action_comic_panel_left_turn"
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

