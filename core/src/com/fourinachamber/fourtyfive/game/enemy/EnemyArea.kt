package com.fourinachamber.fourtyfive.game.enemy

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.fourinachamber.fourtyfive.screen.InitialiseableActor
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.screen.ZIndexActor
import ktx.actors.contains

/**
 * actor representing the area in which enemies can appear on the screen
 */
class EnemyArea : Widget(), ZIndexActor, InitialiseableActor {

    private lateinit var screenDataProvider: ScreenDataProvider

    override var fixedZIndex: Int = 0

    private var _enemies: MutableList<Enemy> = mutableListOf()

    /**
     * all enemies in this area
     */
    val enemies: List<Enemy>
        get() = _enemies

    private var isInitialised: Boolean = false

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
    }

    /**
     * adds a new enemy to this area
     */
    fun addEnemy(enemy: Enemy) {
        _enemies.add(enemy)
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
        for (enemy in _enemies) enemy.actor.setPosition(x + enemy.offsetX, y + enemy.offsetY)
    }

}
