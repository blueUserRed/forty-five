package com.fourinachamber.fourtyfive.game

import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import onj.OnjArray
import onj.OnjNamedObject
import onj.OnjObject

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

        fun fromOnj(onj: OnjObject, screenDataProvider: ScreenDataProvider): EnemyBrain = EnemyBrain(
            onj.get<OnjArray>("actions").value.map { actionFromOnj(it as OnjNamedObject, screenDataProvider) }
        )

        private fun actionFromOnj(
            onj: OnjNamedObject,
            screenDataProvider: ScreenDataProvider
        ): Pair<Int, () -> EnemyAction> = when (onj.name) {

            "DamagePlayerEnemyAction" -> ({
                DamagePlayerEnemyAction(
                    (onj.get<Long>("min").toInt()..onj.get<Long>("max").toInt()).random(),
                    screenDataProvider.textures[onj.get<String>("indicatorTexture")] ?: throw
                        RuntimeException("unknown texture: ${onj.get<String>("indicatorTexture")}")
                )
            })

            else -> throw RuntimeException("unknown enemy action ${onj.name}")

        }.let {
            onj.get<Long>("weight").toInt() to it
        }

    }

}
