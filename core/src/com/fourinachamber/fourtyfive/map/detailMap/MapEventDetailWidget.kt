package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.screen.ResourceHandle
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.*
import com.fourinachamber.fourtyfive.screen.general.styles.StyleCondition
import com.fourinachamber.fourtyfive.screen.general.styles.StyleInstruction
import com.fourinachamber.fourtyfive.screen.general.styles.StyleManager
import com.fourinachamber.fourtyfive.utils.MainThreadOnly
import ktx.actors.onClick

//class MapEventDetailWidget(
//    private val screen: OnjScreen,
//    private val font: BitmapFont,
//    private val fontColor: Color,
//    backgroundHandle: ResourceHandle
//) : CustomFlexBox(screen) {
//
//    private val descriptionWidget: CustomLabel = CustomLabel(screen, "", Label.LabelStyle(font, fontColor))
//
//    private val startButton: CustomLabel = CustomLabel(screen, "start", Label.LabelStyle(font, fontColor))
//
//    private val completedLabel: CustomLabel =
//        CustomLabel(screen, "already completed", Label.LabelStyle(font, fontColor))
//
//    var onStartClickedListener: (() -> Unit)? = null
//
//    init {
//        this.backgroundHandle = backgroundHandle
//        // TODO: put magic numbers in some file
//        descriptionWidget.setFontScale(0.05f)
//        startButton.setFontScale(0.05f)
//        completedLabel.setFontScale(0.05f)
//        width = 0f
//        height = 0f
//        startButton.onClick { onStartClickedListener?.invoke() }
//    }
//
//    @MainThreadOnly
//    fun setForEvent(mapEvent: MapEvent?) {
//        clearChildren()
//        mapEvent ?: return
//        descriptionWidget.setText(mapEvent.descriptionText)
//        mapEvent.icon?.let {
//            val icon = CustomImageActor(it, screen)
//            val node = add(icon)
//            node.aspectRatio = 1f
//            icon.setScale(0.01f)
//            icon.reportDimensionsWithScaling = true
//            icon.ignoreScalingWhenDrawing = true
//        }
//        mapEvent.additionalIcons.forEach { iconHandle ->
//            val icon = CustomImageActor(iconHandle, screen)
//            icon.setScale(0.016f)
//            icon.reportDimensionsWithScaling = true
//            icon.ignoreScalingWhenDrawing = true
//            add(icon)
//        }
//        add(descriptionWidget)
//        if (mapEvent.canBeStarted) add(startButton)
//        if (mapEvent.isCompleted) add(completedLabel)
//        invalidateHierarchy()
//    }
//
//}