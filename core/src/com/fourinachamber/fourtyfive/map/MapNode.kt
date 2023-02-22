package com.fourinachamber.fourtyfive.map

data class MapNode(
    val edgesTo: List<MapNode>,
    val isArea: Boolean,
    val x: Float,
    val y: Float
) {

    fun getLeft(): MapNode? = null
    fun getRight(): MapNode? = null
    fun getTop(): MapNode? = null
    fun getBottom(): MapNode? = null

    fun getUniqueNodes(): List<MapNode> {
        val nodes = mutableListOf(this)
        getUniqueNodes(nodes)
        return nodes
    }

    private fun getUniqueNodes(to: MutableList<MapNode>) {
        for (child in edgesTo) {
            if (child in to) continue
            to.add(child)
            child.getUniqueNodes(to)
        }
    }

    companion object {

        fun getUniqueEdgesFor(uniqueNodes: List<MapNode>): List<Pair<MapNode, MapNode>> {
            val edges = mutableListOf<Pair<MapNode, MapNode>>()
            for (node in uniqueNodes) {
                for (edgeTo in node.edgesTo) {
                    val pair = node to edgeTo
                    val opposite = edgeTo to node
                    if (pair !in edges && opposite !in edges) edges.add(pair)
                }
            }
            return edges
        }

    }

}
