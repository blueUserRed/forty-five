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

    fun isLinkedTo(node: MapNode): Boolean {
        for (linkedNode in node.edgesTo) {
            if (linkedNode === this) return true
        }
        return false
    }

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

    override fun equals(other: Any?): Boolean = this === other

    override fun toString(): String {
        return "MapNode(x = $x, y = $y, isArea = $isArea)"
    }

    override fun hashCode(): Int {
        var result = isArea.hashCode()
        result = 31 * result + x.hashCode()
        result = 31 * result + y.hashCode()
        return result
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

        fun getUniqueEdgesWithOppositesFor(uniqueNodes: List<MapNode>): List<Pair<MapNode, MapNode>> {
            val edges = mutableListOf<Pair<MapNode, MapNode>>()
            for (node in uniqueNodes) {
                for (edgeTo in node.edgesTo) {
                    val pair = node to edgeTo
                    if (pair !in edges) edges.add(pair)
                }
            }
            return edges
        }

    }
}

data class MapNodeBuilder(
    val x: Float,
    val y: Float,
    val edgesTo: MutableList<MapNodeBuilder> = mutableListOf(),
    val isArea: Boolean = false,
) {

    private var buildEdges: MutableList<MapNode> = mutableListOf()

    private var inBuild: Boolean = false

    private var asNode: MapNode? = null

    fun build(): MapNode {
        if (inBuild) return asNode!!
        inBuild = true
        asNode = MapNode(buildEdges, isArea, x, y)
        for (edge in edgesTo) {
            buildEdges.add(edge.build())
        }
        inBuild = false
        return asNode!!
    }

    fun connect(other: MapNodeBuilder): Boolean {
        if (edgesTo.size > 3 || other.edgesTo.size > 3) throw IllegalArgumentException("Already to 4 Nodes connect, not anymore possible!")
        if (edgesTo.contains(other)) return false
        edgesTo.add(other)
        other.edgesTo.add(this)
        return true
    }

    private fun toStringRec(): String {
        return javaClass.name + "{x: " + x + ",y: " + y + "}"
    }

    override fun toString(): String {
        var cur = ""
        for (i in edgesTo) {
            cur += i.toStringRec() + ", "
        }
        return javaClass.name + "{x: $x,y: $y, neighbours: $cur}"
    }
}