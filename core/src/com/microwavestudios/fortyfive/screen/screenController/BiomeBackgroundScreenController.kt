package com.microwavestudios.fortyfive.screen.screenController

import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.ScreenController
import kotlin.math.sin

class BiomeBackgroundScreenController(
    private val screen: OnjScreen,
    private val useSecondary: Boolean,
    private val zoom: Float = 1f
) : ScreenController() {

    var offX = 0f
    var offY = 0f

    override fun init(context: Any?) {
        val biome = FortyFive.profileManager.currentProfile?.currentMapSaver?.currentMap?.biome ?: return
        val background = if (useSecondary) {
            GraphicsConfig.secondaryBackgroundFor(biome)
        } else {
            GraphicsConfig.encounterBackgroundFor(biome)
        }
        val bg = FortyFive.resourceManager.request<Drawable>(screen, screen.lifetime, background)
        screen.addEarlyRenderTask { batch ->
            val bg = bg.getOrNull() ?: return@addEarlyRenderTask
            val worldWidth = screen.viewport.worldWidth
            val worldHeight = screen.viewport.worldHeight
            bg.draw(
                batch,
                -(worldWidth * (zoom - 1) / 2) + offX,
                -(worldHeight * (zoom - 1) / 2) + offY,
                worldWidth * zoom,
                worldHeight * zoom,
            )
        }
    }
}
