package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.random.Random

class NewMapGenerator {

    private val allNodes: MutableList<MapNodeBuilder> = mutableListOf()

    private lateinit var data: MapGeneratorData
    private lateinit var random: Random
    private lateinit var name: String

    private lateinit var bounds: Rectangle

    private val nodeColliders: MutableList<Rectangle> = mutableListOf()
    private val decorationColliders: MutableList<Rectangle> = mutableListOf()
    private val lineColliders: MutableList<Line2D> = mutableListOf()

    fun generate(name: String, data: MapGeneratorData): DetailMap {
        this.data = data
        this.name = name
        random = Random(data.seed)
        nodeColliders.clear()
        decorationColliders.clear()

        val startNode = newNode(x = 0f, y = 0f)
        startNode.event = EnterMapMapEvent(data.startArea)
        startNode.imageName = data.startArea
        startNode.imagePos = MapNode.ImagePosition.LEFT
        startNode.nodeTexture = data.exitNodeTexture
        val startSignBounds = Rectangle(
            -data.locationSignProtectedAreaWidth,
            -data.locationSignProtectedAreaHeight / 2,
            data.locationSignProtectedAreaWidth,
            data.locationSignProtectedAreaHeight,
        )
        nodeColliders.add(startSignBounds)

        val endNode = newNode(x = data.roadLength, y = 0f)
        endNode.event = EnterMapMapEvent(data.endArea)
        endNode.imageName = data.endArea
        endNode.imagePos = MapNode.ImagePosition.RIGHT
        endNode.nodeTexture = data.exitNodeTexture
        val endSignBounds = Rectangle(
            data.roadLength,
            -data.locationSignProtectedAreaHeight / 2,
            data.locationSignProtectedAreaWidth,
            data.locationSignProtectedAreaHeight,
        )
        nodeColliders.add(endSignBounds)

        val mainLine = Line(startNode, endNode, data.mainLineNodes, 0f)
        mainLine.generate()

        val addLine1 = addAdditionalLine(mainLine, data.altLinesOffset)
        val addLine2 = addAdditionalLine(mainLine, -data.altLinesOffset)

        val minX = allNodes.minOf { it.x }
        val maxX = allNodes.maxOf { it.x }
        val minY = allNodes.minOf { it.y }
        val maxY = allNodes.maxOf { it.y }
        bounds = Rectangle(
            minX - data.horizontalExtension,
            minY - data.verticalExtension,
            maxX - minX + data.horizontalExtension * 2,
            maxY - minY + data.verticalExtension * 2
        )

        val sharedEvents = data.events.filter { it.line == -1 }
        assignEvents(mainLine, sharedEvents)
        assignEvents(addLine1, sharedEvents)
        assignEvents(addLine2, sharedEvents)
        val eventsMainLine = data.events.filter { it.line == 0 }
        assignEvents(mainLine, eventsMainLine)
        val eventsLine1 = data.events.filter { it.line == 1 }
        assignEvents(addLine1, eventsLine1)
        val eventsLine2 = data.events.filter { it.line == 2 }
        assignEvents(addLine2, eventsLine2)

        startNode.build()

        val (animatedDecorations, decorations) = data
            .decorations
            .splitInTwo { it.animated }

        val genDecorations = decorations.map { generateDecoration(it) }
        val genAnimatedDecorations = animatedDecorations.map { generateDecoration(it) }

        return DetailMap(
            name = name,
            startNode = startNode.asNode!!,
            endNode = endNode.asNode!!,
            decorations = genDecorations,
            animatedDecorations = genAnimatedDecorations,
            isArea = false,
            biome = data.biome,
            progress = 0f..10f,
            tutorialText = mutableListOf(),
            scrollable = true,
            camPosOffset = Vector2(0f, 0f)
        )
    }

    private fun addAdditionalLine(mainLine: Line, offsetY: Float): Line {
        val nodes = mainLine.nodes
        val startPadding = data.altLinesPadding.random()
        val endPadding = data.altLinesPadding.random()
        val startNode = nodes[startPadding]
        val endNode = nodes[nodes.size - endPadding - 1]
        val amountNodes = (nodes.size - endPadding) - startPadding - 2
        val line = Line(
            startNode,
            endNode,
            amountNodes,
            offsetY,
        )
        line.generate()
        return line
    }

    private fun assignEvents(line: Line, eventsToAssign: List<MapGeneratorEventSpawner>) {
        val mainEvent = data.mainEvent
        val events = MutableList(line.nodes.size) { mainEvent }
        eventsToAssign.forEach { (eventCreator, offset, nodeTexture) ->
            var cur = 0
            while (true) {
                cur += offset.random()
                if (cur >= events.size) break
                events[cur] = nodeTexture to eventCreator
            }
        }
        events.forEachIndexed { index, (texture, eventCreator) ->
            val node = line.nodes[index]
            node.event = eventCreator()
            node.nodeTexture = texture
        }
    }

