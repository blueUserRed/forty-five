package com.fourinachamber.fortyfive.screen.gameComponents

import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import onj.value.OnjObject

class BiomeBackgroundScreenController(private val screen: OnjScreen, private val useSecondary: Boolean) : ScreenController() {

    override fun init(context: Any?) {
        val background = if (useSecondary) {
            GraphicsConfig.secondaryBackgroundFor(MapManager.currentDetailMap.biome)
        } else {
            GraphicsConfig.encounterBackgroundFor(MapManager.currentDetailMap.biome)
        }
        screen.background = background
    }
}
