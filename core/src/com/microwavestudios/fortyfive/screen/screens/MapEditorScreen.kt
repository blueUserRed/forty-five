package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.map.*
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import java.io.File
import kotlin.reflect.KClass

class MapEditorScreen : ScreenCreator() {

    override val name: String = "mapEditorScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "white_texture"
    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)
    override val playAmbientSounds: Boolean = false
    override val transitionAwayTimes: Map<String, Int> = mapOf("*" to 0)

    private val events: EventPipeline = EventPipeline()

    private val context: MapEditorContext by lazy { context() }


    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        val map =
            context.map?.let { DetailMapBuilder.from(it) }
            ?: context.mapPath?.let { DetailMap.readFromFile(File(it)) }?.let { DetailMapBuilder.from(it) }
            ?: run {
                val startNode = MapNodeBuilder(index = 0, x = -30f, y = 0f)
                val endNode = MapNodeBuilder(index = 1, x = 60f, y = 0f)
                startNode.connect(endNode)
                DetailMapBuilder(startNode = startNode, endNode = endNode)
            }

        val widget = MapEditorWidget(
            mapBuilder = map,
            mapScale = 10f,
            screen = screen,
            nodeSize = 60f,
            lineWidth = 10f,
            events = events
        )
        actor(widget) {
            width = worldWidth
            height = worldHeight
            x = 0f
            y = 0f
        }

        popup()

        toolBar()
    }

    private fun CustomGroup.popup() = group {
        backgroundHandle = "map_detail_background"
        width = worldWidth * 0.23f
        height = worldHeight * 0.8f
        y = (worldHeight / 2 - height / 2)
        x = worldWidth - width + 10f

        decorationPage()
    }

    private fun CustomGroup.decorationPage() = box {
        width = worldWidth * 0.23f
        height = worldHeight * 0.8f
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.START
        verticalAlign = CustomAlign.START
        isVisible = false

        val handle = label("red_wing", "", Color.FortyWhite) {
            syncDimensions()
        }
        val isBackground = label("red_wing", "", Color.FortyWhite) {
            syncDimensions()
        }

        events.watchFor<MapEditorWidget.DisplayDecorationEvent> { (decoration) ->
            isVisible = decoration != null
            decoration ?: return@watchFor
            handle.setText(decoration.drawableHandle)
            isBackground.setText("background: ${decoration.drawInBackground}")
        }
    }

    private fun CustomGroup.toolBar() = box {
        y = 0f
        x = 0f
        width = worldWidth
        height = 30f
        backgroundHandle = "white_texture"
        flexDirection = FlexDirection.ROW

        label("roadgeek", "Mode:") {
            setFontScale(0.7f)
            height = 30f
            syncWidth()
        }
        horizontalSpacer(10f)

        label("roadgeek", "Node") {
            setFontScale(0.7f)
            height = 30f
            syncWidth()
            events.watchFor<MapEditorWidget.ModeChangedEvent> { (newMode) -> setText(newMode.displayName) }
        }
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
    )

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = MapEditorScreen::class
    }

}

interface MapEditorContext {
    val map: DetailMap?
    val mapPath: String?
}