    private fun generateDecoration(decoration: MapGeneratorDecoration): DetailMap.MapDecoration {
        val dist = decoration.distribution(bounds, random).iterator()
        val instances = mutableListOf<Pair<Vector2, Float>>()
        val targetAmount = (decoration.density * bounds.area()).toInt()

        fun checkCollision(collider: Rectangle, other: Rectangle): Boolean = if (decoration.onlyCheckCollisionsAtSpawnPoints) {
            other.contains(Vector2(collider.x, collider.y))
        } else {
            other.overlaps(collider)
        }

        fun checkCollision(collider: Rectangle, other: Line2D): Boolean = if (decoration.onlyCheckCollisionsAtSpawnPoints) {
            false
        } else {
            other.intersects(collider)
        }

        val maxIts = targetAmount * 3
        var iteration = 0
        var spawned = 0
        while (spawned <= targetAmount) {
            if (iteration > maxIts) {
                FortyFiveLogger.warn(logTag, "MaxIts reached when spawning decoration ${decoration.decoration} in map $name")
                break
            }
            iteration++
            if (!dist.hasNext()) break
            val pos = dist.next()
            val scale = decoration.scale.random()
            val shrinkWidth = decoration.baseWidth * scale * decoration.shrinkBoundsWidth
            val shrinkHeight = decoration.baseHeight * scale * decoration.shrinkBoundsHeight
            val thisCollision = Rectangle(
                pos.x + shrinkWidth / 2,
                pos.y + shrinkHeight / 2,
                decoration.baseWidth * scale - shrinkWidth,
                decoration.baseHeight * scale - shrinkHeight
            )
            if (decoration.checkNodeCollisions && nodeColliders.any { checkCollision(thisCollision, it) }) continue
            if (decoration.checkDecorationCollisions && decorationColliders.any { checkCollision(thisCollision, it) }) continue
            if (decoration.checkLineCollisions && lineColliders.any { checkCollision(thisCollision, it) })
            if (decoration.generateDecorationCollisions) decorationColliders.add(thisCollision)
            instances.add(pos to scale)
            spawned++
        }
        if (decoration.sortByY) instances.sortByDescending { it.first.y }
        return DetailMap.MapDecoration(
            decoration.decoration,
            decoration.baseWidth,
            decoration.baseHeight,
            false,
            instances
        )
    }

    private fun newNode(
        x: Float = 0f,
        y: Float = 0f,
    ): MapNodeBuilder = MapNodeBuilder(
        index = allNodes.size,
        x = x,
        y = y
    ).also {
        allNodes.add(it)
        val width = data.nodeProtectedArea
        val halfWidth = width / 2
        nodeColliders.add(Rectangle(x - halfWidth, y - halfWidth, width, width))
    }

    private fun connectNodes(node1: MapNodeBuilder, node2: MapNodeBuilder) {
        node1.connect(node2)
        lineColliders.add(Line2D(Vector2(node1.x, node1.y), Vector2(node2.x, node2.y)))
    }

    private inner class Line(
        val startNode: MapNodeBuilder,
        val endNode: MapNodeBuilder,
        val numNodes: Int,
        val offsetY: Float,
    ) {

        lateinit var nodes: List<MapNodeBuilder>
            private set

        fun generate() {
            val distancePerNode = (endNode.x - startNode.x) / (numNodes + 1)
            val avgY = (endNode.y + startNode.y) / 2f
            var curX = startNode.x + distancePerNode
            var lastNode: MapNodeBuilder? = null
            val varianceX = data.varianceX
            val varianceY = data.varianceY
            val nodes = MutableList(numNodes) {
                val node = newNode(
                    x = curX + (-varianceX..varianceX).random(),
                    y = avgY + (-varianceY..varianceY).random() + offsetY
                )
                curX += distancePerNode
                lastNode?.let {
                    connectNodes(it, node)
                }
                lastNode = node
                node
            }
            connectNodes(nodes.first(), startNode)
            connectNodes(nodes.last(), endNode)
            this.nodes = nodes
        }

    }

