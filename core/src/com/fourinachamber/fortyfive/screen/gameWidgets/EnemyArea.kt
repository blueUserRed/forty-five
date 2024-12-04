package com.fourinachamber.fortyfive.screen.gameWidgets

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexGroup
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.utils.Promise

/**
 * actor representing the area in which enemies can appear on the screen
 */
class EnemyArea(
    private val enemySelectionDrawableHandle: ResourceHandle,
    private val screen: OnjScreen
) : WidgetGroup(), ZIndexActor, ZIndexGroup, StyledActor, ResourceBorrower {

    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean=false

    override var fixedZIndex: Int = 0

    private var _enemies: MutableList<Enemy> = mutableListOf()

    private var selectedEnemy: Enemy? = null

    private val enemySelectionDrawable: Promise<Drawable> =
        ResourceManager.request(this, screen, enemySelectionDrawableHandle)

    /**
     * all enemies in this area
     */
    val enemies: List<Enemy>
        get() = _enemies

    private val canSelectEnemy: Boolean
        get() = _enemies.filter { !it.isDefeated }.size >= 2

    init {
        bindHoverStateListeners(this)
    }

    /**
     * adds a new enemy to this area
     */
    fun addEnemy(enemy: Enemy) {
        _enemies.add(enemy)
//        addActor(enemy.actor)
        if (canSelectEnemy) selectEnemy(_enemies.first { !it.isDefeated })
        invalidate()
    }

    fun selectEnemy(enemy: Enemy) {
        if (!canSelectEnemy || enemy.isDefeated) return
        selectedEnemy = enemy
    }

    fun getTargetedEnemy(): Enemy =
        selectedEnemy ?:
        _enemies.firstOrNull { !it.isDefeated } ?:
        _enemies.firstOrNull() ?:
        throw RuntimeException("No enemies in enemy area")


    fun onEnemyDefeated() {
        selectedEnemy = if (canSelectEnemy) {
            _enemies.first { !it.isDefeated }
        } else {
            null
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        _enemies.forEach(Enemy::update)
        val enemy = selectedEnemy ?: return
        val selectionWidth = 30f
        val enemySelectionDrawable = this.enemySelectionDrawable.getOrNull() ?: return
//        enemySelectionDrawable.draw(
//            batch,
//            x + enemy.actor.x + enemy.actor.width / 2 - selectionWidth / 2 + enemy.headOffset,
//            y + enemy.actor.y + enemy.actor.height + 20f,
//            selectionWidth,
//            selectionWidth * (enemySelectionDrawable.minHeight / enemySelectionDrawable.minWidth)
//        )
    }

    override fun layout() {
        super.layout()
//        val neededWidth = enemies.sumOf { it.actor.width.toDouble() * 1.3 }.toFloat()
//        var curX = width / 2 - neededWidth / 2
//        enemies
//            .map { it.actor }
//            .forEach { enemy ->
//                enemy.setBounds(curX, height / 2, enemy.prefWidth, enemy.prefHeight)
//                curX += enemy.width * 1.3f
//            }
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
