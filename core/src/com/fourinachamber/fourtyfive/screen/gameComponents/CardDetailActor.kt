package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.game.GraphicsConfig
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.screen.general.CustomFlexBox
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import io.github.orioncraftmc.meditate.enums.YogaAlign
import io.github.orioncraftmc.meditate.enums.YogaEdge
import io.github.orioncraftmc.meditate.enums.YogaFlexDirection
import io.github.orioncraftmc.meditate.enums.YogaJustify
import ktx.actors.onEnter
import ktx.actors.onExit

class CardDetailActor(
    val card: Card,
    initialFlavourText: String,
    initialDescription: String,
    initialStatsText: String,
    initialStatsChangedText: String,
    private val font: BitmapFont,
    private val fontColor: Color,
    private val fontScale: Float,
    initialForcedWidth: Float,
    private val screen: OnjScreen,
    initialBackground: Drawable? = null
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
        CustomLabel(initialStatsChangedText, Label.LabelStyle(font, fontColor))

    private var requiresRebuild: Boolean = true

    var flavourText: String = initialFlavourText
        set(value) {
            requiresRebuild = true
            flavourTextActor.setText(value)
            field = value
        }

    var description: String = initialDescription
        set(value) {
            requiresRebuild = true
            descriptionActor.setText(value)
            field = value
        }

    var statsText: String = initialStatsText
        set(value) {
            requiresRebuild = true
            statsTextActor.setText(value)
            field = value
        }

    var statsChangedText: String = initialStatsChangedText
        set(value) {
            requiresRebuild = true
            statsChangedTextActor.setText(value)
            field = value
        }

    var forcedWidth = initialForcedWidth
        set(value) {
            field = value
            root.setWidth(value)
        }

    private var spacing = 1.8f

    init {
        background = initialBackground
        rebuild()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (requiresRebuild) {
            rebuild()
            requiresRebuild = false
        }
        super.draw(batch, parentAlpha)
        val separator = GraphicsConfig.cardDetailSeparator(screen)
        if (batch == null) return
        for (i in 0 until children.size) {
            if (i == 0) continue
            val actor = children[i]
            if (!actor.isVisible) continue
            separator.draw(
                batch,
                x + width / 2 - (width * 0.8f) / 2,
                y + actor.y + actor.height + spacing / 2,
                width * 0.8f,
                spacing / 6f
            )
        }
    }

    private fun rebuild() {
        spacing = GraphicsConfig.cardDetailSpacing()
        clear()
        arrayOf(
            flavourTextActor,
            descriptionActor,
            statsTextActor,
            statsChangedTextActor
        ).forEachIndexed { i, actor ->
            if (!actor.isVisible || actor.textEquals("")) return@forEachIndexed
            val node = add(actor)
            node.setWidthPercent(100f)
            if (i != 0) node.setMargin(YogaEdge.TOP, spacing)
            actor.setFontScale(fontScale)
            actor.setAlignment(Align.center)
            actor.wrap = true
        }
        root.flexDirection = YogaFlexDirection.COLUMN
        root.setPadding(YogaEdge.ALL, spacing)
//        root.alignItems = YogaAlign.CENTER
//        root.justifyContent = YogaJustify.CENTER
        root.setHeightAuto()

        touchable = Touchable.enabled
        onEnter { isHoveredOver = true }
        onExit { isHoveredOver = false }
        layout() // Without this the height is not set correctly on the first frame, don't ask me why
    }

    override fun getPrefWidth(): Float {
        return forcedWidth
    }

}
