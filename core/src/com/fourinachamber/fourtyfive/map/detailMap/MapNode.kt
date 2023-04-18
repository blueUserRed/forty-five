package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.screen.ResourceHandle
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.utils.MainThreadOnly

data class MapNode(
    val edgesTo: List<MapNode>,
    val blockingEdges: List<MapNode>,
    val isArea: Boolean,
    val x: Float,
    val y: Float,
    val imageHandle: ResourceHandle?,
    val imagePos: ImagePosition = ImagePosition.UP,
    val event: MapEvent? = null // TODO: this will be non-nullable in the future
) {

    private var loadedImage: Drawable? = null

    fun getLeft(): MapNode? = null
    fun getRight(): MapNode? = null
    fun getTop(): MapNode? = null
    fun getBottom(): MapNode? = null

    @MainThreadOnly
    fun getImage(screen: OnjScreen): Drawable? {
        if (imageHandle == null) return null
        if (loadedImage != null) return loadedImage
        loadedImage = ResourceManager.get(screen, imageHandle)
        return loadedImage
    }

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

    enum class ImagePosition {
        UP, DOWN, LEFT, RIGHT
    }

}

data class MapNodeBuilder(
    val x: Float,
    val y: Float,
    val edgesTo: MutableList<MapNodeBuilder> = mutableListOf(),
    val blockingEdges: MutableList<MapNodeBuilder> = mutableListOf(),
    var imageHandle: ResourceHandle? = null,
    var imagePos: MapNode.ImagePosition = MapNode.ImagePosition.UP,
    val isArea: Boolean = false,
    val event: MapEvent? = null // TODO: this will be non-nullable in the future
) {

    private var buildEdges: MutableList<MapNode> = mutableListOf()
    private var buildBlockingEdges: MutableList<MapNode> = mutableListOf()

    private var inBuild: Boolean = false

    private var asNode: MapNode? = null

    val dirNodes: Array<Int?> = arrayOfNulls(4)

    fun build(): MapNode {
        if (inBuild) return asNode!!
        inBuild = true
        asNode = MapNode(buildEdges, buildBlockingEdges, isArea, x, y, imageHandle, imagePos, event)
        for (edge in edgesTo) {
            buildEdges.add(edge.build())
        }
        blockingEdges.forEach { edge ->
            buildBlockingEdges.add(buildEdges[edgesTo.indexOf(edge)])
        }
//        inBuild = false
        return asNode!!
    }

    fun connect(other: MapNodeBuilder, dir: Direction = Direction.LEFT, addAsNext: Boolean = true): Boolean {
        if (other == this || edgesTo.contains(other)) return false
        if (edgesTo.size > 3 || other.edgesTo.size > 3) throw IllegalArgumentException("Already to 4 Nodes connect, not anymore possible!")
        edgesTo.add(other)
        other.edgesTo.add(this)
        if (addAsNext) {
            dirNodes[dir.ordinal] = edgesTo.size - 1
            other.dirNodes[dir.getOpp().ordinal] = other.edgesTo.size - 1
        }
        return true
    }

    private fun toStringRec(): String {
        return javaClass.simpleName + "{x: " + x + ",y: " + y + "}"
    }

    override fun toString(): String {
        val cur = edgesTo.joinToString(separator = ",", transform = { it.toStringRec() })
        return javaClass.name + "{x: $x, y: $y, neighbours: $cur}"
    }

    override fun equals(other: Any?): Boolean {
        return other === this // TODO: ???
//        return other != null &&
//                other is MapNodeBuilder &&
//                other.x == this.x &&
//                other.y == this.y &&
//                other.isArea == this.isArea &&
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}