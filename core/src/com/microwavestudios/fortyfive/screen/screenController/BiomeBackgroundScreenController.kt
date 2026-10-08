package com.microwavestudios.fortyfive.screen.screenController

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.ScreenController

class BiomeBackgroundScreenController(
    private val screen: RenderableScreen,
    private val zoom: Float = 1f
) : ScreenController() {

    var offX = 0f
    var offY = 0f

    private var forceBackground: ResourceHandle? = null

    private var chosenBackground: ResourceHandle? = null

    override fun init(context: Any?) {
        val background = forceBackground ?: run {
            val biome = FortyFive.profileManager.currentProfile?.currentMapSaver?.currentMap?.biome ?: return
            val backgrounds = GraphicsConfig.encounterBackgroundsFor(biome)
            backgrounds.random().first
        }
        chosenBackground = background
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

    fun enemyCoordsForChosenBackground(): Array<Vector2> {
        val chosenBackground = chosenBackground
        requireNotNull(chosenBackground) { "cant call 'enemyCoordsForChosenBackground' before screen initialised" }
        val coords = screenEnemyPositions[chosenBackground]
        requireNotNull(coords) { "no enemy positions configured for background $chosenBackground" }
        return coords
    }

    fun forceBackground(background: ResourceHandle) {
        forceBackground = background
    }

    companion object {

        // TODO: move to graphics_config.onj
        private val screenEnemyPositions = mutableMapOf<String, Array<Vector2>>(
            "background_wasteland1" to arrayOf(
                Vector2(696f, 416f),
                Vector2(986f, 253f),
                Vector2(1234f, 411f),
            ),
            "background_wasteland2" to arrayOf(
                Vector2(570f, 483f),
                Vector2(830f, 294f),
                Vector2(1214f, 420f),
            ),
            "background_wasteland3" to arrayOf(
                Vector2(658f, 403f),
                Vector2(898f, 275f),
                Vector2(1214f, 420f),
            ),
        )
    }
}
