package com.fourinachamber.fortyfive.map.detailMap.generation

import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.map.detailMap.*
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.math.cos
import kotlin.math.sin

class RadialMapGenerator(val data: RadialMapGeneratorData) : BaseMapGenerator() {

    override fun generate(name: String): DetailMap {
        setup(name, data)
        val startNode = newNode(0f, 0f)

        val nodes = generateNodes(data.circles)
        generateNodeConnections(startNode, nodes)

        setupBounds(data.horizontalExtension, data.verticalExtension)

        val (animatedDecorations, decorations) = data
            .decorations
            .splitInTwo { it.animated }

        assignEvents(data.events, nodes)

        val genDecorations = decorations.map { generateDecoration(it) }
        val genAnimatedDecorations = animatedDecorations.map { generateDecoration(it) }

        startNode.build()

        return DetailMap(
            name = name,
            startNode = startNode.asNode!!,
            endNode = allNodes.last().asNode!!,
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

    private fun assignEvents(events: List<RadialMapGeneratorEventSpawner>, nodes: List<List<MapNodeBuilder>>) {
        nodes.forEachIndexed { index, circle ->
            val possibleEvents = events
                .filter { it.circle == null || it.circle == index }
                .filter { it.fixedAmount == null }
            if (possibleEvents.isEmpty()) return@forEachIndexed
            circle.forEach { node ->
                val chosen = possibleEvents
                    .map { it.weight to it }
                    .weightedRandom(random)
                node.event = chosen.eventCreator()
                node.nodeTexture = chosen.nodeTexture
            }
        }
        val allNodes = nodes.flatten()
        val fixedEvents = events.filter { it.fixedAmount != null }
        val usedNodes = mutableListOf<MapNodeBuilder>()
        fixedEvents.forEach { event ->
            val possibleNodes = if (event.circle == null) {
                allNodes
            } else {
                nodes.getOrNull(event.circle) ?: throw RuntimeException("no circle with index ${event.circle}")
            }
            var spawned = 0
            while (true) {
                if (spawned >= event.fixedAmount!!) break
                val candidates = possibleNodes.filter { it !in usedNodes }
                if (candidates.isEmpty()) {
                    FortyFiveLogger.warn(
                        logTag,
                        "Cant spawn event $event ${event.fixedAmount} times because no possible nodes are left"
                    )
                    break
                }
                val chosen = candidates.random(random)
                chosen.event = event.eventCreator()
                chosen.nodeTexture = event.nodeTexture
                usedNodes.add(chosen)
                spawned++
            }
        }
    }

    private fun generateNodes(circles: List<Circle>): List<List<MapNodeBuilder>> = circles.map { circle ->
        val amountNodes = circle.numNodes
        val anglePerNode = Math.PI * 2 / amountNodes
        val shift = (0f..(Math.PI.toFloat() * 2f)).random(random)
        val variance = (-circle.angleVariance)..(circle.angleVariance)
        val nodes = mutableListOf<MapNodeBuilder>()
        repeat(amountNodes) { i ->
            val angle = (anglePerNode * i + shift + variance.random(random)).toFloat()
            val node = nodeOnCircle(circle.radius, angle)
            nodes.add(node)
        }
        nodes
    }

    private fun generateNodeConnections(startNode: MapNodeBuilder, nodes: List<List<MapNodeBuilder>>) {
        val circles = mutableListOf(
            listOf(startNode)
        )
        circles.addAll(nodes)
        circles.reverse()
        circles.forEachIndexed { index, circle ->
            if (index + 1 >= circles.size) return@forEachIndexed
            val innerCircle = circles[index + 1]
            circle.forEach { node ->
                var bestMatch: MapNodeBuilder? = null
                var smallestDist = Float.MAX_VALUE
                innerCircle.forEach candidateSearch@{ candidateNode ->
                    val dist = (node.posAsVec() - candidateNode.posAsVec()).len()
                    if (dist > smallestDist) return@candidateSearch
                    bestMatch = candidateNode
                    smallestDist = dist
                }
                connectNodes(node, bestMatch!!)
            }
        }
    }

    private fun nodeOnCircle(radius: Float, angle: Float): MapNodeBuilder = newNode(
        x = sin(angle) * radius,
        y = cos(angle) * radius
    )

    data class Circle(
        val radius: Float,
        val numNodes: Int,
        val angleVariance: Float
    ) {
        companion object {

            fun fromOnj(onj: OnjObject) = Circle(
                radius = onj.get<Double>("radius").toFloat(),
                numNodes = onj.get<Long>("numNodes").toInt(),
                angleVariance = onj.get<Double>("angleVariance").toFloat(),
            )
        }
    }

    data class RadialMapGeneratorData(
        override val seed: Long,
        override val nodeProtectedArea: Float,
        val biome: String,
        val circles: List<Circle>,
        val horizontalExtension: Float,
        val verticalExtension: Float,
        val decorations: List<MapGeneratorDecoration>,
        val events: List<RadialMapGeneratorEventSpawner>,
    ) : BaseMapGeneratorData {

        companion object {

            fun fromOnj(onj: OnjObject) = RadialMapGeneratorData(
                seed = onj.get<Long>("seed"),
                nodeProtectedArea = onj.get<Double>("nodeProtectedArea").toFloat(),
                biome = onj.get<String>("biome"),
                horizontalExtension = onj.get<Double>("horizontalExtension").toFloat(),
                verticalExtension = onj.get<Double>("verticalExtension").toFloat(),
                circles = onj
                    .get<OnjArray>("circles")
                    .value
                    .map { Circle.fromOnj(it as OnjObject) },
                decorations = onj
                    .get<OnjArray>("decorations")
                    .value
                    .map { MapGeneratorDecoration.fromOnj(it as OnjObject) },
                events = onj
                    .get<OnjArray>("events")
                    .value
                    .map { RadialMapGeneratorEventSpawner.fromOnj(it as OnjObject) }
            )
        }
    }

    data class RadialMapGeneratorEventSpawner(
        val eventCreator: () -> MapEvent,
        val circle: Int?,
        val weight: Int,
        val nodeTexture: String,
        val fixedAmount: Int?,
    ) {
        companion object {

            fun fromOnj(onj: OnjObject) = RadialMapGeneratorEventSpawner(
                eventCreator = { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("event")) },
                nodeTexture = onj.get<String>("nodeTexture"),
                circle = onj.getOr<Long?>("circle", null)?.toInt(),
                weight = onj.get<Long>("weight").toInt(),
                fixedAmount = onj.getOr<Long?>("fixedAmount", null)?.toInt(),
            )
        }
    }

}
