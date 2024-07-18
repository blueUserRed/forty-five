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
        setupExitNode(startNode, data.startArea)
        doNodeImage(startNode, MapNode.ImagePosition.LEFT)

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
            progress = data.progress,
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
        val fixedEvents = events.filter { it.fixedAmount != null }.toMutableList()
        fixedEvents.add(RadialMapGeneratorEventSpawner(
            { EnterMapMapEvent(data.endArea) },
            data.exitNodeCircle,
            0,
            data.exitNodeTexture,
            1
        ))
        val usedNodes = mutableListOf<MapNodeBuilder>()
        fixedEvents.forEach { eventSpawner ->
            val possibleNodes = if (eventSpawner.circle == null) {
                allNodes
            } else {
                nodes.getOrNull(eventSpawner.circle) ?: throw RuntimeException("no circle with index ${eventSpawner.circle}")
            }
            var spawned = 0
            while (true) {
                if (spawned >= eventSpawner.fixedAmount!!) break
                val candidates = possibleNodes.filter { it !in usedNodes }
                if (candidates.isEmpty()) {
                    FortyFiveLogger.warn(
                        logTag,
                        "Cant spawn event $eventSpawner ${eventSpawner.fixedAmount} times because no possible nodes are left"
                    )
                    break
                }
                val chosen = candidates.random(random)
                val event = eventSpawner.eventCreator()
                if (event is EnterMapMapEvent) {
                    setupExitNode(chosen, event.targetMap)
                    doNodeImage(chosen, findIdealNodeImagePosition(chosen))
                } else {
                    chosen.event = event
                    chosen.nodeTexture = eventSpawner.nodeTexture
                }
                usedNodes.add(chosen)
                spawned++
            }
        }
    }

    private fun findIdealNodeImagePosition(node: MapNodeBuilder): MapNode.ImagePosition {
        val width = bounds.width
        val x = bounds.x
        if (node.x in x..(x + width * 0.3f)) return MapNode.ImagePosition.LEFT
        if (node.x in (x + width * 0.6f)..(x + width)) return MapNode.ImagePosition.RIGHT
        if (node.y > 0f) return MapNode.ImagePosition.UP
        return MapNode.ImagePosition.DOWN
    }

    private fun generateNodes(circles: List<Circle>): List<List<MapNodeBuilder>> = circles.mapIndexed { index, circle ->
        val amountNodes = circle.numNodes
        val anglePerNode = Math.PI * 2 / amountNodes
        val shift = if (index == 0) {
            1.4f // magic numbers are the best numbers
        } else {
            (0f..(Math.PI.toFloat() * 2f)).random(random)
        }
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
        override val locationSignProtectedAreaWidth: Float,
        override val locationSignProtectedAreaHeight: Float,
        override val startArea: String,
        val endArea: String,
        override val exitNodeTexture: String,
        override val progress: ClosedFloatingPointRange<Float>,
        val exitNodeCircle: Int,
    ) : BaseMapGeneratorData {

        companion object {

            fun fromOnj(onj: OnjObject) = RadialMapGeneratorData(
                seed = onj.get<Long>("seed"),
                nodeProtectedArea = onj.get<Double>("nodeProtectedArea").toFloat(),
                biome = onj.get<String>("biome"),
                horizontalExtension = onj.get<Double>("horizontalExtension").toFloat(),
                verticalExtension = onj.get<Double>("verticalExtension").toFloat(),
                locationSignProtectedAreaWidth = onj.get<Double>("locationSignProtectedAreaWidth").toFloat(),
                locationSignProtectedAreaHeight = onj.get<Double>("locationSignProtectedAreaHeight").toFloat(),
                startArea = onj.get<String>("startArea"),
                endArea = onj.get<String>("endArea"),
                exitNodeCircle = onj.get<Long>("exitNodeCircle").toInt(),
                exitNodeTexture = onj.get<String>("exitNodeTexture"),
                progress = onj.get<OnjArray>("progress").toFloatRange(),
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
