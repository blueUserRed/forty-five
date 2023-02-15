package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.game.GraphicsConfig
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ZIndexActor
import ktx.actors.onEnter
import ktx.actors.onExit

class CardDetailActor(
    val card: Card,
    initialFlavourText: String,
    initialDescription: String,
    initialStatsText: String,
    initialStatsChangedText: String,
    font: BitmapFont,
    fontColor: Color,
    private val fontScale: Float,
    private val spacing: Float,
    initialForcedWidth: Float,
    private val screen: OnjScreen,
    var background: Drawable? = null
) : WidgetGroup(), ZIndexActor {

    /**
     * true when the actor is hovered over
     */
    var isHoveredOver: Boolean = false
        private set

    override var fixedZIndex: Int = 0

    private val lineHeight = spacing * 0.2f
    private val lineWidthMultiplier = 0.8f

    private val flavourTextActor = CustomLabel(initialFlavourText, Label.LabelStyle(font, fontColor))
    private val descriptionActor = CustomLabel(initialDescription, Label.LabelStyle(font, fontColor))
    private val statsTextActor = CustomLabel(initialStatsText, Label.LabelStyle(font, fontColor))
    private val statsChangedTextActor =
        CustomLabel(initialStatsChangedText, Label.LabelStyle(font, fontColor))

//    private var requiresRebuild: Boolean = true

    var flavourText: String = initialFlavourText
        set(value) {
            invalidateHierarchy()
            flavourTextActor.setText(value)
            flavourTextActor.isVisible = value.isNotBlank()
            field = value
        }

    var description: String = initialDescription
        set(value) {
            invalidateHierarchy()
            descriptionActor.setText(value)
            descriptionActor.isVisible = value.isNotBlank()
            field = value
        }

    var statsText: String = initialStatsText
        set(value) {
            invalidateHierarchy()
            statsTextActor.setText(value)
            statsTextActor.isVisible = value.isNotBlank()
            field = value
        }

    var statsChangedText: String = initialStatsChangedText
        set(value) {
            invalidateHierarchy()
            statsChangedTextActor.setText(value)
            statsChangedTextActor.isVisible = value.isNotBlank()
            field = value
        }

    var forcedWidth = initialForcedWidth

    private var separatorPositions: List<Float> = listOf()

    private val components = arrayOf(
        flavourTextActor,
        descriptionActor,
        statsTextActor,
        statsChangedTextActor,
    )

    private var _prefHeight: Float = 0f

//    private var spacing = 1.8f

    init {
        components.forEach { component ->
            addActor(component)
            component.setFontScale(fontScale)
            component.wrap = true
            component.setAlignment(Align.center)
        }

        onEnter { isHoveredOver = true }
        onExit { isHoveredOver = false }
        invalidateHierarchy()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()

        // the calculated layout is sometimes just wrong, especially on the first frame the detail actor is displayed
        // this sanity check returns when that is the case
        if (height > screen.viewport.worldHeight) return

        if (batch == null) {
            super.draw(null, parentAlpha)
            return
        }

        validate()
        background?.draw(batch, x, y, width, height)

        super.draw(batch, parentAlpha)

        val separator = GraphicsConfig.cardDetailSeparator(screen)
        separatorPositions.forEach { position ->
            val lineWidth = forcedWidth * lineWidthMultiplier
            separator.draw(
                batch,
                x + forcedWidth / 2 - lineWidth / 2, y + position,
                lineWidth, lineHeight
            )
        }
    }

    override fun layout() {
        super.layout()
        val visibleComponents = components.filter { it.isVisible }
        var height = visibleComponents
            .map { it.prefHeight }
            .sum()
        height += visibleComponents.size * spacing
        var curY = height - spacing
        val separatorPositions = mutableListOf<Float>()
        visibleComponents.forEachIndexed { i, component ->
            component.setBounds(
                0f, curY - component.prefHeight,
                forcedWidth, component.prefHeight
            )
            if (i != 0) {
                separatorPositions.add(curY + spacing / 2 - lineHeight / 2)
            }
            curY -= component.height + spacing
        }
        this.separatorPositions = separatorPositions
        this.height = height
        _prefHeight = height
    }

    override fun getPrefWidth(): Float {
        return forcedWidth
    }

    override fun getPrefHeight(): Float = _prefHeight
}
