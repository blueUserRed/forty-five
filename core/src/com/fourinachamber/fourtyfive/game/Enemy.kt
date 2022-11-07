package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fourtyfive.screen.*
import onj.OnjArray
import onj.OnjObject

class Enemy(
    val name: String,
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

    // it's a bit hacky but actor needs to be initialised after currentLives, so the text of actor is correct
    @Suppress("JoinDeclarationAndAssignment")
    val actor: EnemyActor

    var currentLives: Int = lives
        private set

    init {
        actor = EnemyActor(this)
        actor.setScale(scaleX, scaleY)
    }

    companion object {

        fun getFrom(
            enemiesOnj: OnjArray,
            regions: Map<String, TextureRegion>,
            fonts: Map<String, BitmapFont>
        ): List<Enemy> = enemiesOnj
            .value
            .map {
                it as OnjObject
                val texture = regions[it.get<String>("texture")] ?:
                    throw RuntimeException("unknown texture ${it.get<String>("texture")}")
                val detailFont = fonts[it.get<String>("detailFont")] ?:
                    throw RuntimeException("unknown font ${it.get<String>("detailFont")}")
                Enemy(
                    it.get<String>("name"),
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

class EnemyActor(val enemy: Enemy) : CustomVerticalGroup(), ZIndexActor {

    override var fixedZIndex: Int = 0
    private var image: CustomImageActor = CustomImageActor(enemy.texture)
    private var detail: CustomLabel = CustomLabel(
        "",
        Label.LabelStyle(enemy.detailFont, enemy.detailFontColor)
    )

    init {
        detail.setFontScale(enemy.detailFontScale)
        addActor(image)
        addActor(detail)
        updateText()
    }

    fun updateText() {
        detail.setText("${enemy.currentLives}/${enemy.lives}")
    }

    override fun scaleChanged() {
//        println("Hi")
//        image.setScale(scaleX, scaleY)
//        setScale(1f)
        super.scaleChanged()
    }
}
