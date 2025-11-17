package com.microwavestudios.fortyfive.map.generation

import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.map.MapEvent
import com.microwavestudios.fortyfive.map.MapEventFactory
import com.microwavestudios.fortyfive.map.MapNodeBuilder
import com.microwavestudios.fortyfive.utils.minus
import com.microwavestudios.fortyfive.utils.random
import com.microwavestudios.fortyfive.utils.splitInTwo
import com.microwavestudios.fortyfive.utils.weightedRandom
import com.microwavestudios.fortyfive.utils.zip
import com.microwavestudios.fortyfive.utils.zipToFirst
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.collections.map

class PointCloudMapGenerator(val data: PointCloudMapGeneratorData) : BaseMapGenerator() {

    override fun generate(name: String, seed: Long): DetailMap {
        setup(name, data, seed)

        val startNode = newNode(x = -40f, y = 0f)
        setupFirstNode(startNode)
        val endNode = newNode(x = data.roadLength + 40f, y = 0f)
        setupLastNode(endNode)

        val nodes = generateNodes()
        doInitialNodeConnections(nodes, startNode, endNode)
        val groups = collectGroups()
        connectGroups(groups)

        calculateDistances(startNode)
        setupBounds(data.horizontalExtension, data.verticalExtension)

        assignEvents(nodes)
        generateEncounters(startNode)

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
            camPosOffset = Vector2(0f, 0f),
        )
    }

    private fun assignEvents(nodes: List<MapNodeBuilder>) {
        nodes.forEach { node ->
            val event = data.eventSpawner.zipToFirst { it.weight }.weightedRandom(random)
            node.nodeTexture = event.texture
            node.event = event.eventCreator()
        }
    }

    private fun connectGroups(groups: MutableList<MutableList<MapNodeBuilder>>) {
        while (groups.size > 1) {
            val toConnect = groups.first()
            val (closestGroup, nodesToConnect) = groups
                .takeLast(groups.size - 1)
                .zip { closestTwoNodes(toConnect, it) }
                .minBy { (_, nodes) -> (nodes.first.posAsVec() - nodes.second.posAsVec()).len() }
            connectNodes(nodesToConnect.first, nodesToConnect.second)
            groups.remove(toConnect)
            groups.remove(closestGroup)
            val newGroup = mutableListOf<MapNodeBuilder>()
            newGroup.addAll(toConnect)
            newGroup.addAll(closestGroup)
            groups.add(newGroup)
        }
    }

    private fun closestTwoNodes(
        group1: List<MapNodeBuilder>,
        group2: List<MapNodeBuilder>
    ): Pair<MapNodeBuilder, MapNodeBuilder> {
        var minDist = 1e10f
        var closestNode: MapNodeBuilder? = null
        var connectedNode: MapNodeBuilder? = null
        group1.forEach { node ->
            val closest = nearestConnectableNode(node, group2)!!
            val dist = (node.posAsVec() - closest.posAsVec()).len()
            if (dist > minDist) return@forEach
            minDist = dist
            closestNode = node
            connectedNode = closest
        }
        return closestNode!! to connectedNode!!
    }

    private fun collectGroups(): MutableList<MutableList<MapNodeBuilder>> {
        val groups = mutableListOf<MutableList<MapNodeBuilder>>()
        allNodes.forEach { node ->
            val groupLinks = groups.filter { group -> node.edgesTo.any { it in group } }
            when (groupLinks.size) {
                0 -> groups.add(mutableListOf(node)) // node is not linked to any group; create a new one
                1 -> groupLinks.first().add(node) // node is linked to a group
                else -> {
                    // node connects two or more different groups; collect the groups into one
                    groups.removeAll(groupLinks)
                    val newGroup = mutableListOf<MapNodeBuilder>()
                    groupLinks.flatten().forEach { node -> newGroup.add(node) }
                    newGroup.add(node)
                    groups.add(newGroup)
                }
            }
        }
        return groups
    }

    private fun doInitialNodeConnections(
        nodes: List<MapNodeBuilder>,
        startNode: MapNodeBuilder,
        endNode: MapNodeBuilder
    ) {
        nodes.forEach { node ->
            val closest = nearestConnectableNode(node, nodes)
            closest?.let { connectNodes(node, it) }
        }
        nearestConnectableNode(startNode, nodes)?.let { connectNodes(startNode, it) }
        nearestConnectableNode(endNode, nodes)?.let { connectNodes(endNode, it) }
    }

    private fun nearestConnectableNode(
        node: MapNodeBuilder,
        otherNodes: List<MapNodeBuilder>
    ): MapNodeBuilder? {
        var minDist = 1e10f
        var closestNode: MapNodeBuilder? = null
        val nodePos = node.posAsVec()
        otherNodes.forEach { otherNode ->
            if (otherNode === node) return@forEach
            if (otherNode.edgesTo.size >= 4) return@forEach
            if (otherNode.isLinkedTo(node)) return@forEach
            val dist = (nodePos - otherNode.posAsVec()).len()
            if (dist < minDist) {
                minDist = dist
                closestNode = otherNode
            }
        }
        return closestNode
    }

    private fun generateNodes(): List<MapNodeBuilder> {
        val nodes = mutableListOf<MapNodeBuilder>()
        require(data.amountNodes > 2)
        repeat(data.amountNodes) {
            val maxIterations = 80
            var iterations = 0
            var currentPoint: Vector2
            do {
                if (iterations > maxIterations) return nodes
                currentPoint = randomPoint()
                iterations++
            } while (!isPointValid(currentPoint))
            val node = newNode(x = currentPoint.x, y = currentPoint.y)
            nodes.add(node)
        }
        return nodes
    }

    private fun isPointValid(point: Vector2): Boolean {
        allNodes.forEach { node ->
            val distance = (node.posAsVec() - point).len()
            if (distance < 2 * data.exclusionRadius) return false
        }
        return true
    }

    private fun randomPoint(): Vector2 = Vector2(
        (0f..data.roadLength).random(random),
        (-(data.roadHeight / 2f)..(data.roadHeight / 2f)).random(random)
    )

    override fun asOnj(): OnjObject = buildOnjObject {
        name("PointCloud")
        includeAll(data.asOnj())
    }


    data class PointCloudMapGeneratorData(
        override val nodeProtectedArea: Float,
        val amountNodes: Int,
        val roadLength: Float,
        val roadHeight: Float,
        val exclusionRadius: Float,
        override val locationSignProtectedAreaWidth: Float,
        override val locationSignProtectedAreaHeight: Float,
        override val firstNodeEvent: () -> MapEvent,
        override val firstNodeTexture: String,
        override val lastNodeEvent: () -> MapEvent,
        override val lastNodeTexture: String,
        override val majorDifficulty: Int,
        val horizontalExtension: Float,
        val verticalExtension: Float,
        val eventSpawner: List<EventSpawner>,
        val decorations: List<MapGeneratorDecoration>,
        val biome: String,
    ) : BaseMapGeneratorData {

        override fun asOnj(): OnjObject = buildOnjObject {
            "amountNodes" with amountNodes
            "roadLength" with roadLength
            "roadHeight" with roadHeight
            "exclusionRadius" with exclusionRadius
            "horizontalExtension" with horizontalExtension
            "verticalExtension" with verticalExtension
            "decorations" with decorations.map { it.asOnj() }
            "biome" with biome
            "eventSpawner" with eventSpawner.map { it.asOnj() }
            includeBaseData()
        }

        companion object {
            fun fromOnj(onj: OnjObject): PointCloudMapGeneratorData = PointCloudMapGeneratorData(
                onj.get<Double>("nodeProtectedArea").toFloat(),
                onj.get<Long>("amountNodes").toInt(),
                onj.get<Double>("roadLength").toFloat(),
                onj.get<Double>("roadHeight").toFloat(),
                onj.get<Double>("locationSignProtectedAreaWidth").toFloat(),
                onj.get<Double>("locationSignProtectedAreaHeight").toFloat(),
                onj.get<Double>("exclusionRadius").toFloat(),
                { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("firstNodeEvent")) },
                onj.get<String>("firstNodeTexture"),
                { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("lastNodeEvent")) },
                onj.get<String>("lastNodeTexture"),
                onj.get<Long>("majorDifficulty").toInt(),
                onj.get<Double>("horizontalExtension").toFloat(),
                onj.get<Double>("verticalExtension").toFloat(),
                onj.get<OnjArray>("eventSpawner").value.map { EventSpawner.fromOnj(it as OnjObject) },
                onj
                    .get<OnjArray>("decorations")
                    .value
                    .map { MapGeneratorDecoration.fromOnj(it as OnjObject) },
                onj.get<String>("biome")
            )
        }
    }

    data class EventSpawner(
        val eventCreator: () -> MapEvent,
        val texture: String,
        val weight: Int,
    ) {
        fun asOnj(): OnjObject = buildOnjObject {
            "event" with eventCreator().asOnjObject()
            "weight" with weight
            "texture" with texture
        }

        companion object {
            fun fromOnj(onj: OnjObject): EventSpawner = EventSpawner(
                { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("event")) },
                onj.get<String>("texture"),
                onj.get<Long>("weight").toInt(),
            )
        }
    }

}