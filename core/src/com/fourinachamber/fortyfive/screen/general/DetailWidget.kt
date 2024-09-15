package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.PropertyAction
import com.fourinachamber.fortyfive.utils.*

sealed class DetailWidget(protected val screen: OnjScreen) {

    var detailActor: Actor? = null

    val isShown: Boolean = detailActor != null

    var shownAlpha = 1F
        set(value) {
            field = value
        }

    abstract fun generateDetailActor(addFadeInAction: Boolean): Actor

    open fun drawDetailActor(batch: Batch) {
        detailActor?.draw(batch, shownAlpha)
    }

    open fun addFadeInAction(singleTextParent: Actor) {
        shownAlpha = 0f
        val propertyAction = PropertyAction(this, this::shownAlpha, 1f)
        propertyAction.duration = 0.2F
        propertyAction.interpolation = Interpolation.linear
        singleTextParent.addAction(propertyAction)
    }

    open fun updateBounds(original: Actor) {
        //TODO the limits (at the border) of this method need to be tested once backpack and fight are working
        val detailActor = detailActor
        if (detailActor !is Layout) return
        val width = if (detailActor.prefWidth == 0f) detailActor.width else detailActor.prefWidth
        val height = if (detailActor.prefHeight == 0f) detailActor.height else detailActor.prefHeight

        val (x, y) = original.localToStageCoordinates(Vector2(0, 0))
        val yCoordinate =
            if (y + original.height + height > screen.stage.viewport.worldHeight) {
                y - height //if it would be too high up, it will be lower
            } else {
                y + original.height
            }
        val xCoordinate = (x + original.width / 2 - width / 2).between(0F, screen.stage.viewport.worldWidth - width)
        detailActor.setBounds(
            xCoordinate,
            yCoordinate,
            width,
            height
        )
    }

    open fun hide() {
        detailActor = null
    }

    class SimpleBigDetailActor(
        screen: OnjScreen,
        effects: List<AdvancedTextParser.AdvancedTextEffect> = listOf(),
        useDefaultEffects: Boolean = true,
        private val text: () -> String
    ) : AdvancedTextDetailWidget(screen, effects, useDefaultEffects) {


        override fun generateDetailActor(addFadeInAction: Boolean): Actor {
            val actor = AdvancedTextWidget(
                Triple("red_wing", Color.FortyWhite, 0.6f),
                screen, true
            )
            actor.backgroundHandle = defBackground
            actor.width = 300F
            actor.height = 100F
            actor.setRawText(text.invoke(), effects)
            actor.setPadding(15F)
            if (addFadeInAction) addFadeInAction(actor)
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

        private var subtextParent: CustomBox? = null
        private val distanceBetweenMainAndSub: Float = 10F
        override fun generateDetailActor(addFadeInAction: Boolean): Actor {
            val texts = text.invoke().filter { it.isNotEmpty() }
            val width = 300F
            generateSubtexts(width * 3 / 5)

            if (texts.size <= 1) {
                val singleTextParent = getSingleTextParent(texts, width)
                if (addFadeInAction) addFadeInAction(singleTextParent)
                return singleTextParent
            }

            val parent = CustomBox(screen)
            parent.verticalAlign = CustomAlign.SPACE_AROUND
            parent.setPadding(15F)
            parent.width = width
            parent.fitContentInFlexDirection = true
            parent.minVerticalDistBetweenElements = 5f
            parent.backgroundHandle = defBackground


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
            if (addFadeInAction) addFadeInAction(parent)
            return parent
        }

        @Suppress("SameParameterValue")
        private fun generateSubtexts(width: Float) {
            val texts = subtexts.invoke().filter { it.isNotBlank() }
            if (texts.isEmpty()) {
                subtextParent = null
                return
            }
            val parent = CustomBox(screen)
            parent.width = width
            parent.minVerticalDistBetweenElements = 10F

            texts.forEach {
                val actor = AdvancedTextWidget(
                    Triple("roadgeek_bmp", Color.FortyWhite, 0.6f), screen, true
                )
                actor.backgroundHandle = defBackgroundSmall
                actor.width = width
                actor.setRawText(it, effects)
                actor.setPadding(13F)
                parent.addActor(actor)
                actor.fitContentHeight = true
            }
            parent.fitContentInFlexDirection = true
            subtextParent = parent
        }

        @Suppress("SameParameterValue")
        private fun getSingleTextParent(text: List<String>, width: Float): Actor {
            val actor = AdvancedTextWidget(
                Triple("roadgeek_bmp", Color.FortyWhite, 0.6f), screen, true
            )
            actor.backgroundHandle = defBackground
            actor.width = width
            actor.height = 100F
            actor.setRawText(if (text.isEmpty()) " ".repeat(20) else text[0], effects) //this if needs to be tested
            actor.setPadding(15F)
            return actor
        }

        override fun drawDetailActor(batch: Batch) {
            val shownAlpha1 = shownAlpha
            detailActor?.draw(batch, shownAlpha1)
            subtextParent?.draw(batch, shownAlpha1)
        }

        override fun updateBounds(original: Actor) {
            super.updateBounds(original)
            val sub = subtextParent ?: return
            val main = detailActor ?: return
            val worldWidth = screen.stage.viewport.worldWidth
            val x = if (main.x + main.width + sub.width + distanceBetweenMainAndSub >= worldWidth) {
                main.x - sub.width - distanceBetweenMainAndSub
            } else {
                main.x + main.width + distanceBetweenMainAndSub
            }
            val y = (main.y + main.height - sub.prefHeight).between(0F, screen.stage.viewport.worldHeight)
            sub.setBounds(
                x,
                y,
                sub.width,
                sub.prefHeight
            )
        }

        override fun hide() {
            super.hide()
            subtextParent = null
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