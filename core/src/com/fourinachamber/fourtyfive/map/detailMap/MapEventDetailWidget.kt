package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.*
import com.fourinachamber.fourtyfive.utils.MainThreadOnly
import ktx.actors.onClick

class MapEventDetailWidget(
    private val screen: OnjScreen,
    private val font: BitmapFont,
    private val fontColor: Color,
    background: Drawable,
    private val onStartClickedListener: () -> Unit
) : CustomVerticalGroup() {

    private val descriptionWidget: CustomLabel = CustomLabel("", Label.LabelStyle(font, fontColor))

    private val startButton: CustomLabel = CustomLabel("start", Label.LabelStyle(font, fontColor))

    private val completedLabel: CustomLabel = CustomLabel("already completed", Label.LabelStyle(font, fontColor))

    init {
        this.background = background
        // TODO: put magic numbers in some file
        descriptionWidget.setFontScale(0.05f)
        startButton.setFontScale(0.05f)
        width = 0f
        height = 0f
        startButton.onClick { onStartClickedListener() }
    }

    @MainThreadOnly
    fun setForEvent(mapEvent: MapEvent) {
        clearChildren()
        descriptionWidget.setText(mapEvent.descriptionText)
        mapEvent.icon?.let {
            val icon = CustomImageActor(ResourceManager.get(screen, it))
            icon.setScale(0.03f)
            icon.reportDimensionsWithScaling = true
            icon.ignoreScalingWhenDrawing = true
            addActor(icon)
        }
        mapEvent.additionalIcons.forEach { iconHandle ->
            val icon = CustomImageActor(ResourceManager.get(screen, iconHandle))
            icon.setScale(0.016f)
            icon.reportDimensionsWithScaling = true
            icon.ignoreScalingWhenDrawing = true
            addActor(icon)
        }
        addActor(descriptionWidget)
        if (mapEvent.canBeStarted) addActor(startButton)
        if (mapEvent.isCompleted) addActor(completedLabel)
        invalidateHierarchy()
    }

}