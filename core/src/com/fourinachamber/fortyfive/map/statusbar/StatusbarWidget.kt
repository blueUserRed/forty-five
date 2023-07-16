package com.fourinachamber.fortyfive.map.statusbar

import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.EnterMapMapEvent
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import onj.value.OnjString
import onj.value.OnjValue

class StatusbarWidget(
    private val map_indicator_name: String,
    private val inCenter: Boolean,
    private val options: List<OnjValue>,
    private val screen: OnjScreen
) : CustomFlexBox(screen) {


    fun initSpecialChildren() {
        val mapIndicator = screen.namedActorOrError(map_indicator_name) as CustomFlexBox
        val curImage = MapManager.mapImages.find { it.name == MapManager.currentDetailMap.name }
        if (curImage == null || !MapManager.currentDetailMap.isArea) {
            screen.screenBuilder.generateFromTemplate(
                "statusbar_text",
                mapOf(Pair("text", OnjString("Road from "))),
                mapIndicator,
                screen
            )
            screen.screenBuilder.generateFromTemplate(
                "statusbar_sign",
                mapOf(
                    Pair(
                        "textureName",
                        OnjString(MapManager.mapImages.find { it.name == (MapManager.currentDetailMap.startNode.event as EnterMapMapEvent).targetMap }!!.resourceHandle)
                    )
                ),
                mapIndicator,
                screen
            )
            screen.screenBuilder.generateFromTemplate(
                "statusbar_text",
                mapOf(Pair("text", OnjString("to "))),
                mapIndicator,
                screen
            );
            screen.screenBuilder.generateFromTemplate(
                "statusbar_sign",
                mapOf(
                    Pair(
                        "textureName",
                        OnjString(MapManager.mapImages.find { it.name == (MapManager.currentDetailMap.endNode.event as EnterMapMapEvent).targetMap }!!.resourceHandle)
                    )
                ),
                mapIndicator,
                screen
            )
        } else {
            screen.screenBuilder.generateFromTemplate(
                "statusbar_sign",
                mapOf(Pair("textureName", OnjString(curImage.resourceHandle))),
                mapIndicator,
                screen
            )
        }
    }
}