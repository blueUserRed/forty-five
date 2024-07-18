package com.fourinachamber.fortyfive.map.detailMap.generation

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.map.detailMap.*
import com.fourinachamber.fortyfive.utils.random
import com.fourinachamber.fortyfive.utils.splitInTwo
import com.fourinachamber.fortyfive.utils.toFloatRange
import com.fourinachamber.fortyfive.utils.toIntRange
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class ThreeLineMapGenerator(private val data: ThreeLineMapGeneratorData) : BaseMapGenerator() {

    override fun generate(name: String): DetailMap {
        setup(name, data)

        val startNode = newNode(x = 0f, y = 0f)
        setupExitNode(startNode, data.startArea)
        doNodeImage(startNode, MapNode.ImagePosition.LEFT)

        val endNode = newNode(x = data.roadLength, y = 0f)
        setupExitNode(endNode, data.endArea)
        doNodeImage(endNode, MapNode.ImagePosition.RIGHT)

        val mainLine = Line(startNode, endNode, data.mainLineNodes, 0f)
        mainLine.generate()

        val addLine1 = addAdditionalLine(mainLine, data.altLinesOffset)
        val addLine2 = addAdditionalLine(mainLine, -data.altLinesOffset)

        setupBounds(data.horizontalExtension, data.verticalExtension)

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
            progress = data.progress,
            tutorialText = mutableListOf(),
            scrollable = true,
            camPosOffset = Vector2(0f, 0f)
        )
    }

    private fun addAdditionalLine(mainLine: Line, offsetY: Float): Line {
        val nodes = mainLine.nodes
        val startPadding = data.altLinesPadding.random(random)
        val endPadding = data.altLinesPadding.random(random)
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

    private fun assignEvents(line: Line, eventsToAssign: List<ThreeLineMapGeneratorEventSpawner>) {
        val mainEvent = data.mainEvent
        val events = MutableList(line.nodes.size) { mainEvent }
        eventsToAssign.forEach { (eventCreator, offset, nodeTexture) ->
            var cur = 0
            while (true) {
                cur += offset.random(random)
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
                    x = curX + (-varianceX..varianceX).random(random),
                    y = avgY + (-varianceY..varianceY).random(random) + offsetY
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


    data class ThreeLineMapGeneratorData(
        override val seed: Long,
        val biome: String,
        override val progress: ClosedFloatingPointRange<Float>,
        override val exitNodeTexture: String,
        val roadLength: Float,
        val mainLineNodes: Int,
        val altLinesPadding: IntRange,
        val altLinesOffset: Float,
        val varianceX: Float,
        val varianceY: Float,
        override val startArea: String,
        val endArea: String,
        val horizontalExtension: Float,
        val verticalExtension: Float,
        override val nodeProtectedArea: Float,
        override val locationSignProtectedAreaWidth: Float,
        override val locationSignProtectedAreaHeight: Float,
        val mainEvent: Pair<String, () -> MapEvent>,
        val events: List<ThreeLineMapGeneratorEventSpawner>,
        val decorations: List<MapGeneratorDecoration>,
    ) : BaseMapGeneratorData {

        companion object {

            fun fromOnj(onj: OnjObject): ThreeLineMapGeneratorData = ThreeLineMapGeneratorData(
                onj.get<Long>("seed"),
                onj.get<String>("biome"),
                onj.get<OnjArray>("progress").toFloatRange(),
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
                    .map { ThreeLineMapGeneratorEventSpawner.fromOnj(it as OnjObject) },
                onj
                    .get<OnjArray>("decorations")
                    .value
                    .map { MapGeneratorDecoration.fromOnj(it as OnjObject) }
            )
        }
    }

    data class ThreeLineMapGeneratorEventSpawner(
        val eventCreator: () -> MapEvent,
        val offset: IntRange,
        val nodeTexture: String,
        val line: Int,
    ) {
        companion object {
            fun fromOnj(onj: OnjObject): ThreeLineMapGeneratorEventSpawner = ThreeLineMapGeneratorEventSpawner(
                eventCreator = { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("event")) },
                offset = onj.get<OnjArray>("offset").toIntRange(),
                nodeTexture = onj.get<String>("nodeTexture"),
                line = onj.get<Long>("line").toInt()
            )
        }
    }

}
