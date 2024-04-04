package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.random
import com.fourinachamber.fortyfive.utils.repeatingSequenceOf
import onj.builder.buildOnjObject
import kotlin.random.Random

class NewMapGenerator {

    private val allNodes: MutableList<MapNodeBuilder> = mutableListOf()

    private lateinit var data: MapGeneratorData
    private lateinit var random: Random
    private lateinit var name: String

    private lateinit var bounds: Rectangle

    private val nodeColliders: MutableList<Rectangle> = mutableListOf()
    private val decorationColliders: MutableList<Rectangle> = mutableListOf()

    fun generate(name: String): DetailMap {

        data = MapGeneratorData(
            seed = 7892342308974093L,
            roadLength = 400f,
            exitNodeTexture = "map_node_exit",
            mainLineNodes = 10,
            altLinesPadding = 0..3,
            altLinesOffset = 30f,
            varianceX = 10f,
            varianceY = 10f,
            startArea = "aqua_balle",
            endArea = "tabu_letter_outpost",
            mainEvent = "map_node_fight" to { EncounterMapEvent(buildOnjObject {
                "encounterIndex" with 0
                "currentlyBlocks" with true
                "canBeStarted" with true
                "isCompleted" with false
                "distanceToEnd" with 0
            }) },
            additionalEvents = listOf(
                Triple(2..3, "map_node_heal") { HealOrMaxHPMapEvent(buildOnjObject {
                    "encounterIndex" with 0
                    "currentlyBlocks" with true
                    "canBeStarted" with true
                    "isCompleted" with false
                    "distanceToEnd" with 0
                    "seed" with 0
                    "healRange" with arrayOf(1, 2)
                    "maxHPRange" with arrayOf(1, 2)
                }) }
            ),
            nodeProtectedArea = 10f,
            decorations = listOf(
                MapGeneratorDecoration(
                    DecorationDistribution::random,
                    "map_decoration_wasteland_cactus_1",
                    baseWidth = 2.0f,
                    baseHeight = 4.0f,
                    scale = 2.75f..3.25f,
                    density = 0.005f,
                    generateDecorationCollisions = true,
                    checkDecorationCollisions = true,
                    onlyCheckCollisionsAtSpawnPoints = true,
                    checkNodeCollisions = true,
                    sortByY = true
                )
            )
        )

        this.name = name
        random = Random(data.seed)
        nodeColliders.clear()
        decorationColliders.clear()

        val startNode = newNode(x = 0f, y = 0f)
        startNode.event = EnterMapMapEvent(data.startArea)
        startNode.imageName = data.startArea
        startNode.imagePos = MapNode.ImagePosition.LEFT
        startNode.nodeTexture = data.exitNodeTexture
        val endNode = newNode(x = data.roadLength, y = 0f)
        endNode.event = EnterMapMapEvent(data.endArea)
        endNode.imageName = data.endArea
        endNode.imagePos = MapNode.ImagePosition.RIGHT
        endNode.nodeTexture = data.exitNodeTexture

        val mainLine = Line(startNode, endNode, data.mainLineNodes, 0f)
        mainLine.generate()

        val addLine1 = addAdditionalLine(mainLine, data.altLinesOffset)
        val addLine2 = addAdditionalLine(mainLine, -data.altLinesOffset)

        val minX = allNodes.minOf { it.x }
        val maxX = allNodes.maxOf { it.x }
        val minY = allNodes.minOf { it.y }
        val maxY = allNodes.maxOf { it.y }
        bounds = Rectangle(minX, minY, maxX - minX, maxY - minY)

        assignEvents(mainLine)
        assignEvents(addLine1)
        assignEvents(addLine2)

        startNode.build()

        val decorations = data.decorations.map { generateDecoration(it) }

        return DetailMap(
            name = name,
            startNode = startNode.asNode!!,
            endNode = endNode.asNode!!,
            decorations = decorations,
            animatedDecorations = mutableListOf(),
            isArea = false,
            biome = "wasteland",
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
        val amountNodes = (nodes.size - endPadding) - startPadding
        val line = Line(
            startNode,
            endNode,
            amountNodes,
            offsetY,
        )
        line.generate()
        return line
    }

    private fun assignEvents(line: Line) {
        val mainEvent = data.mainEvent
        val events = MutableList(line.nodes.size) { mainEvent }
        data.additionalEvents.forEach { (nextRange, nodeTexture, event) ->
            var cur = 0
            while (true) {
                cur += nextRange.random()
                if (cur >= events.size) break
                events[cur] = nodeTexture to event
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
            val thisCollision = Rectangle(pos.x, pos.y, decoration.baseWidth * scale, decoration.baseHeight * scale)
            if (decoration.checkNodeCollisions && nodeColliders.any { checkCollision(thisCollision, it) }) continue
            if (decoration.checkDecorationCollisions && decorationColliders.any { checkCollision(thisCollision, it) }) continue
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
                    it.edgesTo.add(node)
                    node.edgesTo.add(it)
                }
                lastNode = node
                node
            }
            nodes.first().edgesTo.add(startNode)
            startNode.edgesTo.add(nodes.first())
            nodes.last().edgesTo.add(endNode)
            endNode.edgesTo.add(nodes.last())
            this.nodes = nodes
        }

    }

    data class MapGeneratorData(
        val seed: Long,
        val exitNodeTexture: String,
        val roadLength: Float,
        val mainLineNodes: Int,
        val altLinesPadding: IntRange,
        val altLinesOffset: Float,
        val varianceX: Float,
        val varianceY: Float,
        val startArea: String,
        val endArea: String,
        val nodeProtectedArea: Float,
        val mainEvent: Pair<String, () -> MapEvent>,
        val additionalEvents: List<Triple<IntRange, String, () -> MapEvent>>,
        val decorations: List<MapGeneratorDecoration>,
    )

    data class MapGeneratorDecoration(
        val distribution: (bounds: Rectangle, random: Random) -> Sequence<Vector2>,
        val decoration: String,
        val baseWidth: Float,
        val baseHeight: Float,
        val density: Float,
        val checkNodeCollisions: Boolean,
        val checkDecorationCollisions: Boolean,
        val generateDecorationCollisions: Boolean,
        val onlyCheckCollisionsAtSpawnPoints: Boolean,
        val scale: ClosedFloatingPointRange<Float>,
        val sortByY: Boolean
    )

    object DecorationDistribution {

        fun random(bounds: Rectangle, random: Random): Sequence<Vector2> = repeatingSequenceOf { Vector2(
            (bounds.x..(bounds.x + bounds.width)).random(random),
            (bounds.y..(bounds.y + bounds.height)).random(random),
        ) }
    }

    companion object {
        const val logTag = "NewMapGenerator"
    }

}
