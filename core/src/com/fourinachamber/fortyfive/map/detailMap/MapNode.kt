package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import kotlin.math.*

data class MapNode(
    val index: Int,
    val edgesTo: List<MapNode>,
    val isArea: Boolean,
    val x: Float,
    val y: Float,
    val imageName: String?,
    val imagePos: ImagePosition?,
    val nodeTexture: ResourceHandle?,
    val event: MapEvent? = null, // TODO: this will be non-nullable in the future,
) {


    private var imageCache: Drawable? = null
    private var nodeTextureCache: Drawable? = null
    private var nodePositionsForDirection: List<MapNode?> = listOf()

    fun getEdge(dir: Direction): MapNode? {
        if (nodePositionsForDirection.size != edgesTo.size) initNodeDirections()
        return nodePositionsForDirection[dir.ordinal]
    }

    private fun initNodeDirections() {
        val nodesWithAngles =
            edgesTo.map { it to (Line(Vector2(it.x, it.y), Vector2(x, y)).ang() + (PI * 2)) % (PI * 2) }
                .sortedBy { it.second }
        val finalPositions = arrayOfNulls<MapNode>(4)
        when (nodesWithAngles.size) {
            4 -> {
                val startNode =
                    nodesWithAngles.minBy { min(abs(it.second % (PI / 2)), (abs((PI / 2) - it.second % (PI / 2)))) }
                val startDir = Direction.values().minBy { abs(it.getAngle() - (startNode.second % (2 * PI))) }
                var curAng = startDir
                val startIndex = nodesWithAngles.indexOf(startNode)
                for (i in 0 until Direction.values().size) {
                    finalPositions[curAng.ordinal] = nodesWithAngles[(startIndex + i) % nodesWithAngles.size].first
                    curAng = curAng.getNextDirCounterClock()
                }
                nodePositionsForDirection = finalPositions.toList()
            }

            2, 3 -> {
                val bestAngles = calcBestAngles(nodesWithAngles, finalPositions, 0F)
                for (i in 0 until bestAngles.first.size) {
                    finalPositions[i] = bestAngles.first[i]
                }

            }
        }
        for (i in finalPositions.indices) {
            if (finalPositions[i] != null) continue
            val dir = Direction.values()[i]
            val possibleNode = edgesTo.map {
                val ang = Line(Vector2(it.x, it.y), Vector2(x, y)).ang()
                it to min(
                    min(abs(dir.getAngle() - ang), abs(dir.getAngle() + 2 * PI.toFloat() - ang)),
                    abs(dir.getAngle() - 2 * PI.toFloat() - ang)
                )
            }.minBy { it.second }
            if (possibleNode.second <= Math.PI / 2) finalPositions[i] = possibleNode.first
        }
        nodePositionsForDirection = finalPositions.toList()
    }

    private fun calcBestAngles(
        nodes: List<Pair<MapNode, Double>>,
        positions: Array<MapNode?>,
        distance: Float,
    ): Pair<Array<MapNode?>, Float> {
        if (nodes.isEmpty()) return positions to distance
        var curBestDist = Float.MAX_VALUE
        var curBestPos = positions
        for (node in nodes) {
            for (i in positions.indices) {
                val curPos = positions.copyOf()
                val curNodes = nodes.toMutableList()
                if (positions[i] != null) continue
                val dir = Direction.values()[i]
                val dist = min(
                    min(
                        abs(dir.getAngle() - node.second),
                        abs(dir.getAngle() + 2 * PI.toFloat() - node.second)
                    ),
                    abs(dir.getAngle() - 2 * PI.toFloat() - node.second)
                ).toFloat()
                curPos[dir.ordinal] = node.first
                curNodes.remove(node)
                val res = calcBestAngles(curNodes, curPos, distance + dist)
                if (res.second < curBestDist) {
                    curBestDist = res.second
                    curBestPos = res.first
                }
            }
        }
        return curBestPos to (distance + curBestDist)
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

    @MainThreadOnly
    fun getNodeTexture(screen: OnjScreen): Drawable? {
        if (nodeTexture == null) return null
        if (nodeTextureCache != null) return nodeTextureCache
        nodeTextureCache = ResourceManager.get(screen, nodeTexture)
        return nodeTextureCache
    }

    fun invalidateCachedAssets() {
        imageCache = null
        nodeTextureCache = null
    }

    fun getImageData(): MapManager.MapImageData? =
        MapManager.mapImages.find { it.name == imageName && it.type == "sign" }

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
    var nodeTexture: ResourceHandle? = null,
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
            nodeTexture,
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