    data class MapGeneratorData(
        val seed: Long,
        val biome: String,
        val progress: IntRange,
        val exitNodeTexture: String,
        val roadLength: Float,
        val mainLineNodes: Int,
        val altLinesPadding: IntRange,
        val altLinesOffset: Float,
        val varianceX: Float,
        val varianceY: Float,
        val startArea: String,
        val endArea: String,
        val horizontalExtension: Float,
        val verticalExtension: Float,
        val nodeProtectedArea: Float,
        val locationSignProtectedAreaWidth: Float,
        val locationSignProtectedAreaHeight: Float,
        val mainEvent: Pair<String, () -> MapEvent>,
        val events: List<MapGeneratorEventSpawner>,
        val decorations: List<MapGeneratorDecoration>,
    ) {

        companion object {

            fun fromOnj(onj: OnjObject): MapGeneratorData = MapGeneratorData(
                onj.get<Long>("seed"),
                onj.get<String>("biome"),
                onj.get<OnjArray>("progress").toIntRange(),
                onj.get<String>("exitNodeTexture"),
                onj.get<Double>("roadLength").toFloat(),
                onj.get<Long>("mainLineNodes").toInt(),
                onj.get<OnjArray>("altLinesPadding").toIntRange(),
                onj.get<Double>("altLinesOffset").toFloat(),
                onj.get<Double>("varianceX").toFloat(),
                onj.get<Double>("varianceY").toFloat(),
                onj.get<String>("startArea"),
                onj.get<String>("endArea"),
                onj.get<Double>("horizontalExtension").toFloat(),
                onj.get<Double>("verticalExtension").toFloat(),
                onj.get<Double>("nodeProtectedArea").toFloat(),
                onj.get<Double>("locationSignProtectedAreaWidth").toFloat(),
                onj.get<Double>("locationSignProtectedAreaHeight").toFloat(),
                onj.access<String>(".mainEvent.nodeTexture") to { MapEventFactory.getMapEvent(onj.access(".mainEvent.event")) },
                onj
                    .get<OnjArray>("events")
                    .value
                    .map { MapGeneratorEventSpawner.fromOnj(it as OnjObject) },
                onj
                    .get<OnjArray>("decorations")
                    .value
                    .map { MapGeneratorDecoration.fromOnj(it as OnjObject) }
            )
        }
    }

    data class MapGeneratorEventSpawner(
        val eventCreator: () -> MapEvent,
        val offset: IntRange,
        val nodeTexture: String,
        val line: Int,
    ) {
        companion object {
            fun fromOnj(onj: OnjObject): MapGeneratorEventSpawner = MapGeneratorEventSpawner(
                eventCreator = { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("event")) },
                offset = onj.get<OnjArray>("offset").toIntRange(),
                nodeTexture = onj.get<String>("nodeTexture"),
                line = onj.get<Long>("line").toInt()
            )
        }
    }

    data class MapGeneratorDecoration(
        val distribution: (bounds: Rectangle, random: Random) -> Sequence<Vector2>,
        val decoration: String,
        val baseWidth: Float,
        val baseHeight: Float,
        val density: Float,
        val checkNodeCollisions: Boolean,
        val checkLineCollisions: Boolean,
        val checkDecorationCollisions: Boolean,
        val generateDecorationCollisions: Boolean,
        val onlyCheckCollisionsAtSpawnPoints: Boolean,
        val scale: ClosedFloatingPointRange<Float>,
        val shrinkBoundsWidth: Float,
        val shrinkBoundsHeight: Float,
        val sortByY: Boolean,
        val animated: Boolean,
    ) {

        companion object {

            fun fromOnj(onj: OnjObject): MapGeneratorDecoration = MapGeneratorDecoration(
                DecorationDistribution.fromOnj(onj.get<OnjNamedObject>("distribution")),
                onj.get<String>("decoration"),
                onj.get<Double>("baseWidth").toFloat(),
                onj.get<Double>("baseHeight").toFloat(),
                onj.get<Double>("density").toFloat(),
                onj.get<Boolean>("checkNodeCollisions"),
                onj.get<Boolean>("checkLineCollisions"),
                onj.get<Boolean>("checkDecorationCollisions"),
                onj.get<Boolean>("generateDecorationCollisions"),
                onj.get<Boolean>("onlyCheckCollisionsAtSpawnPoints"),
                onj.get<OnjArray>("scale").toFloatRange(),
                onj.getOr<Double>("shrinkBoundsWidth", 0.0).toFloat(),
                onj.getOr<Double>("shrinkBoundsHeight", 0.0).toFloat(),
                onj.get<Boolean>("sortByY"),
                onj.get<Boolean>("animated"),
            )
        }
    }

    object DecorationDistribution {

        fun random(bounds: Rectangle, random: Random): Sequence<Vector2> = repeatingSequenceOf { Vector2(
            (bounds.x..(bounds.x + bounds.width)).random(random),
            (bounds.y..(bounds.y + bounds.height)).random(random),
        ) }

        fun fadeX(
            bounds: Rectangle,
            random: Random,
            start: Float,
            end: Float,
            interpolation: Interpolation
        ): Sequence<Vector2> = repeatingSequenceOf {
            val x = interpolation.apply(start, end, random.nextFloat())
            val y = (bounds.y..(bounds.y + bounds.height)).random(random)
            Vector2(x, y)
        }

        fun fromOnj(
            onj: OnjNamedObject
        ): (bounds: Rectangle, random: Random) -> Sequence<Vector2> = when (val name = onj.name) {
            "Random" -> ::random
            "FadeX" -> { bounds, random -> fadeX(
                bounds, random,
                onj.get<Double>("start").toFloat(),
                onj.get<Double>("end").toFloat(),
                onj.get<Interpolation>("interpolation")
            ) }
            else -> throw RuntimeException("unknown decoration distribution function $name")
        }
    }

    companion object {
        const val logTag = "NewMapGenerator"
    }

}