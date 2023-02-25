package com.fourinachamber.fourtyfive.map.detailMap

import onj.builder.buildOnjObject
import onj.builder.toOnjArray
import onj.value.OnjArray
import onj.value.OnjInt
import onj.value.OnjObject

data class DetailMap(
    val startNode: MapNode
) {

    val uniqueNodes by lazy {
        startNode.getUniqueNodes()
    }

    val uniqueEdges by lazy {
        MapNode.getUniqueEdgesFor(uniqueNodes)
    }

    val uniqueEdgesWithOpposites by lazy {
        MapNode.getUniqueEdgesWithOppositesFor(uniqueNodes)
    }

    fun asOnjObject(): OnjObject = buildOnjObject {
        "nodes" with nodesAsOnjArray()
        "startNode" with uniqueNodes.indexOf(startNode)
    }

    fun nodesAsOnjArray(): OnjArray = uniqueNodes
        .map { node ->
            buildOnjObject {
                "x" with node.x
                "y" with node.y
                "isArea" with node.isArea
                "edgesTo" with node.edgesTo.map { uniqueNodes.indexOf(it) }
            }
        }
        .toOnjArray()


    companion object {

        fun readFromOnj(onj: OnjObject): DetailMap {
            val nodes = mutableListOf<MapNodeBuilder>()
            val nodesOnj = onj.get<OnjArray>("nodes")
            nodesOnj
                .value
                .forEach { nodeOnj ->
                    nodeOnj as OnjObject
                    nodes.add(
                        MapNodeBuilder(
                        nodeOnj.get<Double>("x").toFloat(),
                        nodeOnj.get<Double>("y").toFloat(),
                        mutableListOf(),
                        nodeOnj.get<Boolean>("isArea"),
                    )
                    )
                }
            nodesOnj
                .value
                .forEachIndexed { index, nodeOnj ->
                    nodeOnj as OnjObject
                    nodeOnj.get<OnjArray>("edgesTo").value.forEach { edgeToOnj ->
                        val edgeTo = (edgeToOnj as OnjInt).value.toInt()
                        nodes[index].edgesTo.add(nodes[edgeTo])
                    }
                }
            val startNodeIndex = onj.get<Long>("startNode").toInt()
            return DetailMap(nodes[startNodeIndex].build())
        }

    }

}
