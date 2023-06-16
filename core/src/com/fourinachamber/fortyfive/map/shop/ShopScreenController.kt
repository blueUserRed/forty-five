package com.fourinachamber.fortyfive.map.shop

import com.fourinachamber.fortyfive.map.detailMap.ShopMapEvent
import com.fourinachamber.fortyfive.map.dialog.DialogWidget
import com.fourinachamber.fortyfive.map.dialog.ShopWidget
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import onj.value.OnjObject

class ShopScreenController(onj: OnjObject): ScreenController() {

    private lateinit var screen: OnjScreen

    private lateinit var context: ShopMapEvent
    private val dialogWidgetName = onj.get<String>("shopWidgetName")
    private val shopFilePath = onj.get<String>("shopsFile")
    private lateinit var shopWidget: ShopWidget
}