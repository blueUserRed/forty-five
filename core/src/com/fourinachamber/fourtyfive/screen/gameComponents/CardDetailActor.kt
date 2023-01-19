package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.screen.general.CustomFlexBox
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import io.github.orioncraftmc.meditate.enums.YogaEdge
import io.github.orioncraftmc.meditate.enums.YogaFlexDirection
import ktx.actors.onEnter
import ktx.actors.onExit

class CardDetailActor(
    initialFlavourText: String,
    initialDescription: String,
    initialStatsText: String,
    initialStatsChangedText: String?,
    private val font: BitmapFont,
    private val fontColor: Color,
    private val fontScale: Float,
    initialForcedWidth: Float,
    private val initialBackground: Drawable? = null
) : CustomFlexBox() {

    /**
     * true when the actor is hovered over
     */
    var isHoveredOver: Boolean = false
        private set

    private val flavourTextActor = CustomLabel(initialFlavourText, Label.LabelStyle(font, fontColor))
    private val descriptionActor = CustomLabel(initialDescription, Label.LabelStyle(font, fontColor))
    private val statsTextActor = CustomLabel(initialStatsText, Label.LabelStyle(font, fontColor))
    private val statsChangedTextActor =
        CustomLabel(initialStatsChangedText ?: "", Label.LabelStyle(font, fontColor))

    var flavourText: String = initialFlavourText
        set(value) {
            flavourTextActor.setText(value)
            field = value
        }

    var description: String? = initialDescription
        set(value) {
            descriptionActor.setText(value ?: "")
            val shouldBeVisible = value != null
            if (descriptionActor.isVisible != shouldBeVisible) {
                descriptionActor.isVisible = shouldBeVisible
                // When this actor is hidden the dimensions are always 0, so you don't need to recalculate everything
                if (this.isVisible) invalidateHierarchy()
            }
            field = value
        }

    var statsText: String = initialStatsText
        set(value) {
            statsTextActor.setText(value)
            field = value
        }

    var statsChangedText: String? = initialStatsChangedText
        set(value) {
            statsChangedTextActor.setText(value ?: "")
            val shouldBeVisible = value != null
            if (statsChangedTextActor.isVisible != shouldBeVisible) {
                statsChangedTextActor.isVisible = shouldBeVisible
                // When this actor is hidden the dimensions are always 0, so you don't need to recalculate everything
                if (this.isVisible) invalidateHierarchy()
            }
            field = value
        }

    var forcedWidth = initialForcedWidth
        set(value) {
            field = value
            root.setWidth(value)
            invalidateHierarchy()
        }

    init {
        arrayOf(flavourTextActor, descriptionActor, statsTextActor, statsChangedTextActor).forEach {
            add(it)
                .setWidthPercent(100f)
                .setMaxWidthPercent(100f)
            it.wrap = true
        }

        root.flexDirection = YogaFlexDirection.COLUMN
        root.setPadding(YogaEdge.ALL, 1f)
        background = initialBackground

        flavourTextActor.setFontScale(fontScale)
        descriptionActor.setFontScale(fontScale)
        statsTextActor.setFontScale(fontScale)
        statsChangedTextActor.setFontScale(fontScale)

        touchable = Touchable.enabled
        onEnter { isHoveredOver = true }
        onExit { isHoveredOver = false }
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        invalidateHierarchy()
    }

    override fun getPrefWidth(): Float {
        return if (isVisible) forcedWidth else 0f
    }

    override fun getPrefHeight(): Float {
        return if (isVisible) super.getPrefHeight() else 0f
    }

    override fun getMinWidth(): Float {
        return if (isVisible) forcedWidth else 0f
    }

    override fun getMinHeight(): Float {
        return if (isVisible) super.getMinHeight() else 0f
    }

    override fun getMaxWidth(): Float {
        return if (isVisible) forcedWidth else 0f
    }

    override fun getMaxHeight(): Float {
        return if (isVisible) super.getMaxHeight() else 0f
    }
}
