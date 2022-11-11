package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.fourinachamber.fourtyfive.screen.InitialiseableActor
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.screen.ZIndexActor
import ktx.actors.contains

class EnemyArea : Widget(), ZIndexActor, InitialiseableActor {

    private lateinit var screenDataProvider: ScreenDataProvider
    override var fixedZIndex: Int = 0
    var enemies: MutableList<Enemy> = mutableListOf()
        private set
    private var isInitialised: Boolean = false

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
    }

    fun addEnemy(enemy: Enemy) {
        enemies.add(enemy)
        if (enemy.actor !in screenDataProvider.stage.root) screenDataProvider.addActorToRoot(enemy.actor)
        updateEnemyPositions()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (!isInitialised) {
            updateEnemyPositions()
            isInitialised = true
        }
        super.draw(batch, parentAlpha)
    }

    private fun updateEnemyPositions() {
        for (enemy in enemies) enemy.actor.setPosition(x + enemy.offsetX, y + enemy.offsetY)
    }

}
