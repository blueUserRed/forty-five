package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.utils.random
import onj.builder.buildOnjObject

class NewMapGenerator {

    private val allNodes: MutableList<MapNodeBuilder> = mutableListOf()

    private lateinit var data: MapGeneratorData

    fun generate(name: String): DetailMap {

        data = MapGeneratorData(
            roadLength = 400f,
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
            )
        )

        val startNode = newNode(x = 0f, y = 0f)
        startNode.event = EnterMapMapEvent(data.startArea)
        startNode.imageName = data.startArea
        startNode.imagePos = MapNode.ImagePosition.LEFT
        val endNode = newNode(x = data.roadLength, y = 0f)
        endNode.event = EnterMapMapEvent(data.endArea)
        endNode.imageName = data.endArea
        endNode.imagePos = MapNode.ImagePosition.RIGHT

        val mainLine = Line(startNode, endNode, data.mainLineNodes, 0f)
        mainLine.generate()

        val addLine1 = addAdditionalLine(mainLine, data.altLinesOffset)
        val addLine2 = addAdditionalLine(mainLine, -data.altLinesOffset)

        assignEvents(mainLine)
        assignEvents(addLine1)
        assignEvents(addLine2)

        startNode.build()

        return DetailMap(
            name = name,
            startNode = startNode.asNode!!,
            endNode = endNode.asNode!!,
            decorations = mutableListOf(),
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

    private fun newNode(
        x: Float = 0f,
        y: Float = 0f,
    ): MapNodeBuilder = MapNodeBuilder(
        index = allNodes.size,
        x = x,
        y = y
    ).also { allNodes.add(it) }

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
        val roadLength: Float,
        val mainLineNodes: Int,
        val altLinesPadding: IntRange,
        val altLinesOffset: Float,
        val varianceX: Float,
        val varianceY: Float,
        val startArea: String,
        val endArea: String,
        val mainEvent: Pair<String, () -> MapEvent>,
        val additionalEvents: List<Triple<IntRange, String, () -> MapEvent>>
    )

}
