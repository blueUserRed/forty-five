package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.ZIndexGroup
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles

/**
 * actor representing the area in which enemies can appear on the screen
 */
class EnemyArea(
    private val enemySelectionDrawableHandle: ResourceHandle,
    private val screen: OnjScreen
) : WidgetGroup(), ZIndexActor, ZIndexGroup, StyledActor {

    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false

    override var fixedZIndex: Int = 0

    private var _enemies: MutableList<Enemy> = mutableListOf()

    var selectedEnemy: Enemy? = null
        set(value) {
            // refuse to set value if there is only one enemy
            if (_enemies.size != 1) field = value
        }

    private val enemySelectionDrawable: Drawable by lazy {
        ResourceManager.get(screen, enemySelectionDrawableHandle)
    }

    /**
     * all enemies in this area
     */
    val enemies: List<Enemy>
        get() = _enemies

    init {
        bindHoverStateListeners(this)
    }

    /**
     * adds a new enemy to this area
     */
    fun addEnemy(enemy: Enemy) {
        _enemies.add(enemy)
        addActor(enemy.actor)
        invalidate()
    }

    fun getTargetedEnemy(): Enemy {
        return selectedEnemy ?: enemies.firstOrNull() ?: throw RuntimeException("No enemies in enemy area")
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        val enemy = selectedEnemy ?: return
        enemySelectionDrawable.draw(
            batch,
            x + enemy.actor.x, y + enemy.actor.y,
            enemy.actor.prefWidth, enemy.actor.prefHeight
        )
    }

    override fun layout() {
        for (enemy in _enemies) {
            enemy.actor.setPosition(width / 2f, height / 2f)
        }
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

}
