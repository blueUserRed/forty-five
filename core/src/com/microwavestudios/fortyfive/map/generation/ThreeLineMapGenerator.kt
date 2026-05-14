package com.microwavestudios.fortyfive.map.generation

import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.map.*
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.utils.random
import com.microwavestudios.fortyfive.utils.splitInTwo
import com.microwavestudios.fortyfive.utils.toIntRange
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class ThreeLineMapGenerator(private val data: ThreeLineMapGeneratorData) : BaseMapGenerator() {

    override fun generate(name: String, run: Run, seed: Long): DetailMap {
        setup(name, data, run, seed)

        val firstNode = newNode(x = 0f, y = 0f)
        val lastNode = newNode(x = data.roadLength, y = 0f)

        val mainLine = Line(firstNode, lastNode, data.mainLineNodes, 0f)
        mainLine.generate()

        addAdditionalLine(mainLine, data.altLinesOffset)
        addAdditionalLine(mainLine, -data.altLinesOffset)

        rotateNodes()

        setupBounds(data.horizontalExtension, data.verticalExtension)

        generateMapEvents()
        generateEncounters()

        firstNode.build()

        val (animatedDecorations, decorations) = data
            .decorations
            .splitInTwo { it.animated }

        val genDecorations = decorations.map { generateDecoration(it) }
        val genAnimatedDecorations = animatedDecorations.map { generateDecoration(it) }

        return DetailMap(
            name = name,
            startNode = this.startNode!!.asNode!!,
            endNode = this.endNode!!.asNode!!,
            decorations = genDecorations,
            animatedDecorations = genAnimatedDecorations,
            isArea = false,
            biome = data.biome,
            scrollable = true,
            majorDifficulty = data.majorDifficulty,
            camPosOffset = Vector2(0f, 0f),
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
        override val lastNodeEvent: () -> MapEvent,
        override val randomStepsToLastNode: Int,
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
        val decorations: List<MapGeneratorDecoration>,
        override val rotation: Float,
        override val fillEvents: List<MapGeneratorFillEvent>,
        override val fixedEvents: List<MapGeneratorFixedEvent>
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
            "decorations" with decorations.map { it.asOnj() }
            includeBaseData()
        }

        companion object {

            fun fromOnj(onj: OnjObject): ThreeLineMapGeneratorData = ThreeLineMapGeneratorData(
                onj.get<Long>("majorDifficulty").toInt(),
                onj.get<String>("biome"),
                { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("firstNodeEvent")) },
                { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("lastNodeEvent")) },
                onj.get<Long>("randomStepsToLastNode").toInt(),
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
                onj
                    .get<OnjArray>("decorations")
                    .value
                    .map { MapGeneratorDecoration.fromOnj(it as OnjObject) },
                onj.get<Double>("rotation").toFloat(),
                onj.get<OnjArray>("fillEvents").value.map { MapGeneratorFillEvent.fromOnj(it as OnjObject) },
                onj.get<OnjArray>("fixedEvents").value.map { MapGeneratorFixedEvent.fromOnj(it as OnjObject) },
            )
        }
    }

}
