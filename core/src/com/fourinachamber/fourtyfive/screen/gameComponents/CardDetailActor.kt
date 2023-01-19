package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
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
        }

    init {
        arrayOf(flavourTextActor, descriptionActor, statsTextActor, statsChangedTextActor).forEach {
            add(it)
                .setWidthPercent(100f)
                .setMaxWidthPercent(100f)
            it.setFontScale(fontScale)
            it.wrap = true
        }
        root.flexDirection = YogaFlexDirection.COLUMN
        root.setPadding(YogaEdge.ALL, 1f)
        background = initialBackground

        touchable = Touchable.enabled
        onEnter { isHoveredOver = true }
        onExit { isHoveredOver = false }
        layout() // Without this the height is not set correctly on the first frame, don't ask me why
    }


    override fun getPrefWidth(): Float {
        return if (isVisible) forcedWidth else 100f
    }

}
