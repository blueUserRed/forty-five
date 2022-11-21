package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fourtyfive.screen.*
import onj.OnjArray
import onj.OnjObject

/**
 * represents an enemy
 * @param name the name of the enemy
 * @param texture the texture of this enemy
 * @param offsetX x-offset from the origin of [EnemyArea] to the point where this enemy is located
 * @param offsetY y-offset from the origin of [EnemyArea] to the point where this enemy is located
 * @param scaleX scales [actor]
 * @param scaleY scales [actor]
 * @param lives the initial (and maximum) lives of this enemy
 * @param detailFont the font used for the description of the enemy
 * @param detailFontScale scales [detailFont]
 * @param detailFontColor the color of [detailFont]
 */
class Enemy(
    val name: String,
    val brain: EnemyBrain,
    val texture: TextureRegion,
    val lives: Int,
    val offsetX: Float,
    val offsetY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val detailFont: BitmapFont,
    val detailFontScale: Float,
    val detailFontColor: Color
) {

    /**
     * the actor that represents this enemy on the screen
     */
    // it's a bit hacky but actor needs to be initialised after currentLives, so the text of actor is correct
    @Suppress("JoinDeclarationAndAssignment")
    val actor: EnemyActor

    /**
     * the current lives of this enemy
     */
    var currentLives: Int = lives
        private set

    init {
        actor = EnemyActor(this)
        actor.setScale(scaleX, scaleY)
    }

    /**
     * reduces the enemies lives by [damage]
     */
    fun damage(damage: Int) {
        currentLives -= damage
        actor.updateText()
    }

    companion object {

        /**
         * reads an array of Enemies from on an OnjArray
         */
        fun getFrom(
            enemiesOnj: OnjArray,
            screenDataProvider: ScreenDataProvider
        ): List<Enemy> = enemiesOnj
            .value
            .map {
                it as OnjObject
                val texture = screenDataProvider.textures[it.get<String>("texture")] ?:
                    throw RuntimeException("unknown texture ${it.get<String>("texture")}")
                val detailFont = screenDataProvider.fonts[it.get<String>("detailFont")] ?:
                    throw RuntimeException("unknown font ${it.get<String>("detailFont")}")
                Enemy(
                    it.get<String>("name"),
                    EnemyBrain.fromOnj(it.get<OnjObject>("brain"), screenDataProvider),
                    texture,
                    it.get<Long>("lives").toInt(),
                    it.get<Double>("offsetX").toFloat(),
                    it.get<Double>("offsetY").toFloat(),
                    it.get<Double>("scaleX").toFloat(),
                    it.get<Double>("scaleY").toFloat(),
                    detailFont,
                    it.get<Double>("detailFontScale").toFloat(),
                    Color.valueOf(it.get<String>("detailFontColor"))
                )
            }

    }

}

/**
 * used for representing an enemy on the screen
 */
class EnemyActor(val enemy: Enemy) : CustomVerticalGroup(), ZIndexActor {

    override var fixedZIndex: Int = 0
    private var image: CustomImageActor = CustomImageActor(enemy.texture)
    private val actionIndicator: CustomHorizontalGroup = CustomHorizontalGroup()

    private val actionIndicatorText: CustomLabel = CustomLabel(
        "",
        Label.LabelStyle(enemy.detailFont, enemy.detailFontColor)
    )

    private var detail: CustomLabel = CustomLabel(
        "",
        Label.LabelStyle(enemy.detailFont, enemy.detailFontColor)
    )

    init {
        detail.setFontScale(enemy.detailFontScale)
        actionIndicator.addActor(actionIndicatorText)
        addActor(actionIndicator)
        addActor(image)
        addActor(detail)
        updateText()
    }

    /**
     * updates the desctiption text of the actor
     */
    fun updateText() {
        detail.setText("${enemy.currentLives}/${enemy.lives}")
    }

}
