package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.CustomHorizontalGroup
import com.fourinachamber.fourtyfive.screen.general.CustomImageActor
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import com.fourinachamber.fourtyfive.screen.general.OnjScreen

class MapEventDetailWidget(
    private val screen: OnjScreen,
    private val font: BitmapFont,
    private val fontColor: Color
) : CustomHorizontalGroup() {

    private val descriptionWidget: CustomLabel = CustomLabel("", Label.LabelStyle(font, fontColor))

    fun setForEvent(mapEvent: MapEvent) {
        clearChildren()
        descriptionWidget.setText(mapEvent.descriptionText)
        mapEvent.icon?.let {
            val icon = CustomImageActor(ResourceManager.get(screen, it))
            addActor(icon)
        }
        mapEvent.additionalIcons.forEach { iconHandle ->
            val icon = CustomImageActor(ResourceManager.get(screen, iconHandle))
            addActor(icon)
        }
        addActor(descriptionWidget)
    }

}