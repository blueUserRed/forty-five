package com.microwavestudios.fortyfive.map.generation

import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.main
import com.microwavestudios.fortyfive.map.*
import com.microwavestudios.fortyfive.utils.random
import com.microwavestudios.fortyfive.utils.splitInTwo
import com.microwavestudios.fortyfive.utils.toFloatRange
import com.microwavestudios.fortyfive.utils.toIntRange
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class ThreeLineMapGenerator(private val data: ThreeLineMapGeneratorData) : BaseMapGenerator() {

    override fun generate(name: String, seed: Long): DetailMap {
        setup(name, data, seed)

        val startNode = newNode(x = 0f, y = 0f)
        setupFirstNode(startNode)

        val endNode = newNode(x = data.roadLength, y = 0f)
        setupLastNode(endNode)

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
            scrollable = true,
            majorDifficulty = data.majorDifficulty,
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
        val events = MutableList(line.nodes.size) { mainEvent.nodeTexture to mainEvent.eventCreator }
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

    override fun asOnj(): OnjObject = buildOnjObject {
        name("ThreeLine")
        includeAll(data.asOnj())
    }

    data class ThreeLineMapGeneratorData(
        override val majorDifficulty: Int,
        val biome: String,
        override val firstNodeEvent: () -> MapEvent,
        override val firstNodeTexture: String,
        override val lastNodeEvent: () -> MapEvent,
        override val lastNodeTexture: String,
        val roadLength: Float,
        val mainLineNodes: Int,
        val altLinesPadding: IntRange,
        val altLinesOffset: Float,
        val varianceX: Float,
        val varianceY: Float,
        val horizontalExtension: Float,
        val verticalExtension: Float,
        override val nodeProtectedArea: Float,
        override val locationSignProtectedAreaWidth: Float,
        override val locationSignProtectedAreaHeight: Float,
        val mainEvent: ThreeLineMapGeneratorEventSpawner,
        val events: List<ThreeLineMapGeneratorEventSpawner>,
        val decorations: List<MapGeneratorDecoration>,
    ) : BaseMapGeneratorData {

        override fun asOnj(): OnjObject = buildOnjObject {
            "biome" with biome
            "roadLength" with roadLength
            "mainLineNodes" with mainLineNodes
            "altLinesPadding" with arrayOf(altLinesPadding.first, altLinesPadding.last)
            "altLinesOffset" with altLinesOffset
            "varianceX" with varianceX
            "varianceY" with varianceY
            "horizontalExtension" with horizontalExtension
            "verticalExtension" with verticalExtension
            "mainEvent" with buildOnjObject {
                "event" with mainEvent.eventCreator().asOnjObject()
                "nodeTexture" with mainEvent.nodeTexture
            }
            "events" with events.map { it.asOnj() }
            "decorations" with decorations.map { it.asOnj() }
            includeBaseData()
        }

        companion object {

            fun fromOnj(onj: OnjObject): ThreeLineMapGeneratorData = ThreeLineMapGeneratorData(
                onj.get<Long>("majorDifficulty").toInt(),
                onj.get<String>("biome"),
                { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("firstNodeEvent")) },
                onj.get<String>("firstNodeTexture"),
                { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("lastNodeEvent")) },
                onj.get<String>("lastNodeTexture"),
                onj.get<Double>("roadLength").toFloat(),
                onj.get<Long>("mainLineNodes").toInt(),
                onj.get<OnjArray>("altLinesPadding").toIntRange(),
                onj.get<Double>("altLinesOffset").toFloat(),
                onj.get<Double>("varianceX").toFloat(),
                onj.get<Double>("varianceY").toFloat(),
                onj.get<Double>("horizontalExtension").toFloat(),
                onj.get<Double>("verticalExtension").toFloat(),
                onj.get<Double>("nodeProtectedArea").toFloat(),
                onj.get<Double>("locationSignProtectedAreaWidth").toFloat(),
                onj.get<Double>("locationSignProtectedAreaHeight").toFloat(),
                ThreeLineMapGeneratorEventSpawner(
                    { MapEventFactory.getMapEvent(onj.access(".mainEvent.event")) },
                    (0..1),
                    onj.access<String>(".mainEvent.nodeTexture"),
                    0
                ),
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

        fun asOnj(): OnjObject = buildOnjObject {
            "event" with eventCreator().asOnjObject()
            "offset" with arrayOf(offset.first, offset.last)
            "nodeTexture" with nodeTexture
            "line" with line
        }

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
