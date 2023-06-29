package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import java.util.*
import kotlin.math.*

data class MapNode(
    val index: Int,
    val edgesTo: List<MapNode>,
    val isArea: Boolean,
    val x: Float,
    val y: Float,
    val imageName: String?,
    val imagePos: ImagePosition?,
    val event: MapEvent? = null, // TODO: this will be non-nullable in the future,
) {


    private var imageCache: Drawable? = null
    fun getEdge(dir: Direction): MapNode? {

        val possibleNode = edgesTo.map {
            val ang = Line(Vector2(it.x, it.y), Vector2(x, y)).ang()
            it to min(min(abs(dir.getAngle() - ang), abs(dir.getAngle() + 2 * PI.toFloat() - ang)),abs(dir.getAngle() - 2 * PI.toFloat() - ang))
        }.minBy { it.second }
        if (possibleNode.second > Math.PI/2) return null
        return possibleNode.first
    }

    @MainThreadOnly
    fun getImage(screen: OnjScreen): Drawable? {
        if (imageName == null) return null
        if (imageCache != null) return imageCache
        val handle = getImageData()?.resourceHandle
        if (handle == null) {
            FortyFiveLogger.warn(logTag, "No image data found for $imageName")
            return null
        }
        imageCache = ResourceManager.get(screen, handle)
        return imageCache
    }

    fun invalidateCachedAssets() {
        imageCache = null
    }

    fun getImageData(): MapManager.MapImageData? = MapManager.mapImages.find { it.name == imageName }

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

    fun toStringRec(): String {
        return "(x = $x, y = $y)"
    }

    override fun toString(): String {
        return "MapNode(x = $x, y = $y, isArea = $isArea, edgesTo = ${edgesTo.map { it.toStringRec() }})"
    }

    override fun hashCode(): Int {
        var result = isArea.hashCode()
        result = 31 * result + x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }

    companion object {

        const val logTag = "MapNode"

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
    var index: Int = 0,
    var x: Float,
    var y: Float,
    val edgesTo: MutableList<MapNodeBuilder> = mutableListOf(),
    var isArea: Boolean = false,
    var imageName: String? = null,
    var imagePos: MapNode.ImagePosition = MapNode.ImagePosition.UP,
    var event: MapEvent? = null // TODO: this will be non-nullable in the future
) {

    private var buildEdges: MutableList<MapNode> = mutableListOf()

    private var inBuild: Boolean = false

    var asNode: MapNode? = null
        private set

    val dirNodes: Array<Int?> = arrayOfNulls(4)


    fun scale(xScale: Float = 1F, yScale: Float = 1F) {
        x *= xScale
        y *= yScale
    }

    /**
     * rotates the node around P(0|0) in radians PI/2 means 90 Degree to left
     */
    fun rotate(radianVal: Double = PI) {
        val xNew = cos(radianVal) * x - sin(radianVal) * y
        val yNew = sin(radianVal) * x + cos(radianVal) * y
        x = xNew.toFloat()
        y = yNew.toFloat()
    }

    fun build(): MapNode {
        if (inBuild) return asNode!!
        inBuild = true
        asNode = MapNode(
            index,
            buildEdges,
            isArea,
            x, y,
            imageName,
            imagePos,
            event
        )
        for (edge in edgesTo) {
            buildEdges.add(edge.build())
        }
        return asNode!!
    }

    fun connect(other: MapNodeBuilder, dir: Direction = Direction.LEFT, addAsNext: Boolean = true): Boolean {
        if (other == this || edgesTo.contains(other)) return false
        if (edgesTo.size > 3 || other.edgesTo.size > 3) throw IllegalArgumentException("Already to 4 Nodes connect, not anymore possible!")
        edgesTo.add(other)
        other.edgesTo.add(this)
        if (addAsNext) {
            dirNodes[dir.ordinal] = edgesTo.size - 1
            other.dirNodes[dir.getOpposite().ordinal] = other.edgesTo.size - 1
        }
        return true
    }

    private fun toStringRec(): String {
        return javaClass.simpleName + "{x: " + x + ",y: " + y + "}"
    }

    override fun toString(): String {
        val cur = edgesTo.joinToString(separator = ",", transform = { it.toStringRec() })
        return javaClass.simpleName + "{x: $x, y: $y, neighbors: $cur}"
    }

    override fun equals(other: Any?): Boolean {
        return other != null &&
                (other is MapNodeBuilder || other is MapNode) &&
                other is MapNodeBuilder &&
                other.x == this.x &&
                other.y == this.y
    }

    override fun hashCode(): Int {
        return (x * 100 + y).hashCode()
    }

    fun posAsVec(): Vector2 {
        return Vector2(x, y)
    }

    fun sizeAsVec(): Vector2 {
        return Vector2(5F, 5F)
    }
}