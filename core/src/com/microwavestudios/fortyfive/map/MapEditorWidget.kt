package com.microwavestudios.fortyfive.map

import com.badlogic.gdx.Game
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
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.screen.actors.ZIndexActor
import com.microwavestudios.fortyfive.screen.screens.MapEditorScreen
import com.microwavestudios.fortyfive.utils.*
import kotlin.math.asin
import kotlin.math.ceil

class MapEditorWidget(
    val mapBuilder: DetailMapBuilder,
    private val mapScale: Float,
    private val screen: CustomScreen,
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

    private var mode: Mode = Mode.NODE
        set(value) {
            field = value
            events.fire(ModeChangedEvent(value))
        }

    private var tool: Tool? = null

    private var scaleOriginalMousePos: Vector2 = Vector2()
    private var originalScale: Float = 0f

    private var selectedNode: MapNodeBuilder? = null
        set(value) {
            field = value
            if (mode != Mode.NODE) return
            events.fire(DisplayNodePageEvent(value))
        }

    private var selectedDecoration: Pair<MapDecorationBuilder, MapDecorationBuilderInstance>? = null
        set(value) {
            field = value
            if (value == null) return
            val (decoration, _) = value
            val protoIndex = decorationPrototypes.indexOfFirst {
                it.drawInBackground == decoration.drawInBackground &&
                it.baseWidth == decoration.baseWidth &&
                it.baseHeight == decoration.baseHeight &&
                it.drawableHandle == decoration.drawableHandle
            }
            require(protoIndex >= 0) { "Selected decoration has no matching prototype" }
            val proto = decorationPrototypes[protoIndex]
            currentDecoProtoIndex = protoIndex
            events.fire(DisplayDecorationEvent(proto))
        }

    private var currentDecoProtoIndex: Int = 0
    private val decorationPrototypes: MutableList<MapDecorationPrototype> = mutableListOf()

    init {
        initInput(this, screen)
        addListener(dragListener)
        addListener(clickListener)
        eventHandlers()
        mode = Mode.NODE

        decorationPrototypes.addAll(MapDecorationPrototype.standardDecorations)
        mapBuilder
            .decorations
            .map { MapDecorationPrototype(
                it.drawableHandle,
                it.baseWidth,
                it.baseHeight,
                it.drawInBackground
            ) }
            .forEach {
                if (it !in decorationPrototypes) decorationPrototypes.add(it)
            }

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
            if (selectedNode != null || selectedDecoration != null) tool = Tool.MOVE
        }
        onInput(GameInputs.mapEditorScale) {
            tool = null
            val selectedDecoration = selectedDecoration
            if (selectedDecoration != null) {
                tool = Tool.SCALE
                scaleOriginalMousePos = lastMousePos.cpy()
                originalScale = selectedDecoration.second.scale
            }
        }
        onInput(GameInputs.mapEditorNext) {
            if (mode != Mode.DECORATION) return@onInput
            currentDecoProtoIndex++
            if (currentDecoProtoIndex >= decorationPrototypes.size) currentDecoProtoIndex = 0
            events.fire(DisplayDecorationEvent(decorationPrototypes[currentDecoProtoIndex]))
        }
        onInput(GameInputs.mapEditorPrevious) {
            if (mode != Mode.DECORATION) return@onInput
            currentDecoProtoIndex--
            if (currentDecoProtoIndex < 0) currentDecoProtoIndex = decorationPrototypes.size - 1
            events.fire(DisplayDecorationEvent(decorationPrototypes[currentDecoProtoIndex]))
        }
        onInput(GameInputs.mapEditorSwitchMode) {
            tool = null
            selectedNode = null
            selectedDecoration = null
            if (mode == Mode.DECORATION) {
                mode = Mode.NODE
                events.fire(DisplayDecorationEvent(null))
                events.fire(DisplayNodePageEvent(selectedNode))
            } else {
                mode = Mode.DECORATION
                events.fire(DisplayNodePageEvent(null))
                events.fire(DisplayDecorationEvent(decorationPrototypes[currentDecoProtoIndex]))
            }
        }
        events.watchFor<MapEditorScreen.BuildMapEvent> { event ->
            event.map = mapBuilder.build()
        }
        events.watchFor<MapEditorScreen.MakeStartNodeEvent> {
            val node = selectedNode ?: return@watchFor
            if (mapBuilder.endNode == node) return@watchFor
            mapBuilder.startNode = node
        }
        events.watchFor<MapEditorScreen.MakeEndNodeEvent> {
            val node = selectedNode ?: return@watchFor
            if (mapBuilder.startNode == node) return@watchFor
            mapBuilder.endNode = node
        }
        events.watchFor<MapEditorScreen.CycleNodeTextureEvent> {
            val node = selectedNode ?: return@watchFor
            var index = nodeTextures.indexOf(node.nodeTexture)
            if (index < 0) return@watchFor
            index++
            if (index >= nodeTextures.size) index = 0
            node.nodeTexture = nodeTextures[index]
            node.invalidateCaches()
            events.fire(DisplayNodePageEvent(selectedNode))
        }
    }

    private fun handleClick(x: Float, y: Float, button: Int) {
        when (button) {
            MouseButton.LEFT.code -> handleLeftClick(x, y)
            MouseButton.RIGHT.code -> handleRightClick(x, y)
        }
    }

    private fun handleRightClick(x: Float, y: Float) {
        if (mode == Mode.NODE) {
            val mapCoords = screenToMapSpace(Vector2(x, y) - Vector2(nodeSize / 2, nodeSize / 2))
            placeNode(mapCoords)
        } else {
            val decorationProto = decorationPrototypes[currentDecoProtoIndex]
            var mapDecoration = mapBuilder.decorations.find {
                it.drawInBackground == decorationProto.drawInBackground &&
                it.baseWidth == decorationProto.baseWidth &&
                it.baseHeight == decorationProto.baseHeight &&
                it.drawableHandle == decorationProto.drawableHandle
            }
            if (mapDecoration == null) {
                val decoration = decorationProto.generateDecorationBuilder()
                mapBuilder.decorations.add(decoration)
                mapDecoration = decoration
            }
            val reasonableScale = if (mapDecoration.instances.isNotEmpty()) {
                mapDecoration.instances.map { it.scale }.average().toFloat()
            } else {
                decorationProto.recommendScale
            }
            val mapCoords = screenToMapSpace(Vector2(x, y) - Vector2(nodeSize / 2, nodeSize / 2))
            mapDecoration.instances.add(MapDecorationBuilderInstance(mapCoords, reasonableScale))
        }
    }

    private fun handleLeftClick(x: Float, y: Float) {
        if (mode == Mode.NODE) {
            val clickedNode = findNodeAtPosition(x, y)
            when (tool) {
                Tool.CONNECT -> {
                    val selectedNode = selectedNode
                    if (clickedNode != null && selectedNode != null && clickedNode != selectedNode) {
                        if (selectedNode.isLinkedTo(clickedNode)) {
                            disconnect(selectedNode, clickedNode)
                        } else {
                            connect(selectedNode, clickedNode)
                        }
                    }
                    tool = null
                    return
                }
                Tool.MOVE -> {
                    tool = null
                    return
                }
                else -> {
                    selectedNode = clickedNode
                }
            }
        } else {
            tool = null
            selectedDecoration = findDecorationAtPosition(x, y)
        }
    }

    private fun connect(node1: MapNodeBuilder, node2: MapNodeBuilder) {
        if (node2 in node1.edgesTo) return
        node1.connect(node2)
        require(node1.index != node2.index)
        val edge = if (node1.index > node2.index) node1 to node2 else node2 to node1
        edges.add(edge)
    }

    private fun disconnect(node1: MapNodeBuilder, node2: MapNodeBuilder) {
        if (node2 !in node1.edgesTo) return
        require(node1.index != node2.index)
        node1.edgesTo.remove(node2)
        node2.edgesTo.remove(node1)
        val edge = if (node1.index > node2.index) node1 to node2 else node2 to node1
        edges.removeIf { it == edge }
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

    private fun findDecorationAtPosition(x: Float, y: Float): Pair<MapDecorationBuilder, MapDecorationBuilderInstance>? {
        mapBuilder.decorations.forEach { decoration ->
            val baseWidth = decoration.baseWidth
            val baseHeight = decoration.baseHeight
            decoration.instances.forEach instanceTest@{ instance ->
                val width = baseWidth * instance.scale * mapScale
                val height = baseHeight * instance.scale * mapScale
                val (iX, iY) = instance.position * mapScale + mapOffset
                if (x < iX || x > iX + width) return@instanceTest
                if (y < iY || y > iY + height) return@instanceTest
                return decoration to instance
            }
        }
        return null
    }

    private fun delete() {
        if (mode == Mode.NODE) {
            selectedNode?.let {
                selectedNode = null
                deleteNode(it)
            }
        } else {
            val (deco, instance) = selectedDecoration ?: return
            deco.instances.remove(instance)
            selectedDecoration = null
        }
    }

    private fun deleteNode(toDelete: MapNodeBuilder) {
        if (toDelete == mapBuilder.startNode || toDelete == mapBuilder.endNode) {
            FortyFive.soundPlayer.situation("not_allowed", screen)
            return
        }
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

    private fun updateScaleTool() {
        if (tool != Tool.SCALE) return
        val (_, instance) = selectedDecoration ?: return
        val startPos = scaleOriginalMousePos
        val mousePos = lastMousePos
        val dist = mousePos.x - startPos.x
        instance.scale = (originalScale + (dist * 0.0002f)).coerceAtLeast(0f)
    }

    private fun updateMoveTool() {
        if (tool != Tool.MOVE) return
        val selectedNode = selectedNode
        val selectedDecoration = selectedDecoration
        if (selectedNode != null) {
            val pos = screenToMapSpace(lastMousePos - Vector2(nodeSize / 2, nodeSize / 2))
            selectedNode.x = pos.x
            selectedNode.y = pos.y
        } else if (selectedDecoration != null) {
            val (deco, instance) = selectedDecoration
            val width = deco.baseWidth * instance.scale * mapScale
            val height = deco.baseHeight * instance.scale * mapScale
            val pos = screenToMapSpace(lastMousePos - Vector2(width / 2, height / 2))
            instance.position = pos
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()
        updateMoveTool()
        updateScaleTool()

        batch ?: return
        val viewport = screen.stage.viewport
        val scissor = Rectangle(
            0f, viewport.bottomGutterHeight.toFloat(),
            (Gdx.graphics.width / viewport.worldWidth) * width,
            ((Gdx.graphics.height - viewport.topGutterHeight - viewport.bottomGutterHeight) / viewport.worldHeight) * height
        )
        if (!ScissorStack.pushScissors(scissor)) return

        drawBackground(batch)
        drawBackgroundDecorations(batch)
        drawEdges(batch)
        drawNodes(batch)
        drawForegroundDecorations(batch)
        drawAnimatedDecorations(batch)

        drawConnectingEdge(batch)

        batch.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        viewport.apply()
        shapeRenderer.projectionMatrix = viewport.camera.combined

        drawStartEndNodeIndicators()
        drawSelectedNodeIndicator()
        drawSelectedDecorationIndicator()

        shapeRenderer.flush()
        shapeRenderer.end()
        batch.begin()
        ScissorStack.popScissors()
    }

    private fun drawStartEndNodeIndicators() {
        val start = scaledNodePos(mapBuilder.startNode) + mapOffset
        val end = scaledNodePos(mapBuilder.endNode) + mapOffset
        shapeRenderer.flush()
        Gdx.gl.glLineWidth(4f)
        shapeRenderer.color = Color.Green
        shapeRenderer.rect(start.x, start.y, nodeSize, nodeSize)
        shapeRenderer.flush()
        shapeRenderer.color = Color.Red
        shapeRenderer.rect(end.x, end.y, nodeSize, nodeSize)
    }

    private fun drawSelectedDecorationIndicator() {
        val (decoration, instance) = selectedDecoration ?: return
        val width = decoration.baseWidth * instance.scale * mapScale
        val height = decoration.baseHeight * instance.scale * mapScale
        val (x, y) = instance.position * mapScale + mapOffset
        shapeRenderer.flush()
        shapeRenderer.color = Color.Blue
        Gdx.gl.glLineWidth(10f)
        shapeRenderer.rect(x, y, width, height)
    }

    private fun drawSelectedNodeIndicator() {
        val node = selectedNode ?: return
        val coords = scaledNodePos(node) + mapOffset
        shapeRenderer.color = Color.Blue
        shapeRenderer.flush()
        Gdx.gl.glLineWidth(10f)
        shapeRenderer.circle(x + coords.x + nodeSize / 2, y + coords.y + nodeSize / 2, nodeSize / 2)
    }

    private fun drawForegroundDecorations(batch: Batch) {
        val (offX, offY) = mapOffset
        val decorations = mapBuilder.decorations.filter { !it.drawInBackground && !it.animated }
        decorations.forEach { decoration ->
            drawDecoration(decoration, batch, offX, offY)
        }
    }

    private fun drawBackgroundDecorations(batch: Batch) {
        val (offX, offY) = mapOffset
        val decorations = mapBuilder.decorations.filter { it.drawInBackground && !it.animated }
        decorations.forEach { decoration ->
            drawDecoration(decoration, batch, offX, offY)
        }
    }

    private fun drawAnimatedDecorations(batch: Batch) {
        val (offX, offY) = mapOffset
        val decorations = mapBuilder.decorations.filter { it.animated }
        decorations.forEach { decoration ->
            drawDecoration(decoration, batch, offX, offY)
        }
    }

    private fun drawDecoration(
        decoration: MapDecorationBuilder,
        batch: Batch,
        offX: Float,
        offY: Float,
    ) {
        val drawable = decoration.getDrawable(screen, this)
        val width = decoration.baseWidth
        val height = decoration.baseHeight
        decoration.instances.forEach { instance ->
            drawable.getOrNull()?.draw(
                batch,
                x + offX + instance.position.x * mapScale, y + offY + instance.position.y * mapScale,
                width * instance.scale * mapScale, height * instance.scale * mapScale
            )
        }
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

    data class MapDecorationPrototype(
        val drawableHandle: ResourceHandle,
        val baseWidth: Float,
        val baseHeight: Float,
        val drawInBackground: Boolean,
        val animated: Boolean = false,
        val recommendScale: Float = 1f
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MapDecorationPrototype
            if (baseWidth != other.baseWidth) return false
            if (baseHeight != other.baseHeight) return false
            if (drawInBackground != other.drawInBackground) return false
            if (drawableHandle != other.drawableHandle) return false
            return true
        }

        override fun hashCode(): Int {
            var result = baseWidth.hashCode()
            result = 31 * result + baseHeight.hashCode()
            result = 31 * result + drawInBackground.hashCode()
            result = 31 * result + drawableHandle.hashCode()
            return result
        }

        fun generateDecorationBuilder(): MapDecorationBuilder = MapDecorationBuilder(
            drawableHandle,
            baseWidth,
            baseHeight,
            drawInBackground,
            animated,
            mutableListOf()
        )

        companion object {
            val standardDecorations: Array<MapDecorationPrototype> = arrayOf(
                MapDecorationPrototype(
                    "map_decoration_wasteland_cactus_1",
                    2f, 4f,
                    false,
                    recommendScale = 3f
                ),
                MapDecorationPrototype(
                    "map_decoration_wasteland_cactus_2",
                    2f, 4f,
                    false,
                    recommendScale = 3f
                ),
                MapDecorationPrototype(
                    "map_decoration_wasteland_skull_1",
                    3f, 3f,
                    false,
                    recommendScale = 1.5f
                ),
                MapDecorationPrototype(
                    "map_decoration_wasteland_skull_2",
                    3f, 3f,
                    false,
                    recommendScale = 1.5f
                ),
                MapDecorationPrototype(
                    "map_decoration_magenta_mountains_mountain",
                    80f, 53.664597f,
                    false,
                    recommendScale = 1f
                ),
                MapDecorationPrototype(
                    "map_decoration_magenta_mountains_mountain_small_1",
                    60f, 51.933815f,
                    false,
                    recommendScale = 1f
                ),
                MapDecorationPrototype(
                    "map_decoration_magenta_mountains_mountain_small_2",
                    60f, 42.656574f,
                    false,
                    recommendScale = 1f
                ),
                MapDecorationPrototype(
                    "map_decoration_magenta_mountains_fog",
                    80f, 52.987015f,
                    false,
                    recommendScale = 1f
                ),
                MapDecorationPrototype(
                    "map_decoration_magenta_mountains_tree",
                    20f, 20f,
                    false,
                    recommendScale = 0.7f
                ),
                MapDecorationPrototype(
                    "enemy_pyro",
                    30f, 39.1f,
                    false,
                    recommendScale = 1f
                ),
                MapDecorationPrototype(
                    "enemy_outlaw",
                    20f, 43.63f,
                    false,
                    recommendScale = 1f
                ),
                MapDecorationPrototype(
                    "sheep",
                    8f, 8f,
                    false,
                    animated = true,
                    recommendScale = 1f
                ),
                MapDecorationPrototype(
                    "tree",
                    5f, 10f,
                    false,
                    animated = true,
                    recommendScale = 1.5f
                ),
                MapDecorationPrototype(
                    "grass",
                    5f, 5f * (34f / 18f),
                    false,
                    animated = true,
                    recommendScale = 1.5f
                ),
            )
        }
    }

    enum class Tool(val displayName: String) {
        CONNECT("Connect"),
        MOVE("Move"),
        SCALE("scale")
    }

    enum class Mode(val displayName: String) {
        NODE("Node"),
        DECORATION("Decoration"),
    }

    enum class MapEditorBiome(val internalName: String, val displayName: String, val background: String) {
        WASTELAND("wasteland", "Wasteland","map_background_wasteland_tileable"),
        BEWITCHED_FOREST("bewitched_forest", "Bewitched Forest","map_background_bewitched_forest_tileable"),
        MAGENTA_MOUNTAINS("magenta_mountains", "Magenta Mountains","map_background_magenta_mountains_tileable",)
    }

    data class DisplayDecorationEvent(val display: MapDecorationPrototype?)
    data class DisplayNodePageEvent(val node: MapNodeBuilder?)
    data class ModeChangedEvent(val newMode: Mode)

    companion object {
        val nodeTextures: Array<String?> = arrayOf(
            null,
            "map_node_default",
            "map_node_heal",
            "map_node_exit",
            "map_node_fight",
            "map_node_shop",
            "map_node_get_card",
            "map_node_choose_card",
            "map_node_dialog"
        )
        val animatedDecoPreviewDrawables: Map<String, String> = mapOf(
            "sheep" to "map_decoration_bewitched_forest_sheep_1",
            "tree" to "map_decoration_bewitched_forest_tree1",
            "grass" to "map_decoration_grass",
        )
    }

}
