package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.g2d.TextureRegion

abstract class EnemyAction {

    abstract val indicatorTexture: TextureRegion

    abstract fun execute(gameScreenController: GameScreenController)

}

class DamagePlayerEnemyAction(
    val damage: Int,
    override val indicatorTexture: TextureRegion
) : EnemyAction() {

    override fun execute(gameScreenController: GameScreenController) {
        gameScreenController.damagePlayer(damage)
    }

}
