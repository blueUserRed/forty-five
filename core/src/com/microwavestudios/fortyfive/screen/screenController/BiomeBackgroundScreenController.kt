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

    fun enemyCoordsForChosenBackground(): Array<Pair<Vector2, Float>> {
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
        private val screenEnemyPositions = mutableMapOf<String, Array<Pair<Vector2, Float>>>(
            "background_wasteland1" to arrayOf(
                Vector2(700f, 362f) to 0.7f,
                Vector2(950f, 362f) to 0.7f,
                Vector2(1200f, 362f) to 0.7f,
            ),
            "background_wasteland2" to arrayOf(
                Vector2(700f, 260f) to 0.8f,
                Vector2(920f, 290f) to 0.7f,
                Vector2(1190f, 410f) to 0.6f,
            ),
            "background_wasteland3" to arrayOf(
                Vector2(780f, 403f) to 0.6f,
                Vector2(1010f, 250f) to 0.8f,
                Vector2(1214f, 390f) to 0.6f,
            ),
            "background_wasteland4" to arrayOf(
                Vector2(780f, 250f) to 0.7f,
                Vector2(1010f, 280f) to 0.7f,
                Vector2(1220f, 270f) to 0.7f,
            ),
            "background_wasteland5" to arrayOf(
                Vector2(910f, 410f) to 0.5f,
                Vector2(1105f, 330f) to 0.6f,
                Vector2(1300f, 270f) to 0.7f,
            ),
        )
    }
}
