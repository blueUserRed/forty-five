package com.microwavestudios.fortyfive.map

import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.map.DetailMap.MapDecoration

class DetailMapBuilder(
    var name: String = "",
    var startNode: MapNodeBuilder,
    var endNode: MapNodeBuilder,
    val decorations: MutableList<MapDecoration> = mutableListOf(),
    val animatedDecorations: MutableList<MapDecoration> = mutableListOf(),
    var isArea: Boolean = false,
    var biome: String = "wasteland",
    var scrollable: Boolean = true,
    var camPosOffset: Vector2 = Vector2(0f, 0f),
    var majorDifficulty: Int = 0,
) {

    fun uniqueNodes(): Set<MapNodeBuilder> {

        fun nodesFor(node: MapNodeBuilder, nodes: MutableSet<MapNodeBuilder>) {
            if (!nodes.add(node)) return
            node.edgesTo.forEach { nodesFor(it, nodes) }
        }

        val nodes = mutableSetOf<MapNodeBuilder>()
        nodesFor(startNode, nodes)
        return nodes
    }

    fun uniqueEdges(uniqueNodes: Set<MapNodeBuilder>): Set<Pair<MapNodeBuilder, MapNodeBuilder>> {
        val edges = mutableSetOf<Pair<MapNodeBuilder, MapNodeBuilder>>()
        uniqueNodes.forEach { node ->
            node.edgesTo.forEach { edgeNode ->
                require(node.index != edgeNode.index)
                val edge = if (node.index > edgeNode.index) node to edgeNode else edgeNode to node
                edges.add(edge)
            }
        }
        return edges
    }

    fun build(): DetailMap {
        startNode.build()
        return DetailMap(
            name,
            startNode.asNode!!,
            endNode.asNode!!,
            decorations,
            animatedDecorations,
            isArea,
            biome,
            scrollable,
            camPosOffset,
            majorDifficulty
        )
    }

    companion object {

        fun from(map: DetailMap): DetailMapBuilder {
            val (startNode, endNode) = MapNodeBuilder.fromNode(map.startNode, map)
            return DetailMapBuilder(
                map.name,
                startNode,
                endNode,
                map.decorations.toMutableList(),
                map.animatedDecorations.toMutableList(),
                map.isArea,
                map.biome,
                map.scrollable,
                map.camPosOffset,
                map.majorDifficulty,
            )
        }
    }

}
