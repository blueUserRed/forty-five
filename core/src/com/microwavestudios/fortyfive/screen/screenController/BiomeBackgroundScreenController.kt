package com.microwavestudios.fortyfive.screen.screenController

import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.ScreenController

class BiomeBackgroundScreenController(
    private val screen: RenderableScreen,
    private val zoom: Float = 1f
) : ScreenController() {

    var offX = 0f
    var offY = 0f

    override fun init(context: Any?) {
        val biome = FortyFive.profileManager.currentProfile?.currentMapSaver?.currentMap?.biome ?: return
        val backgrounds = GraphicsConfig.encounterBackgroundsFor(biome)
        val background = backgrounds.random().first
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
