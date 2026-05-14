package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.microwavestudios.fortyfive.screen.BakedDropShadow
import com.microwavestudios.fortyfive.screen.actors.CustomImageActor
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.PropertyAction
import com.microwavestudios.fortyfive.utils.*

sealed class DetailWidget(protected val screen: RenderableScreen) {

    var detailActor: Actor? = null

    val isShown: Boolean = detailActor != null

    var shownAlpha = 1F

    abstract fun generateDetailActor(addFadeInAction: Boolean): Actor?

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
        detailActor.validate()
        val width = detailActor.width
        val height = detailActor.prefHeight

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
        screen: RenderableScreen,
        effects: List<AdvancedTextParser.AdvancedTextEffect> = listOf(),
        useDefaultEffects: Boolean = true,
        private val text: () -> String
    ) : AdvancedTextDetailWidget(screen, effects, useDefaultEffects) {


        override fun generateDetailActor(addFadeInAction: Boolean): Actor {
            val actor = AdvancedTextWidget(
                Triple("red wing", Color.FortyWhite, 19),
                screen
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

    class SimpleSmallDetailActor(
        screen: RenderableScreen,
        effects: List<AdvancedTextParser.AdvancedTextEffect> = listOf(),
        useDefaultEffects: Boolean = true,
        private val text: () -> String
    ) : AdvancedTextDetailWidget(screen, effects, useDefaultEffects) {


        override fun generateDetailActor(addFadeInAction: Boolean): Actor {
            val actor = AdvancedTextWidget(
                Triple("red wing", Color.FortyWhite, 15),
                screen
            )
            val group = CustomGroup(screen)
            group.backgroundHandle = defBackgroundSmall
            group.dropShadow = BakedDropShadow(
                defBackgroundSmall,
                screen,
                0f, 0f,
                1.33f, 1.33f
            )
            group.width = 200F
            group.height = 150F
            actor.width = group.width
            actor.setRawText(text.invoke(), effects)
            actor.setPadding(15F)
            group.addActor(actor)
            actor.onLayout {
                actor.height = actor.prefHeight
                actor.x = group.width / 2f - actor.width / 2f
                actor.y = group.height / 2f - actor.height / 2f
            }
            if (addFadeInAction) addFadeInAction(actor)
            return group
        }
    }

    class ComplexBigDetailActor(
        screen: RenderableScreen,
        effects: List<AdvancedTextParser.AdvancedTextEffect> = listOf(),
        useDefaultEffects: Boolean = true,
        private val text: () -> List<String>,
        private val topText: () -> String = { "" },
        private val subtexts: () -> List<String> = { listOf() },
    ) : AdvancedTextDetailWidget(screen, effects, useDefaultEffects) {

        private var subtextParent: CustomBox? = null
        private var columns: Int = 0

        override fun generateDetailActor(addFadeInAction: Boolean): Actor? {
            val texts = text().filter { it.isNotBlank() }
            val topText = topText()
            val subtexts = subtexts()

            val subtextSplit = if (subtexts.size > 3) subtexts.size / 2 else 0
            val firstSubtexts = subtexts.subList(0, subtextSplit)
            val secondSubtexts = subtexts.subList(subtextSplit, subtexts.size)

            val width = 300F

            if (subtexts.isEmpty() && texts.isEmpty() && topText.isBlank()) return null

            var actualWidth = 0f

            val parent = CustomBox(screen)
            parent.flexDirection = FlexDirection.ROW
            parent.horizontalAlign = CustomAlign.CENTER
            parent.verticalAlign = CustomAlign.START
            parent.fitContentInFlexDirection = true

            val smallFontSize = 16
            val bigFontSize = 18

            if (firstSubtexts.isNotEmpty()) with(CustomBox(screen)) {
                flexDirection = FlexDirection.COLUMN
                horizontalAlign = CustomAlign.CENTER
                verticalAlign = CustomAlign.END
                this.width = width
                onLayout { height = prefHeight }

                firstSubtexts.forEach {
                    val actor = AdvancedTextWidget(
                        Triple("roadgeek", Color.FortyWhite, smallFontSize), screen
                    )
                    actor.marginBottom = 20f
                    actor.backgroundHandle = defBackgroundSmall
                    actor.dropShadow = BakedDropShadow(
                        defBackgroundSmall,
                        screen,
                        0f, 0f,
                        1.33f, 1.33f
                    )
                    actor.width = width
                    actor.setRawText(it, effects)
                    actor.fitContentHeight = true
                    actor.setPadding(13F)
                    addActor(actor)
                }
                actualWidth += width
                parent.addActor(this)
            }

            val middleParent = CustomBox(screen)
            middleParent.width = width
            middleParent.flexDirection = FlexDirection.COLUMN
            middleParent.fitContentInFlexDirection = true

            if (topText.isNotBlank()) with(CustomBox(screen)) {
                setPadding(15F)
                this.width = width
                fitContentInFlexDirection = true
                backgroundHandle = defBackground
                dropShadow = BakedDropShadow(
                    defBackground,
                    screen,
                    0f, 0f,
                    1.33f, 1.33f
                )
                val actor = AdvancedTextWidget(
                    Triple("roadgeek", Color.FortyWhite, bigFontSize),
                    screen
                )
                actor.width = 260f
                actor.setRawText(topText, effects)
                actor.fitContentHeight = true
                addActor(actor)
                middleParent.addActor(this)
            }

            val mainText = CustomBox(screen)
            mainText.verticalAlign = CustomAlign.SPACE_AROUND
            mainText.setPadding(15F)
            mainText.width = width
            mainText.onLayout { mainText.height = mainText.prefHeight.coerceAtLeast(200f) }
            mainText.minVerticalDistBetweenElements = 5f
            mainText.backgroundHandle = defBackground
            mainText.dropShadow = BakedDropShadow(
                defBackground,
                screen,
                0f, 0f,
                1.33f, 1.33f
            )
            val innerWidth = mainText.width - mainText.paddingLeft - mainText.paddingRight
            texts.forEachIndexed { i, it ->
                if (i != 0) {
                    val imgActor = CustomImageActor("forty_white_rounded", screen)
                    imgActor.width = innerWidth
                    imgActor.height = 2f
                    mainText.addActor(imgActor)
                }
                val actor = AdvancedTextWidget(
                    Triple("roadgeek", Color.FortyWhite, bigFontSize),
                    screen
                )
                actor.width = innerWidth
                actor.setRawText(it, effects)
                actor.fitContentHeight = true
                mainText.addActor(actor)
            }
            middleParent.addActor(mainText)

            parent.addActor(middleParent)
            actualWidth += width

            if (secondSubtexts.isNotEmpty()) with(CustomBox(screen)) {
                flexDirection = FlexDirection.COLUMN
                horizontalAlign = CustomAlign.CENTER
                verticalAlign = CustomAlign.END
                this.width = width
                onLayout { height = prefHeight }

                secondSubtexts.forEach {
                    val actor = AdvancedTextWidget(
                        Triple("roadgeek", Color.FortyWhite, smallFontSize), screen
                    )
                    actor.marginBottom = 20f
                    actor.backgroundHandle = defBackgroundSmall
                    actor.dropShadow = BakedDropShadow(
                        defBackgroundSmall,
                        screen,
                        0f, 0f,
                        1.33f, 1.33f
                    )
                    actor.width = width
                    actor.setRawText(it, effects)
                    actor.fitContentHeight = true
                    actor.setPadding(13F)
                    addActor(actor)
                }
                actualWidth += width
                parent.addActor(this)
            }

            parent.width = actualWidth
            columns = (actualWidth / width).toInt()
            if (addFadeInAction) addFadeInAction(parent)
            return parent
        }


        override fun drawDetailActor(batch: Batch) {
            val shownAlpha1 = shownAlpha
            detailActor?.draw(batch, shownAlpha1)
        }

        override fun updateBounds(original: Actor) {
            super.updateBounds(original)
            val detailActor = detailActor
            if (detailActor !is Layout) return
            detailActor.validate()
            val width = detailActor.width
            val height = detailActor.prefHeight

            val (x, y) = original.localToStageCoordinates(Vector2(0, 0))
            val yCoordinate =
                if (y + original.height + height > screen.stage.viewport.worldHeight) {
                    y - height
                } else {
                    y + original.height
                }
            var xCoordinate = (x + original.width / 2 - width / 2)
            if (columns == 2) xCoordinate += 150
            xCoordinate = xCoordinate.between(0F, screen.stage.viewport.worldWidth - width)
            detailActor.setBounds(
                xCoordinate,
                yCoordinate,
                width,
                height
            )
        }

        override fun hide() {
            super.hide()
            subtextParent = null
        }
    }

    abstract class AdvancedTextDetailWidget(
        screen: RenderableScreen,
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
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontSizeTextEffect("\$minimal\$", 25),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontSizeTextEffect("\$small\$", 21),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontSizeTextEffect("\$tiny\$", 16),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontSizeTextEffect("\$big\$", 38),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontSizeTextEffect("\$giant\$", 43),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontSizeTextEffect("\$enormous\$", 48),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontTextEffect("\$red_wing\$", "red wing"),
                AdvancedTextParser.AdvancedTextEffect.AdvancedFontTextEffect("\$roadgeek\$", "roadgeek"),
            )
        }
    }

    companion object {
        const val LOG_TAG: String = "DetailWidget"

        const val defBackground: String = "detail_widget_background_big"
        const val defBackgroundSmall: String = "detail_widget_background_small"
    }
}