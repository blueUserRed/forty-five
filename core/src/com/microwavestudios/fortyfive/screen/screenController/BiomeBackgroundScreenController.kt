package com.microwavestudios.fortyfive.screen.screenController

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.map.MapManager
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.ScreenController

class BiomeBackgroundScreenController(private val screen: OnjScreen, private val useSecondary: Boolean) : ScreenController() {

    override fun init(context: Any?) {
        val biome = FortyFive.profileManager.currentProfile?.currentMapSaver?.currentMap?.biome ?: return
        val background = if (useSecondary) {
            GraphicsConfig.secondaryBackgroundFor(biome)
        } else {
            GraphicsConfig.encounterBackgroundFor(biome)
        }
        screen.background = background
    }
}
