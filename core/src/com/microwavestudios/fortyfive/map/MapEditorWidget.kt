package com.microwavestudios.fortyfive.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.keyInput.InputActorImpl
import com.microwavestudios.fortyfive.keyInput.MouseButton
import com.microwavestudios.fortyfive.rendering.BetterShader
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.actors.ZIndexActor
import com.microwavestudios.fortyfive.screen.screens.MapEditorScreen
import com.microwavestudios.fortyfive.utils.*
import kotlin.math.asin
import kotlin.math.ceil

class MapEditorWidget(
    val mapBuilder: DetailMapBuilder,
    private val mapScale: Float,
    private val screen: OnjScreen,
    private val nodeSize: Float,
    private val lineWidth: Float,
    private val events: EventPipeline
) : Widget(), ZIndexActor, InputActor by InputActorImpl(), ResourceBorrower {

    override var fixedZIndex: Int = 0

    var mapOffset: Vector2 = Vector2(50f, 50f)

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    var backgroundHandle: String? by backgroundHandleObserver
    private val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen.lifetime, arrayOf())

    private val shapeRenderer: ShapeRenderer = ShapeRenderer()

    private val edgeShader: Promise<BetterShader> =
        FortyFive.resourceManager.request(this, screen.lifetime, "map_edge_shader")

    private val nodeDrawable: Promise<Drawable> = FortyFive.resourceManager.request(this, screen.lifetime, "map_node_default")
    private val edgeTexture: Promise<TextureRegion> = FortyFive.resourceManager.request(this, screen.lifetime, "map_path")

    private var screenDragged: Boolean = false

    private val dragListener = object : DragListener() {

        private var dragStartPosition: Vector2? = null
        private var mapOffsetOnDragStart: Vector2? = null

        override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            super.dragStart(event, x, y, pointer)
            dragStartPosition = Vector2(x, y)
            mapOffsetOnDragStart = mapOffset
            screenDragged = true
        }

        override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            super.drag(event, x, y, pointer)
            val dragStartPosition = dragStartPosition ?: return
            val mapOffsetOnDragStart = mapOffsetOnDragStart ?: return
            val draggedDistance = dragStartPosition - Vector2(x, y)
            mapOffset = mapOffsetOnDragStart - draggedDistance
        }

        override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            super.dragStop(event, x, y, pointer)
            dragStartPosition = null
            mapOffsetOnDragStart = null
        }
    }

    private val lastMousePos: Vector2 = Vector2()

    private val clickListener = object : ClickListener() {

        val maxClickTime: Long = 300

        private var lastTouchDownTime: Long = 0

        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            lastTouchDownTime = TimeUtils.millis()
            if (button != MouseButton.LEFT.code) handleClick(x, y, button)
            return super.touchDown(event, x, y, pointer, button)
        }

        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            event ?: return
            val screenDragged = screenDragged
            this@MapEditorWidget.screenDragged = false
            if (screenDragged || TimeUtils.millis() > lastTouchDownTime + maxClickTime) return
            if (button == MouseButton.LEFT.code) handleClick(x, y, event.button)
        }

        override fun mouseMoved(event: InputEvent?, x: Float, y: Float): Boolean {
            lastMousePos.set(x, y)
            return super.mouseMoved(event, x, y)
        }
    }

    private val nodes: MutableList<MapNodeBuilder> = mutableListOf()
    private val edges: MutableList<Pair<MapNodeBuilder, MapNodeBuilder>> = mutableListOf()

    private var selectedNode: MapNodeBuilder? = null
    private var tool: Tool? = null

    init {
        initInput(this, screen)
        addListener(dragListener)
        addListener(clickListener)
        eventHandlers()

        val biome = MapEditorBiome.entries.find { it.internalName == mapBuilder.biome }!!
        backgroundHandle = biome.background

        mapOffset.set(screen.viewport.worldWidth / 2f, screen.viewport.worldHeight / 2f)

        val uniqueNodes = mapBuilder.uniqueNodes()
        val uniqueEdges = mapBuilder.uniqueEdges(uniqueNodes)
        nodes.addAll(uniqueNodes)
        edges.addAll(uniqueEdges)
        nodes.forEachIndexed { i, node -> node.index = i }
    }

    private fun eventHandlers() {
        onInput(GameInputs.mapEditorDelete) { delete() }
        onInput(GameInputs.mapEditorConnect) {
            tool = null
            if (selectedNode != null) tool = Tool.CONNECT
        }
        onInput(GameInputs.mapEditorMove) {
            tool = null
            if (selectedNode != null) tool = Tool.MOVE
        }
    }

    private fun handleClick(x: Float, y: Float, button: Int) {
        when (button) {
            MouseButton.LEFT.code -> handleLeftClick(x, y)
            MouseButton.RIGHT.code -> handleRightClick(x, y)
        }
    }

    private fun handleRightClick(x: Float, y: Float) {
        val mapCoords = screenToMapSpace(Vector2(x, y) - Vector2(nodeSize / 2, nodeSize / 2))
        placeNode(mapCoords)
    }

    private fun handleLeftClick(x: Float, y: Float) {
        val clickedNode = findNodeAtPosition(x, y)
        if (tool == Tool.CONNECT) {
            val selectedNode = selectedNode
            if (clickedNode != null && selectedNode != null && clickedNode != selectedNode) {
                connect(selectedNode, clickedNode)
            }
            tool = null
        } else if (tool == Tool.MOVE) {
            tool = null
        } else {
            selectedNode = clickedNode
        }
    }

    private fun connect(node1: MapNodeBuilder, node2: MapNodeBuilder) {
        if (node2 in node1.edgesTo) return
        node1.connect(node2)
        require(node1.index != node2.index)
        val edge = if (node1.index > node2.index) node1 to node2 else node2 to node1
        edges.add(edge)
    }

    private fun findNodeAtPosition(x: Float, y: Float): MapNodeBuilder? {
        val pos = Vector2(x, y)
        val radius = nodeSize / 2
        nodes.forEach { node ->
            val center = scaledNodePos(node) + mapOffset + Vector2(radius, radius)
            val distance = (pos - center).len()
            if (distance <= radius * 2) return node
        }
        return null
    }

    private fun delete() {
        selectedNode?.let {
            selectedNode = null
            deleteNode(it)
        }
    }

    private fun deleteNode(toDelete: MapNodeBuilder) {
        if (!nodes.remove(toDelete)) return
        nodes.forEachIndexed { i, node -> node.index = i }
        toDelete.edgesTo.forEach { edgeNode ->
            edgeNode.edgesTo.remove(toDelete)
        }
        edges.iterateRemoving { edge, remover ->
            if (edge.first == toDelete || edge.second == toDelete) remover()
        }
    }

    private fun placeNode(coords: Vector2) {
        val node = MapNodeBuilder(nodes.size, coords.x, coords.y)
        nodes.add(node)
    }

    private fun updateMoveTool() {
        if (tool != Tool.MOVE) return
        val selectedNode = selectedNode ?: return
        val pos = screenToMapSpace(lastMousePos - Vector2(nodeSize / 2, nodeSize / 2))
        selectedNode.x = pos.x
        selectedNode.y = pos.y
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()
        updateMoveTool()

        batch ?: return
        val viewport = screen.stage.viewport
        val scissor = Rectangle(
            0f, viewport.bottomGutterHeight.toFloat(),
            (Gdx.graphics.width / viewport.worldWidth) * width,
            ((Gdx.graphics.height - viewport.topGutterHeight - viewport.bottomGutterHeight) / viewport.worldHeight) * height
        )
        if (!ScissorStack.pushScissors(scissor)) return

        drawBackground(batch)
        drawEdges(batch)
        drawNodes(batch)
        drawConnectingEdge(batch)

        batch.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        viewport.apply()
        shapeRenderer.projectionMatrix = viewport.camera.combined

        drawSelectedNodeIndicator()

        shapeRenderer.flush()
        shapeRenderer.end()
        batch.begin()
    }

    private fun drawSelectedNodeIndicator() {
        val node = selectedNode ?: return
        val coords = scaledNodePos(node) + mapOffset
        shapeRenderer.color = Color.Blue
        Gdx.gl.glLineWidth(20f)
        shapeRenderer.circle(x + coords.x + nodeSize / 2, y + coords.y + nodeSize / 2, nodeSize / 2)
    }

    private fun drawBackground(batch: Batch) {
        val background = background ?: return
        val minWidth = background.minWidth
        val minHeight = background.minHeight
        val amountX = ceil(width / minWidth).toInt() + 2
        val amountY = ceil(height / minHeight).toInt() + 2
        var curX = x - minWidth + (mapOffset.x % minWidth)
        var curY = y - minHeight + (mapOffset.y % minHeight)
        repeat(amountX) {
            repeat(amountY) {
                background.draw(batch, curX, curY, minWidth, minHeight)
                curY += minHeight
            }
            curY = y - minHeight + (mapOffset.y % minHeight)
            curX += minWidth
        }
    }

    private fun drawNodes(batch: Batch) {
        nodes.forEach { node ->
            val (nodeX, nodeY) = scaledNodePos(node) + mapOffset
            val drawable = node.getNodeTexture(screen) ?: nodeDrawable
            drawable.getOrNull()?.draw(batch, x + nodeX, y + nodeY, nodeSize, nodeSize)
        }
    }

    private fun drawEdges(batch: Batch) {
        batch.flush()
        val edgeShader = edgeShader.getOrNull() ?: return
        val edgeTexture = edgeTexture.getOrNull() ?: return
        edgeShader.prepare(screen)
        batch.shader = edgeShader.shader
        for ((node1, node2) in edges) {
            val node1Pos = scaledNodePos(node1)
            val node2Pos = scaledNodePos(node2)
            val dy = node2Pos.y - node1Pos.y
            val dx = node2Pos.x - node1Pos.x
            val length = Vector2(dx, dy).len()
            var angle = Math.toDegrees(asin((dy / length).toDouble())).toFloat() - 90f
            if (dx < 0) angle = 360 - angle
            edgeShader.shader.setUniformf("u_lineLength", length)
            batch.draw(
                edgeTexture,
                x + node1Pos.x + mapOffset.x + nodeSize / 2 - lineWidth / 2,
                y + node1Pos.y + mapOffset.y + nodeSize / 2,
                lineWidth / 2, 0f,
                lineWidth,
                length,
                1.0f, 1.0f,
                angle
            )
            batch.flush()
        }
        batch.shader = null
    }

    private fun drawConnectingEdge(batch: Batch) {
        if (tool != Tool.CONNECT) return
        val selectedNode = selectedNode ?: return
        batch.flush()
        val edgeShader = edgeShader.getOrNull() ?: return
        val edgeTexture = edgeTexture.getOrNull() ?: return
        edgeShader.prepare(screen)
        batch.shader = edgeShader.shader
        val firstPos = scaledNodePos(selectedNode) + mapOffset
        val secondPos = lastMousePos - Vector2(nodeSize / 2, nodeSize / 2)
        val dy = secondPos.y - firstPos.y
        val dx = secondPos.x - firstPos.x
        val length = Vector2(dx, dy).len()
        var angle = Math.toDegrees(asin((dy / length).toDouble())).toFloat() - 90f
        if (dx < 0) angle = 360 - angle
        edgeShader.shader.setUniformf("u_lineLength", length)
        batch.draw(
            edgeTexture,
            x + firstPos.x + nodeSize / 2 - lineWidth / 2,
            y + firstPos.y + nodeSize / 2,
            lineWidth / 2, 0f,
            lineWidth,
            length,
            1.0f, 1.0f,
            angle
        )
        batch.flush()

        batch.shader = null
    }

    private fun screenToMapSpace(screen: Vector2): Vector2 = (screen - mapOffset) / mapScale

    private fun scaledNodePos(node: MapNodeBuilder): Vector2 = Vector2(node.x, node.y) * mapScale

    enum class Tool(val displayName: String) {
        CONNECT("Connect"),
        MOVE("Move"),
    }

    enum class MapEditorBiome(val internalName: String, val displayName: String, val background: String) {
        WASTELAND("wasteland", "Wasteland","map_background_wasteland_tileable"),
        BEWITCHED_FOREST("bewitched_forest", "Bewitched Forest","map_background_bewitched_forest_tileable"),
        MAGENTA_MOUNTAINS("magenta_mountains", "Magenta Mountains","map_background_magenta_mountains_tileable",)
    }
}
