package com.microwavestudios.fortyfive.game.enemy

import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.onjNamespaces.OnjEnemyPredicate
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjObject

class EnemyBrain(
    private val enemyActions: List<EnemyActionOption>
) {

    fun selectAction(enemy: Enemy, controller: GameController, difficulty: Double): NextEnemyAction {
        if (enemyActions.isEmpty()) return NextEnemyAction.None
        val winner = enemyActions
            .filter { it.maxExecutions == -1 || it.executions < it.maxExecutions }
            .filter { option -> option.selectionConditions.all { it(enemy, controller) } }
            .zipToFirst { it.weight(enemy, controller) }
            .weightedRandomOrNull(controller.random)
            ?: return NextEnemyAction.None
        if (winner.action == null) return NextEnemyAction.None
        winner.executions++
        val variant = winner
            .variants
            .zipToFirst { it.weight }
            .weightedRandom(controller.random)
        val data = EnemyAction.EnemyActionData(
            variant.hidden,
            difficulty,
            enemy,
            controller,
            winner.overrideTitle,
            winner.overrideDescription,
            winner.overrideIcon,
            winner.overrideSecondaryIcon
        )
        val action = winner.action.copy(data)
        return if (variant.hidden) {
            NextEnemyAction.HiddenEnemyAction(action)
        } else {
            NextEnemyAction.ShownEnemyAction(action)
        }
    }

    data class EnemyActionOption(
        val action: EnemyAction?,
        val weight: EnemyActionValue,
        val maxExecutions: Int,
        val selectionConditions: List<EnemyPredicate>,
        val variants: List<EnemyActionVariant>,
        var executions: Int,
        val overrideTitle: String?,
        val overrideDescription: String?,
        val overrideIcon: ResourceHandle?,
        val overrideSecondaryIcon: ResourceHandle?
    )

    data class EnemyActionVariant(
        val weight: Int,
        val hidden: Boolean
    )

    companion object {

        fun fromOnj(onj: OnjObject): EnemyBrain = onj
            .get<OnjArray>("actions")
            .value
            .map { obj ->
                obj as OnjObject
                val weight = obj.get<EnemyActionValue>("weight")
                val action = obj.get<EnemyAction?>("action")
                val maxExecutions = obj.getOr("maxExecutions", -1L).toInt()

                val selectionConditions = obj.getOr<OnjArray?>("selectionConditions", null)
                    ?.value
                    ?.map { (it as OnjEnemyPredicate).value }
                    ?: listOf()

                val variants = obj.getOr<OnjArray?>("variants", null)?.value?.map {
                    it as OnjObject
                    EnemyActionVariant(it.get<Long>("weight").toInt(), it.get<Boolean>("hidden"))
                } ?: listOf(EnemyActionVariant(1, false))

                EnemyActionOption(
                    action,
                    weight,
                    maxExecutions,
                    selectionConditions,
                    variants,
                    0,
                    obj.getOr<String?>("overrideTitle", null),
                    obj.getOr<String?>("overrideDescription", null),
                    obj.getOr<String?>("overrideIcon", null),
                    obj.getOr<String?>("overrideSecondaryIcon", null),
                )
            }.let { EnemyBrain(it) }

    }
}

//class ScriptedEnemyBrain(actions: OnjArray, private val enemy: Enemy) : EnemyBrain() {
//
//    private val actions: List<Triple<Int, EnemyActionPrototype, Boolean>> = actions
//        .value
//        .map { it as OnjObject }
//        .map {
//            Triple(
//                it.get<Long>("turn").toInt() - 1, // controller counts from 0
//                EnemyActionPrototype.fromOnj(it.get<OnjNamedObject>("action"), enemy),
//                it.get<Boolean>("show")
//            )
//        }
//
//    override fun onNewTurn(
//        controller: GameController,
//        enemy: Enemy
//    ) {
//    }
//
//    override fun chooseNewAction(
//        controller: GameController,
//        enemy: Enemy,
//        difficulty: Double,
//        otherChosenActions: List<Pair<EnemyActionPrototype, Boolean>>
//    ): Pair<EnemyActionPrototype, Boolean>? {
//        val (_, actionProto, show) = actions
//            .find { (turn, _, _) -> turn == controller.turnCounter }
//            ?: return null
//        return actionProto to show
//    }
//}
//
//object NoOpEnemyBrain : EnemyBrain() {
//
//    override fun onNewTurn(
//        controller: GameController,
//        enemy: Enemy
//    ) {}
//
//    override fun chooseNewAction(
//        controller: GameController,
//        enemy: Enemy,
//        difficulty: Double,
//        otherChosenActions: List<Pair<EnemyActionPrototype, Boolean>>
//    ): Pair<EnemyActionPrototype, Boolean>? = null
//}

