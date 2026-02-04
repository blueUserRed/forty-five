package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.Game
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.map.*
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.setText
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import java.io.File
import java.nio.file.Paths
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.reflect.KClass

class MapEditorScreen : ScreenCreator() {

    override val name: String = "mapEditorScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "white_texture"
    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)
    override val playAmbientSounds: Boolean = false

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        "*" to noTransition()
    )

    private val events: EventPipeline = EventPipeline()

    private val context: MapEditorContext by lazy { context() }

    private lateinit var map: DetailMapBuilder

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
        this@MapEditorScreen.map = map

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

        topBar()
        toolBar()

        events.fire(SaveLocationChangedEvent)
    }

    private fun CustomGroup.popup() = group {
        backgroundHandle = "map_detail_background"
        width = worldWidth * 0.23f
        height = worldHeight * 0.8f
        y = (worldHeight / 2 - height / 2)
        x = worldWidth - width + 10f

        nodePage()
        decorationPage()
    }

    private fun CustomGroup.nodePage() = box {
        width = worldWidth * 0.2f
        height = worldHeight * 0.8f
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.START
        verticalAlign = CustomAlign.START
        isVisible = false
        centerX()
        y = -20f

        val index = label("roadgeek", "", Color.FortyWhite, (24 * 0.7).toInt()) {
            syncDimensions()
        }
        val nodeTexture = label("roadgeek", "", Color.FortyWhite, (24 * 0.7).toInt()) {
            syncDimensions()
            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.enabled
            onInput(GameInputs.interact) { events.fire(CycleNodeTextureEvent) }
        }
        val makeStart = label("roadgeek", "make start node", Color.FortyWhite, (24 * 0.7).toInt()) {
            syncDimensions()
            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.enabled
            onInput(GameInputs.interact) { events.fire(MakeStartNodeEvent) }
        }
        val makeEnd = label("roadgeek", "make end node", Color.FortyWhite, (24 * 0.7).toInt()) {
            syncDimensions()
            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.enabled
            onInput(GameInputs.interact) { events.fire(MakeEndNodeEvent) }
        }

        events.watchFor<MapEditorWidget.DisplayDecorationEvent> { isVisible = false }
        events.watchFor<MapEditorWidget.DisplayNodePageEvent> { (node) ->
            isVisible = node != null
            node ?: return@watchFor
            index.setText("index: ${node.index}")
            nodeTexture.setText("texture: ${node.nodeTexture ?: "null"}")
            val startEndNodeVisible = map.startNode != node && map.endNode != node
            makeStart.isVisible = startEndNodeVisible
            makeEnd.isVisible = startEndNodeVisible
        }
    }

    private fun CustomGroup.decorationPage() = box {
        width = worldWidth * 0.2f
        height = worldHeight * 0.8f
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.START
        verticalAlign = CustomAlign.START
        isVisible = false
        centerX()
        y = -20f

        val handle = label("roadgeek", "", Color.FortyWhite, (24 * 0.7).toInt()) {
            syncDimensions()
        }
        val isAnimated = label("roadgeek", "", Color.FortyWhite, (24 * 0.7).toInt()) {
            syncDimensions()
        }
        val isBackground = label("roadgeek", "", Color.FortyWhite, (24 * 0.7).toInt()) {
            syncDimensions()
        }

        events.watchFor<MapEditorWidget.DisplayNodePageEvent> { isVisible = false }
        events.watchFor<MapEditorWidget.DisplayDecorationEvent> { (decoration) ->
            isVisible = decoration != null
            decoration ?: return@watchFor
            val drawableHandle = decoration.drawableHandle.removePrefix("map_decoration_")
            handle.setText(drawableHandle)
            isAnimated.setText("animated: ${decoration.animated}")
            isBackground.setText("background: ${decoration.drawInBackground}")
        }
    }

    private fun CustomGroup.topBar() = box {
        height = 60f
        width = worldWidth
        x = 0f
        onLayout { y = worldHeight - height }
        flexDirection = FlexDirection.ROW
        verticalAlign = CustomAlign.START
        horizontalAlign = CustomAlign.START

        horizontalSpacer(20f)
        label("roadgeek", "Load", Color.FortyWhite, 24) {
            backgroundHandle = "transparent_black_texture"
            height = 50f
            syncWidth()
            setAlignment(Align.center)
            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.enabled
            onInput(GameInputs.interact) { loadMap() }
        }
        horizontalSpacer(20f)
        label("roadgeek", "Save", Color.FortyWhite, 24) {
            backgroundHandle = "transparent_black_texture"
            height = 50f
            syncWidth()
            setAlignment(Align.center)
            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.enabled
            onInput(GameInputs.interact) { save() }
        }
        horizontalSpacer(20f)
        label("roadgeek", "Save To", Color.FortyWhite, 24) {
            backgroundHandle = "transparent_black_texture"
            height = 50f
            syncWidth()
            setAlignment(Align.center)
            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.enabled
            onInput(GameInputs.interact) { saveTo() }
        }
        horizontalSpacer(20f)
        label("roadgeek", "", fontSize = (24 * 0.7).toInt()) {
            backgroundHandle = "white_texture"
            height = 30f
            syncWidth()

            events.watchFor<SaveLocationChangedEvent> {
                val path = context.mapPath
                setText(path ?: "<no file>")
            }
        }
    }

    private fun save() {
        val path = context.mapPath ?: run {
            FortyFive.soundPlayer.situation("not_allowed", screen)
            return
        }
        val file = File(path)
        val event = BuildMapEvent()
        events.fire(event)
        file.writeText(event.map!!.asOnjObject().toString())
    }

    private fun saveTo() {
        val fileChooser = JFileChooser()
        val filter = FileNameExtensionFilter("Forty-Five map", "onj")
        fileChooser.currentDirectory = Paths.get("").toFile().canonicalFile
        fileChooser.fileFilter = filter
        val result = fileChooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return
        val file = fileChooser.selectedFile
        if (!file.exists()) file.createNewFile()
        val event = BuildMapEvent()
        events.fire(event)
        file.writeText(event.map!!.asOnjObject().toString())
        context.mapPath = file.canonicalPath
        events.fire(SaveLocationChangedEvent)
    }

    private fun loadMap() {
        val fileChooser = JFileChooser()
        val filter = FileNameExtensionFilter("Forty-Five map", "onj")
        fileChooser.currentDirectory = Paths.get("").toFile().canonicalFile
        fileChooser.fileFilter = filter
        val result = fileChooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return
        val file = fileChooser.selectedFile
        FortyFive.screenManager.appendScreen(MapEditorScreen, object : MapEditorContext {
            override val map: DetailMap? = null
            override var mapPath: String? = file.canonicalPath
        })
        FortyFive.screenManager.screenFinished()
    }

    private fun CustomGroup.toolBar() = box {
        y = 0f
        x = 0f
        width = worldWidth
        height = 30f
        backgroundHandle = "white_texture"
        flexDirection = FlexDirection.ROW

        label("roadgeek", "Mode:", fontSize = (24 * 0.7).toInt()) {
            height = 30f
            syncWidth()
        }
        horizontalSpacer(10f)

        label("roadgeek", "Node", fontSize = (24 * 0.7).toInt()) {
            height = 30f
            syncWidth()
            events.watchFor<MapEditorWidget.ModeChangedEvent> { (newMode) -> setText(newMode.displayName) }
        }
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
    )

    private data object SaveLocationChangedEvent
    data object MakeStartNodeEvent
    data object MakeEndNodeEvent
    data object CycleNodeTextureEvent
    data class BuildMapEvent(var map: DetailMap? = null)

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = MapEditorScreen::class
    }

}

interface MapEditorContext {
    val map: DetailMap?
    var mapPath: String?
}
