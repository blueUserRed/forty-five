package com.fourinachamber.fourtyfive.game.enemy

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.fourinachamber.fourtyfive.game.GameScreenController

abstract class EnemyAction {

    abstract val indicatorTexture: TextureRegion

    abstract val descriptionText: String

    abstract fun execute(gameScreenController: GameScreenController)

}

class DamagePlayerEnemyAction(
    val damage: Int,
    override val indicatorTexture: TextureRegion
) : EnemyAction() {

    override val descriptionText: String = damage.toString()

    override fun execute(gameScreenController: GameScreenController) {
        gameScreenController.damagePlayer(damage)
    }

}
