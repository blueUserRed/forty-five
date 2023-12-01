package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.Utils
import com.fourinachamber.fortyfive.utils.toIntRange
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

            "WitchBrain" -> WitchBrain(
                onj.get<OnjArray>("firstEffectPossibleTurns").toIntRange(),
                onj.get<OnjArray>("secondEffectOffset").toIntRange(),
                onj.get<OnjArray>("bewitchedTurns").toIntRange(),
                onj.get<OnjArray>("bewitchedRotations").toIntRange(),
                onj.get<OnjArray>("wrathOfTheWitchDamageRange").toIntRange(),
                onj.get<OnjArray>("wardOfTheWitchCoverRange").toIntRange(),
                onj.get<Long>("bewitchedBufferTurns").toInt(),
                onj.get<Double>("bewitchedProbability").toFloat(),
                onj.get<Double>("bewitchedShowProbability").toFloat(),
                onj.get<Double>("rotateRevolverProbability").toFloat(),
                onj.get<Double>("rotateRevolverShowProbability").toFloat(),
                onj.get<Double>("damageProbability").toFloat(),
                onj.get<OnjArray>("damage").toIntRange(),
                onj.get<Double>("shieldProbability").toFloat(),
                onj.get<OnjArray>("shield").toIntRange(),
                onj.get<String>("revolverRotationIconHandle"),
                onj.get<String>("damageIconHandle"),
                onj.get<String>("shieldIconHandle"),
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

class WitchBrain(
    firstEffectPossibleTurns: IntRange,
    secondEffectOffset: IntRange,
    private val bewitchedTurns: IntRange,
    private val bewitchedRotations: IntRange,
    wrathOfTheWitchDamageRange: IntRange,
    wardOfTheWitchCoverRange: IntRange,
    private val bewitchedBufferTurns: Int,
    private val bewitchedProbability: Float,
    private val bewitchedShowProbability: Float,
    private val rotateRevolverProbability: Float,
    private val rotateRevolverShowProbability: Float,
    private val damageProbability: Float,
    private val damage: IntRange,
    private val shieldProbability: Float,
    private val shield: IntRange,
    private val revolverRotationIconHandle: ResourceHandle,
    private val damageIconHandle: ResourceHandle,
    private val shieldIconHandle: ResourceHandle,
) : EnemyBrain() {

    private val firstEffectTurn = firstEffectPossibleTurns.random()
    private val secondEffectTurn = firstEffectTurn + secondEffectOffset.random()
    private val firstEffectIsWrathOfTheWitch: Boolean = Utils.coinFlip(0.5f)

    private val wrathOfTheWitchDamage = wrathOfTheWitchDamageRange.random()
    private val wardOfTheWitchCover = wardOfTheWitchCoverRange.random()

    private val bewitchedActiveUntilTurn: Int = 0

    private var nextAction: EnemyAction? = null
    private var doBewitched: Boolean = false

    private fun createBewitchedActionPrototype(enemy: Enemy): EnemyActionPrototype = EnemyActionPrototype.GivePlayerStatusEffect(
        { Bewitched(bewitchedTurns.random(), bewitchedRotations.random()) },
        1f,
        enemy,
        false,
        true
    ).also {
        setParameters(
            it,
            // im not passing all these values through the constructor, it is already long enough
            // TODO: come up with a better solution
            "enemy_action_bewitched",
            "The player gets the bewitched status effect!",
            "Bewitched",
            "enemy_witch_action_comic_panel_bewitched"
        )
    }

    private fun createWrathOfTheWitchActionPrototype(enemy: Enemy): EnemyActionPrototype = EnemyActionPrototype.GivePlayerStatusEffect(
        { WrathOfTheWitch(wrathOfTheWitchDamage) },
        1f,
        enemy,
        false,
        true
    ).also {
        setParameters(
            it,
            "enemy_action_bewitched",
            "The player gets the 'Wrath of the Witch' status effect!",
            "Wrath of the Witch",
            "enemy_witch_action_comic_panel_wrath_of_the_witch"
        )
    }

    private fun createWardOfTheWitchActionPrototype(enemy: Enemy): EnemyActionPrototype = EnemyActionPrototype.GiveSelfStatusEffect(
        { WardOfTheWitch(wardOfTheWitchCover) },
        1f,
        enemy,
        false,
        true
    ).also {
        setParameters(
            it,
            "enemy_action_bewitched",
            "The player gets the 'Ward of the Witch' status effect!",
            "Ward of the Witch",
            "enemy_witch_action_comic_panel_ward_of_the_witch"
        )
    }

    private fun createLeftRotationActionPrototype(enemy: Enemy): EnemyActionPrototype = EnemyActionPrototype.RotateRevolver(
        1,
        GameController.RevolverRotation.Left(1),
        1f,
        enemy,
        false,
        true
    ).also {
        setParameters(
            it,
            "enemy_action_left_turn",
            "The revolver rotates {amount} to the left",
            "Left Rotation",
            "enemy_witch_action_comic_panel_left_turn"
        )
    }

    private fun setParameters(
        of: EnemyActionPrototype,
        icon: String,
        descriptionTemplate: String,
        title: String,
        specialPanel: String? = null
    ) {
        of.commonPanel1 = "enemy_witch_action_comic_common_panel_1"
        of.commonPanel2 = "enemy_witch_action_comic_common_panel_2"
        of.commonPanel3 = "enemy_witch_action_comic_common_panel_3"
        specialPanel?.let { of.specialPanel = it }
        of.iconHandle = icon
        of.descriptionTemplate = descriptionTemplate
        of.title = title
    }

    override fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction? {
        val turn = controller.turnCounter
        if (turn == firstEffectTurn || turn == secondEffectTurn) {
            return if (turn == firstEffectTurn && firstEffectIsWrathOfTheWitch) {
                createWrathOfTheWitchActionPrototype(enemy).create(controller, difficulty)
            } else {
                createWardOfTheWitchActionPrototype(enemy).create(controller, difficulty)
            }
        }
        nextAction?.let {
            nextAction = null
            return it
        }
        if (doBewitched) {
            return createBewitchedActionPrototype(enemy).create(controller, difficulty)
        }
        return null
    }

    override fun chooseNewAction(controller: GameController, enemy: Enemy, difficulty: Double): NextEnemyAction = run {
        val turn = controller.turnCounter
        doBewitched = false
        if (turn == firstEffectTurn || turn == secondEffectTurn) return@run NextEnemyAction.HiddenEnemyAction
        if (bewitchedActiveUntilTurn + bewitchedBufferTurns < turn && Utils.coinFlip(bewitchedProbability)) {
            doBewitched = true
            val action = createBewitchedActionPrototype(enemy).create(controller, difficulty)
            nextAction = action
            return@run if (Utils.coinFlip(bewitchedShowProbability)) {
                NextEnemyAction.HiddenEnemyAction
            } else {
                NextEnemyAction.ShownEnemyAction(action)
            }
        }

        if (Utils.coinFlip(rotateRevolverProbability)) {
            val action = createLeftRotationActionPrototype(enemy).create(controller, difficulty)
            nextAction = action
            return@run if (Utils.coinFlip(rotateRevolverShowProbability)) {
                NextEnemyAction.ShownEnemyAction(action)
            } else {
                NextEnemyAction.HiddenEnemyAction
            }
        }
        if (Utils.coinFlip(damageProbability)) {
            val action = EnemyActionPrototype.DamagePlayer(
                damage,
                1f,
                enemy,
                false,
                false
            ).also {
                setParameters(
                    it,
                    "enemy_action_damage",
                    "This attack does {damage} damage",
                    "Damage"
                )
            }.create(controller, difficulty)
            nextAction = action
            return NextEnemyAction.ShownEnemyAction(action)
        }
        if (Utils.coinFlip(shieldProbability)) {
            val action = EnemyActionPrototype.TakeCover(
                shield,
                1f,
                enemy,
                false,
                false
            ).also {
                setParameters(
                    it,
                    "enemy_action_cover",
                    "The enemy adds {cover} shield",
                    "Cover"
                )
            }.create(controller, difficulty)
            nextAction = action
            return NextEnemyAction.ShownEnemyAction(action)
        }

        return@run NextEnemyAction.None
    }

}

object NoOpEnemyBrain : EnemyBrain() {

    override fun resolveEnemyAction(controller: GameController, enemy: Enemy, difficulty: Double): EnemyAction? = null

    override fun chooseNewAction(controller: GameController, enemy: Enemy, difficulty: Double): NextEnemyAction = NextEnemyAction.None
}
