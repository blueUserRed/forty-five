package com.fourinachamber.fourtyfive.game.enemy

import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class EnemyBrain(
    private val actionCreators: List<Pair<Int, () -> EnemyAction>>
) {

    private val totalWeight: Int

    init {
        var weightSum = 0
        for ((weight, _) in actionCreators) weightSum += weight
        totalWeight = weightSum
    }

    fun chooseAction(): EnemyAction {
        if (actionCreators.isEmpty()) {
            throw RuntimeException("cannot create a behaviour because no behaviour creators exist")
        }
        val rand = (0 until totalWeight).random()
        var curWeight = 0
        for ((weight, creator) in actionCreators) {
            curWeight += weight
            if (rand < curWeight) return creator()
        }
        return actionCreators.last().second()
    }

    companion object {

        fun fromOnj(onj: OnjObject, onjScreen: OnjScreen, enemy: Enemy): EnemyBrain = EnemyBrain(
            onj.get<OnjArray>("actions").value.map { actionFromOnj(it as OnjNamedObject, onjScreen, enemy) }
        )

        private fun actionFromOnj(
            onj: OnjNamedObject,
            onjScreen: OnjScreen,
            enemy: Enemy
        ): Pair<Int, () -> EnemyAction> = when (onj.name) {

            "DamagePlayerEnemyAction" -> ({
                val min = onj.get<Long>("min").toInt()
                val max = onj.get<Long>("max").toInt()

                EnemyAction.DamagePlayer(
                    enemy,
                    onj,
                    onjScreen,
                    onj.get<Double>("indicatorTextureScale").toFloat(),
                    (min..max).random(),
                )
            })

            "AddCoverEnemyAction" -> ({
                val min = onj.get<Long>("min").toInt()
                val max = onj.get<Long>("max").toInt()

                EnemyAction.AddCover(
                    enemy,
                    onj,
                    onjScreen,
                    onj.get<Double>("indicatorTextureScale").toFloat(),
                    (min..max).random(),
                )
            })

            "DoNothingEnemyAction" -> ({
                EnemyAction.DoNothing(
                    onj.get<OnjArray>("insults").value.map { it.value as String }.random(),
                    enemy,
                    onj,
                    onjScreen,
                    onj.get<Double>("indicatorTextureScale").toFloat(),
                )
            })


            else -> throw RuntimeException("unknown enemy action ${onj.name}")

        }.let {
            onj.get<Long>("weight").toInt() to it
        }

    }

}
