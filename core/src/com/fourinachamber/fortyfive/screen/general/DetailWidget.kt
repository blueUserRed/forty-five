package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fortyfive.utils.*

sealed class DetailWidget(protected val screen: OnjScreen) {

    var detailActor: Actor? = null

    val isShown: Boolean = detailActor != null
    abstract fun generateDetailActor(): Actor
    open fun drawDetailActor(batch: Batch) {
        detailActor?.draw(batch, 1f)
    }

    open fun updateBounds(original: Actor) {
        val detailActor = detailActor
        if (detailActor !is Layout) return
        val width = if (detailActor.prefWidth == 0f) detailActor.width else detailActor.prefWidth
        val height = if (detailActor.prefHeight == 0f) detailActor.height else detailActor.prefHeight

        val (x, y) = original.localToStageCoordinates(Vector2(0, 0))
        val yCoordinate =
            if (y + original.height + height > original.stage.viewport.worldHeight) {
                y - height //if it would be too high up, it will be lower
            } else {
                y + original.height
            }
        val xCoordinate = (x + original.width / 2 - width / 2).between(0F, original.stage.viewport.worldWidth - width)
        detailActor.setBounds(
            xCoordinate,
            yCoordinate,
            width,
            height
        )
    }

    class SimpleDetailActor(
        screen: OnjScreen,
        effects: List<AdvancedTextParser.AdvancedTextEffect> = listOf(),
        useDefaultEffects: Boolean = true,
        private val text: () -> String
    ) : AdvancedTextDetailWidget(screen,effects,useDefaultEffects) {


        override fun generateDetailActor(): Actor {
            val actor = AdvancedTextWidget(
                Triple("red_wing", Color.FortyWhite, 1f),
                screen, true
            )
            actor.backgroundHandle = defBackground
            actor.width = 300F
            actor.height = 100F
            actor.setRawText(text.invoke(), effects)
            actor.setPadding(20F)
            actor.validate() //this is needed, or it flashed on the first frame
            return actor
        }
    }


    abstract class AdvancedTextDetailWidget(
        screen: OnjScreen,
        effects: List<AdvancedTextParser.AdvancedTextEffect> = listOf(),
        useDefaultEffects: Boolean = true
    ) : DetailWidget(screen) {
        protected val effects: List<AdvancedTextParser.AdvancedTextEffect>

        init {
            if (useDefaultEffects) this.effects = effects + defaultEffects
            else this.effects = effects
        }

        companion object{
           private val defaultEffects: List<AdvancedTextParser.AdvancedTextEffect> = mutableListOf(
                AdvancedTextParser.AdvancedTextEffect.AdvancedColorTextEffect("\$fwhite\$", Color.FortyWhite),
                AdvancedTextParser.AdvancedTextEffect.AdvancedColorTextEffect("\$red\$", Color.Red),
                AdvancedTextParser.AdvancedTextEffect.AdvancedColorTextEffect("\$green\$", Color.Green),
                AdvancedTextParser.AdvancedTextEffect.AdvancedColorTextEffect("\$blue\$", Color.Blue),
                AdvancedTextParser.AdvancedTextEffect.AdvancedColorTextEffect("\$brown\$", Color.DarkBrown),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontScaleTextEffect("\$minimal\$", 0.8f),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontScaleTextEffect("\$small\$", 0.65f),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontScaleTextEffect("\$tiny\$", 0.5f),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontScaleTextEffect("\$big\$", 1.2f),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontScaleTextEffect("\$giant\$", 1.35f),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontScaleTextEffect("\$enormous\$", 1.5f),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontTextEffect("\$red_wing\$", "red_wing"),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontTextEffect("\$roadgeek\$", "roadgeek"),
            )
        }
    }
    companion object {
        const val logTag: String = "DetailWidget"

        const val defBackground: String = "nav_bar_background" //TODO replace this with the correct one
    }
}