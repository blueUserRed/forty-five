package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
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

    class SimpleBigDetailActor(
        screen: OnjScreen,
        effects: List<AdvancedTextParser.AdvancedTextEffect> = listOf(),
        useDefaultEffects: Boolean = true,
        private val text: () -> String
    ) : AdvancedTextDetailWidget(screen, effects, useDefaultEffects) {


        override fun generateDetailActor(): Actor {
            val actor = AdvancedTextWidget(
                Triple("red_wing", Color.FortyWhite, 0.7f),
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

    class KomplexBigDetailActor(
        screen: OnjScreen,
        effects: List<AdvancedTextParser.AdvancedTextEffect> = listOf(),
        useDefaultEffects: Boolean = true,
        private val text: () -> List<String>,
        private val subtexts: () -> List<String> = { listOf() },
    ) : AdvancedTextDetailWidget(screen, effects, useDefaultEffects) {

        private val subtextActors: MutableList<Actor> = mutableListOf()
        override fun generateDetailActor(): Actor {
            val texts = text.invoke().filter { it.isNotEmpty() }
            //TODO subtexts here
            if (texts.size <= 1) return getSingleTextParent(texts)

            val parent = CustomBox(screen)
            parent.verticalAlign = CustomAlign.SPACE_AROUND
            parent.setPadding(13F)
            parent.width = 300F
            parent.height = 200f
            parent.onLayout {
                parent.height = parent.prefHeight
            }
            parent.minVerticalDistBetweenElements = 5f
            parent.backgroundHandle = defBackground
            parent.debug = true


            val innerWidth = parent.width - parent.paddingLeft - parent.paddingRight
            texts.forEachIndexed { i, it ->
                if (i != 0) {
                    val imgActor = CustomImageActor("forty_white_rounded", screen)
                    imgActor.width = innerWidth
                    imgActor.height = 2f
                    parent.addActor(imgActor)
                }
                val actor = AdvancedTextWidget(
                    Triple("roadgeek_bmp", Color.FortyWhite, if (i == 0) 0.6f else 0.5f),
                    screen, true
                )
                actor.width = innerWidth
                actor.setRawText(it, effects)
                actor.fitContentHeight = true
                parent.addActor(actor)
            }

//            parent.validate() //this is needed, or it flashed on the first frame
            return parent
        }

        private fun getSingleTextParent(text: List<String>): Actor {
            val actor = AdvancedTextWidget(
                Triple("roadgeek_bmp", Color.FortyWhite, 0.6f), screen, true
            )
            actor.backgroundHandle = defBackground
            actor.width = 300F
            actor.height = 100F
            actor.setRawText(if (text.isEmpty()) " ".repeat(20) else text[0], effects) //this if needs to be tested
            actor.setPadding(13F)
            return actor
        }

        override fun drawDetailActor(batch: Batch) {
            super.drawDetailActor(batch)
            subtextActors.forEach { it.draw(batch, 1f) }
        }

//        fun getDetailExtra(text:String) : Actor{
//
//        }
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

        companion object {
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
        const val LOG_TAG: String = "DetailWidget"

        const val defBackground: String = "detail_widget_background_big" //TODO replace assets once markus exported them
        const val defBackgroundSmall: String = "detail_widget_background_small"
    }
}