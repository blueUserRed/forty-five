package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fourtyfive.utils.Vector2
import com.fourinachamber.fourtyfive.utils.clone
import com.fourinachamber.fourtyfive.utils.random
import com.fourinachamber.fourtyfive.utils.subListTillMax
import onj.value.OnjObject
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.*
import kotlin.random.Random

/**
 * @author Zwickelstorfer Felix
 *
 * Generates a "road map"
 */
class SeededMapGenerator(
    private val seed: Long = 103,
    /**
     * all possible restrictions and features which the map should have
     */
    private val restrictions: MapRestriction = MapRestriction()
) {
    private lateinit var nodes: List<MapNodeBuilder>
    private val rnd: Random = Random(seed)

    /**
     * the first line which is generated, which is always a fully connected line, to ensure that there is a way from beginning to end
     */
    private lateinit var mainLine: MapGeneratorLine

    private fun build(): MapNode {
        nodes.forEachIndexed { index, node -> node.index = index }
        return nodes[0].build()
    }

    /**
     * generates the line
     */
    fun generate(name: String): DetailMap {
        val nodes: MutableList<MapNodeBuilder> = generateNodesPositions()
        val connections = checkAndChangeConnectionIntersection(nodes)
        addAreas(nodes, connections)
        addEvents(nodes)
        nodes.forEach { it.scale(1F, .6F) }
        val decos = generateDecorations(nodes, connections)
        nodes.forEach { it.rotate(restrictions.rotation) }
        this.nodes = nodes
        return DetailMap(name, build(), this.nodes.last().asNode!!, decos)
    }

    private fun generateDecorations(
        nodes: List<MapNodeBuilder>,
        connections: MutableList<Line>
    ): List<DetailMap.MapDecoration> {
        val decos: MutableList<DetailMap.MapDecoration> = mutableListOf()
        restrictions.decorations.forEach {
            decos.add(it.getDecoration(nodes, restrictions, connections))
        }
        return decos
    }

    //nodeTexture: "key_select_frame",
//        playerTexture: "enemy_texture",
//        playerWidth: 2.0,
//        playerHeight: 6.0,
//        background: "white_texture",
//        edgeTexture: "black_texture",
//        playerMovementTime: 0.3,
//        directionIndicator: "heart_texture",
//        detailWidgetName: "mapEventDetail"
    private fun addEvents(nodes: MutableList<MapNodeBuilder>) {
        val nodesWithoutEvents: MutableList<MapNodeBuilder> = nodes.filter { a -> a.event == null }.toMutableList()
        for (curEvent in restrictions.fixedEvents) {
            if (nodesWithoutEvents.isEmpty()) {
                break
            }
            val curNode = nodesWithoutEvents.random(rnd)
            curNode.event = curEvent
            nodesWithoutEvents.remove(curNode)
        }
        val maxWeight: Int = restrictions.optionalEvents.sumOf { a -> a.first }
        val allWeightEnds = mutableListOf<Double>()
        var curSum = .0
        for (i in restrictions.optionalEvents) {
            curSum += i.first
            allWeightEnds.add(curSum / maxWeight)
        }
        while (nodesWithoutEvents.isNotEmpty()) {
            val curNode = nodesWithoutEvents.random(rnd)
            val rndMy = rnd.nextDouble()
            curNode.event =
                restrictions.optionalEvents[allWeightEnds.indexOf(allWeightEnds.first { it >= rndMy })].second()
            nodesWithoutEvents.remove(curNode)
        }
    }

    /**
     * adds the areas at the end, after all other nodes were placed
     */
    private fun addAreas(nodes: MutableList<MapNodeBuilder>, connections: MutableList<Line>) {
        val areaNodes: MutableList<MapNodeBuilder> = mutableListOf()
        areaNodes.add(mainLine.lineNodes.first())
        areaNodes.add(mainLine.lineNodes.last())
        mainLine.lineNodes.first().event = EnterMapMapEvent(restrictions.startArea, false)
        mainLine.lineNodes.last().event = EnterMapMapEvent(restrictions.endArea, true)
        for (areaName in restrictions.otherAreas) {
            var direction: Direction
            var borderNodes: List<MapNodeBuilder>
            var newPos: Vector2
            do {
                val x: Float =
                    (restrictions.minDistanceBetweenAreas..(mainLine.lineNodes.last().x - restrictions.minDistanceBetweenAreas))
                        .random(rnd)
                direction = arrayOf(Direction.DOWN, Direction.UP).random(rnd)
                newPos = Vector2(
                    x,
                    getLimitInRange(x, nodes, direction)
                            + restrictions.distanceFromAreaToLine * if (direction == Direction.UP) 1 else -1
                )
                borderNodes = getBorderNodesInArea(direction, nodes, newPos)
            } while (isIllegalPositionForArea(newPos, areaNodes))
            val newArea = MapNodeBuilder(
                0,
                newPos.x,
                newPos.y,
                event = EnterMapMapEvent(areaName, false)
            ) //TODO add direction of event picture
            borderNodes.random(rnd).connect(newArea, direction)
            connections.add(Line(newPos, newArea.edgesTo.first().posAsVec()))
            areaNodes.add(newArea)
        }
        areaNodes.filter { it !in nodes }.forEach { nodes.add(it) }
    }

    /**
     * checks if there are any other areas too close
     */
    private fun isIllegalPositionForArea(
        newPos: Vector2,
        areaNodes: MutableList<MapNodeBuilder>
    ): Boolean {
        return areaNodes.stream()
            .anyMatch { Vector2(it.x, it.y).sub(newPos).len() < restrictions.minDistanceBetweenAreas }
    }

    /**
     * searches for the highest or lowest position of nodes in an area
     */
    private fun getLimitInRange(x: Float, nodes: MutableList<MapNodeBuilder>, direction: Direction): Float {
        val nodesInRange: List<Float> =
            nodes.filter { abs(it.x - x) < restrictions.rangeToCheckBetweenNodes }.map { it.y }
        return if (direction == Direction.UP) nodesInRange.max() else nodesInRange.min()

    }

    /**
     * returns all nodes in a specific radius around the area
     */
    private fun getBorderNodesInArea(
        direction: Direction,
        nodes: MutableList<MapNodeBuilder>,
        position: Vector2
    ): List<MapNodeBuilder> {
        return nodes.filter {
            Vector2(it.x, it.y).sub(position)
                .len() < restrictions.distanceFromAreaToLine * (1 + restrictions.percentageForAllowedNodesInRangeBetweenLineAndArea)
                    && it.dirNodes[direction.ordinal] == null && it.edgesTo.size < 4
        }
    }


    /**
     * generates all nodes and lines and connects them
     */
    private fun generateNodesPositions(): MutableList<MapNodeBuilder> {
        val nodes: MutableList<MapNodeBuilder> = mutableListOf()
        val nbrOfNodes = (restrictions.minNodes..restrictions.maxNodes).random(rnd)
        mainLine = MapGeneratorLine(seed, restrictions, rnd, nbrOfPoints = nbrOfNodes)
        var curDown = mainLine
        var curUp = mainLine
        for (i in 1 until restrictions.maxLines) {
            if (rnd.nextBoolean()) {
                curDown = curDown.generateNextLine(true)!!
            } else {
                curUp = curUp.generateNextLine(false)!!
            }
        }
        mainLine.connectWithEachOther(null, nodes)
        return nodes
    }

    /**
     * tries to fix all connections until all are fixed
     */
    private fun checkAndChangeConnectionIntersection(nodes: MutableList<MapNodeBuilder>): MutableList<Line> {
        val uniqueLines: MutableList<Line> = mutableListOf()
        for (node in nodes) {
            for (other in node.edgesTo) {
                if (Line(Vector2(other.x, other.y), Vector2(node.x, node.y)) !in uniqueLines) {
                    uniqueLines.add(Line(Vector2(node.x, node.y), Vector2(other.x, other.y)))
                }
            }
        }
        @Suppress("ControlFlowWithEmptyBody")
        while (checkLinesNotIntercepting(uniqueLines, nodes));
        return uniqueLines
    }

    /**
     * checks if lines are intercepting, and if they are, they either get deleted, or a node is placed at the intersection point
     */
    private fun checkLinesNotIntercepting(uniqueLines: MutableList<Line>, nodes: MutableList<MapNodeBuilder>): Boolean {
        for (i in uniqueLines.indices) {
            val line1 = uniqueLines[i]
            for (j in (i + 1) until uniqueLines.size) {
                val line2 = uniqueLines[j]
                val interceptPoint = line1.intersection(line2)
                if (interceptPoint != null && nodes.none { a -> a.x == interceptPoint.x && a.y == interceptPoint.y }) {
                    correctInterceptionNode(nodes, line1, line2, interceptPoint, uniqueLines)
                    return true
                }
            }
        }
        return false
    }

    /**
     * either deletes one of the lines, or places an interception node in between
     */
    private fun correctInterceptionNode(
        nodes: MutableList<MapNodeBuilder>,
        line1: Line,
        line2: Line,
        interceptPoint: Vector2,
        uniqueLines: MutableList<Line>
    ) {
        var intersectionNode: MapNodeBuilder? = null
        for (i in nodes) {
            val newVec = interceptPoint.clone().sub(Vector2(i.x, i.y))
            if (newVec.len() < 10) {
                intersectionNode = i
                break
            }
        }
        if (intersectionNode == null) {
            val newNode = MapNodeBuilder(0, interceptPoint.x, interceptPoint.y)
            addNodeInBetween(line1, nodes, newNode, uniqueLines)
            addNodeInBetween(line2, nodes, newNode, uniqueLines)
            nodes.add(newNode)
        } else {
            deleteLineInBetween(
                getLineToDelete(nodes, line1, line2, intersectionNode),
                nodes,
                uniqueLines
            )
        }
    }

    /**
     * tries to evaluate which line is better and which line should be deleted
     */
    private fun getLineToDelete(
        nodes: MutableList<MapNodeBuilder>,
        line1: Line,
        line2: Line,
        possibleIntersectionNode: MapNodeBuilder
    ): Line {
        val curNodes = arrayOf(
            nodes.first { a -> a.x == line1.start.x && a.y == line1.start.y },
            nodes.first { a -> a.x == line1.end.x && a.y == line1.end.y },
            nodes.first { a -> a.x == line2.start.x && a.y == line2.start.y },
            nodes.first { a -> a.x == line2.end.x && a.y == line2.end.y },
        )
        if (curNodes[0] in mainLine.lineNodes && curNodes[1] in mainLine.lineNodes) return line2
        if (curNodes[2] in mainLine.lineNodes && curNodes[3] in mainLine.lineNodes) return line1

        if (possibleIntersectionNode == curNodes[0] || possibleIntersectionNode == curNodes[1]) return line2
        if (possibleIntersectionNode == curNodes[2] || possibleIntersectionNode == curNodes[3]) return line1
        return if (rnd.nextBoolean()) line1 else line2
    }

    /**
     * deletes a line and removes all traces that it ever existed
     */
    private fun deleteLineInBetween(
        nodesConnection: Line,
        nodes: MutableList<MapNodeBuilder>,
        uniqueLines: MutableList<Line>
    ) {
        val firstNode = nodes.first { a -> a.x == nodesConnection.start.x && a.y == nodesConnection.start.y }
        val secNode = nodes.first { a -> a.x == nodesConnection.end.x && a.y == nodesConnection.end.y }
        uniqueLines.remove(nodesConnection)
        firstNode.dirNodes[firstNode.edgesTo.indexOf(secNode)] = null
        secNode.dirNodes[secNode.edgesTo.indexOf(firstNode)] = null
        firstNode.edgesTo.remove(secNode)
        secNode.edgesTo.remove(firstNode)
    }

    private fun addNodeInBetween(
        nodesConnection: Line,
        nodes: MutableList<MapNodeBuilder>,
        newNode: MapNodeBuilder,
        uniqueLines: MutableList<Line>
    ) {
        val firstNode = nodes.first { a -> a.x == nodesConnection.start.x && a.y == nodesConnection.start.y }
        val secNode = nodes.first { a -> a.x == nodesConnection.end.x && a.y == nodesConnection.end.y }
        firstNode.edgesTo[firstNode.edgesTo.indexOf(secNode)] = newNode
        secNode.edgesTo[secNode.edgesTo.indexOf(firstNode)] = newNode
        newNode.edgesTo.add(firstNode)
        newNode.edgesTo.add(secNode)
        nodes.add(newNode)
        uniqueLines.remove(nodesConnection)
        uniqueLines.add(Line(Vector2(firstNode.x, firstNode.y), Vector2(newNode.x, newNode.y)))
        uniqueLines.add(Line(Vector2(newNode.x, newNode.y), Vector2(secNode.x, secNode.y)))
        //TODO set directions for the "node in between"
    }

    /*    fun generateBezier(): DetailMap {
            val nodes: MutableList<MapNodeBuilder> = mutableListOf()
            val nbrOfNodes = (restrictions.minNodes..restrictions.maxNodes).random(rnd)
    //        val boundary: Float = 50F
            nodes.add(MapNodeBuilder(0F, 0F))
    //        nodes.add(MapNodeBuilder(boundary, 0F))
    //        nodes.add(MapNodeBuilder(boundary, boundary))
    //        nodes.add(MapNodeBuilder(0F, boundary))
    //        nodes.add(MapNodeBuilder(0F, 0F))
            val curve = BezierCurve(seed, restrictions, rnd, nbrOfNodes)

            val accuracy = nbrOfNodes - 1
            for (i in 0 until accuracy) {
                addNodeFromCurve(
                    curve,
                    (max(
                        min(i.toFloat() + ((-0.75F / accuracy)..(0.75F / accuracy)).random(rnd), accuracy.toFloat()),
                        0F
                    ) / accuracy.toFloat()),
                    nodes
                )
            }
            val vec: Vector2 = curve.getPos(1F)
            nodes.add(MapNodeBuilder(vec.x, vec.y))

            for (i in 1 until nodes.size) {
                nodes[i].connect(nodes[i - 1])
            }
            this.nodes = nodes
            return DetailMap(build(), listOf())
        }

        private fun addNodeFromCurve(
            curve: BezierCurve,
            t: Float,
            nodes: MutableList<MapNodeBuilder>
        ) {
            val vec: Vector2 = curve.getPos(t)
            nodes.add(MapNodeBuilder(vec.x, vec.y))
        }*/


    class MapGeneratorLine(
        private val seed: Long,
        private val restrict: MapRestriction,
        private val rnd: Random = Random(seed),
        /**
         * the "originLine", from which direction it came (since it starts at the middle and goes up and down)
         */
        oldLine: MapGeneratorLine? = null,
        /**
         * if the old line was a minimum or maximum (True means, it was a minimum, and this line now is the new minimum)
         */
        isOldLineMin: Boolean = false,
        val nbrOfPoints: Int = 15,
    ) {
        val lineNodes: List<MapNodeBuilder>
        var lineUp: MapGeneratorLine? = null
        var lineDown: MapGeneratorLine? = null

        init {
            val points: MutableList<Vector2> = mutableListOf()
            if (oldLine != null) {
                if (isOldLineMin) {
                    lineUp = oldLine
                } else {
                    lineDown = oldLine
                }
                calcPointsForAdditionalLines(points, oldLine, isOldLineMin)
            } else {
                calcPointsForFirstLine(points)
            }

            val nodesList: MutableList<MapNodeBuilder> = mutableListOf()
            points.forEach { p -> nodesList.add(MapNodeBuilder(0, p.x, p.y)) }
            this.lineNodes = nodesList
        }

        /**
         * calculates the points, if it is not the main (first) line
         */
        private fun calcPointsForAdditionalLines(
            points: MutableList<Vector2>,
            oldLine: MapGeneratorLine,
            isOldLineMin: Boolean
        ) {
            points.add(Vector2(oldLine.lineNodes[0].x, oldLine.lineNodes[0].y))
            for (i in 1 until nbrOfPoints) {
                val (pointToAdd: Vector2, posRange) = calcPointToAdd(points, isOldLineMin, oldLine)
                if (pointToAdd.y !in posRange) {
                    points.add(Vector2(pointToAdd.x, posRange.random(rnd)))
                } else {
                    points.add(pointToAdd)
                }
            }
            points.removeFirst()
        }

        /**
         * calculates the point to add for the additional lines
         */
        private fun calcPointToAdd(
            points: MutableList<Vector2>,
            isOldLineMin: Boolean,
            oldLine: MapGeneratorLine
        ): Pair<Vector2, ClosedFloatingPointRange<Float>> {
            val posVec: Vector2 = generateRandomPoint()
            val pointToAdd: Vector2 = posVec.add(points.last())
            val posRange = getPossibleYPoint(pointToAdd, isOldLineMin, oldLine)
            return Pair(pointToAdd, posRange)
        }

        private fun getPossibleYPoint(
            pointToAdd: Vector2,
            oldLineMin: Boolean,
            oldLine: MapGeneratorLine
        ): ClosedFloatingPointRange<Float> {
            if (oldLineMin) {
                val a = oldLine.getMinInRange(pointToAdd.x) - 5
                return ((a - restrict.maxWidth)..(a))
            }
            val a = oldLine.getMaxInRange(pointToAdd.x) + 5
            return ((a)..(a + restrict.maxWidth))
        }

        /**
         * returns the lowest x pos in a certain range of that line
         */
        private fun getMinInRange(x: Float): Float {
            var minPos: Float = Float.MAX_VALUE
            lineNodes.forEach { a ->
                if (abs(x - a.x) < restrict.rangeToCheckBetweenNodes / 2) minPos = min(minPos, a.y)
            }
            if (minPos == Float.MAX_VALUE) {
                minPos = lineNodes.stream().min { o1, o2 -> (abs(o1.x - x) compareTo abs(o2.x - x)) }.orElse(null).y
            }
            return minPos
        }

        /**
         * returns the highest x pos in a certain range of that line
         */
        private fun getMaxInRange(x: Float): Float {
            var maxPos: Float = Float.MIN_VALUE
            lineNodes.forEach { a ->
                if (abs(x - a.x) < restrict.rangeToCheckBetweenNodes / 2) maxPos = max(maxPos, a.y)
            }
            return maxPos
        }

        private fun calcPointsForFirstLine(points: MutableList<Vector2>) {
            points.add(Vector2(0, 0))
            for (i in 1 until nbrOfPoints) {
                val posVec: Vector2 = generateRandomPoint()
                if (abs(points.last().y + posVec.y) > restrict.maxWidth / 2) posVec.y *= -1
                points.add(posVec.add(points.last()))
            }
            if (abs(points.last().y) > 1) {
                val posVec: Vector2 = generateRandomPoint()
                points.add(Vector2(posVec.add(points.last()).x, 0F))
            }
        }

        /**
         * generates a possible new Vector to go to
         */
        private fun generateRandomPoint(): Vector2 {
            val length: Float =
                getMultiplier() * restrict.averageLengthOfLineInBetween * (1 + (rnd.nextFloat() * 0.1F))
            val angle: Float =
                (((1 - restrict.maxAnglePercent) / 2)..(1 - (1 - restrict.maxAnglePercent) / 2)).random(rnd) * Math.PI.toFloat()
            return Vector2(
                (sin(angle.toDouble()) * length).toFloat(),
                (cos(angle.toDouble()) * 0.5 * length).toFloat()
            )
        }

        private fun getMultiplier(): Float {
            val max = 5
            val a = rnd.nextInt(max - 1) + 1
            return a * a * (a * ((0.2F..1.05F).random(rnd))) / 10 / 5 + 1
        }

        fun generateNextLine(generateUp: Boolean = true): MapGeneratorLine? {
            if (lineDown != null && lineUp == null) {
                lineUp = MapGeneratorLine(seed, restrict, rnd, this, false, nbrOfPoints - 1)
                return lineUp
            }
            if (lineUp != null && lineDown == null) {
                lineDown = MapGeneratorLine(seed, restrict, rnd, this, true, nbrOfPoints - 1)
                return lineDown
            }
            if (lineUp == null) {
                return if (generateUp) {
                    lineUp = MapGeneratorLine(seed, restrict, rnd, this, false, nbrOfPoints - 1)
                    lineUp
                } else {
                    lineDown = MapGeneratorLine(seed, restrict, rnd, this, true, nbrOfPoints - 1)
                    lineDown
                }
            }
            return null
        }


        /**
         * creates lines of connection for the nodes (like seperate paths)
         */
        fun connectWithEachOther(lastOne: MapGeneratorLine?, nodes: MutableList<MapNodeBuilder>) {
            if (lastOne == null) {
                for (i in 1 until lineNodes.size) {
                    lineNodes[i].connect(lineNodes[i - 1], Direction.LEFT)
                }
                lineNodes.first().dirNodes[Direction.LEFT.ordinal] = -1
                lineNodes.last().dirNodes[Direction.RIGHT.ordinal] = -1
                for (i in lineNodes) {
                    if (i !in nodes) nodes.add(i)
                }

//                var lastToTop: Int = -2
//                var lastToBottom: Int = -2
                for (i in lineNodes.indices) {
                    val numberOfConnections: Int = getNbrOfConn(
                        rnd,
                        restrict.splitProb + max(0F, 0.3F - i / 10F)
                    ) //TODO maybe extract some methods to the MapNode, due to it being more beautiful
                    if (numberOfConnections > 1) {
                        createConnection(lineNodes[i], numberOfConnections - 1, this, this, nodes, true)
                    }
                }
            }
        }

        /**
         * represents a path which connects nodes recursive and random with each other (like a pathfinder it can go up, down or right and starts always at the main line) related to [SeededMapGenerator.MapGeneratorLine.connectWithEachOther]
         */
        private fun createConnection(
            node: MapNodeBuilder,
            numberOfWishedConnections: Int,
            curLine: MapGeneratorLine,
            mainLine: MapGeneratorLine,
            nodes: MutableList<MapNodeBuilder>,
            isFirst: Boolean = false
        ) {
            if (node in mainLine.lineNodes && !isFirst) {
                return
            }
            if (node !in nodes) nodes.add(node)
            var numberOfMissingConnections: Int = numberOfWishedConnections
            val posDirs: MutableList<Int> =
                getPossibleDirectionsToCreatePathsTo(node, curLine)
            while (numberOfMissingConnections > 0 && posDirs.isNotEmpty() && node.edgesTo.size < 4) {
                val curDir: Direction = Direction.values()[posDirs.random(rnd)]
                posDirs.remove(curDir.ordinal)
                val possiblePointsToConnectTo: List<MapNodeBuilder> =
                    getPossiblePointsToConnect(node, curLine, curDir, nodes)
                if (possiblePointsToConnectTo.isEmpty()) continue
                val nodeToConnectTo = possiblePointsToConnectTo.random(rnd)
                node.connect(nodeToConnectTo, curDir)
                numberOfMissingConnections -= 1
                curDir.getOtherLine(curLine)
                    ?.let {
                        createConnection(
                            nodeToConnectTo,
                            getNbrOfConn(rnd, restrict.splitProb),
                            it,
                            mainLine,
                            nodes,
                            false
                        )
                    }
            }
        }

        /**
         * searches all possible points to connect to for generating a path
         */
        private fun getPossiblePointsToConnect(
            node: MapNodeBuilder,
            curLine: MapGeneratorLine,
            curDir: Direction,
            nodes: MutableList<MapNodeBuilder>
        ): List<MapNodeBuilder> {
            val posNodes: MutableList<MapNodeBuilder> = mutableListOf()
            when (curDir) {
                Direction.UP -> curLine.lineUp?.getNextXNodesWithoutSpecialConnection(node, curDir.getOpposite())
                    ?.let { posNodes.addAll(it) }

                Direction.DOWN -> curLine.lineDown?.getNextXNodesWithoutSpecialConnection(node, curDir.getOpposite())
                    ?.let { posNodes.addAll(it) }

                Direction.RIGHT -> posNodes.add(curLine.lineNodes[curLine.lineNodes.indexOf(node) + 1])
                Direction.LEFT -> {}
            }

            for (nodeToTestIfInRange in nodes) {
                if (nodeToTestIfInRange != node)
                    for (possibleNode in posNodes) {
                        if (nodeToTestIfInRange != possibleNode) {
                            if (Intersector.intersectSegmentCircle(
                                    Vector2(node.x, node.y),
                                    Vector2(possibleNode.x, possibleNode.y),
                                    Vector2(nodeToTestIfInRange.x, nodeToTestIfInRange.y),
                                    10F.pow(2)
                                )
                            ) {
                                posNodes.remove(possibleNode)
                                break
                            }
                        }
                    }
            }
            return posNodes
        }


        private fun getNextXNodesWithoutSpecialConnection(
            node: MapNodeBuilder,
            direction: Direction,
            numberOfNodes: Int = 3
        ): List<MapNodeBuilder> {
            return lineNodes.filter { a -> a.x > node.x }.sortedBy { a -> a.x }.subListTillMax(numberOfNodes)
                .filter { a -> a.dirNodes[direction.ordinal] == null }
        }

        private fun getPossibleDirectionsToCreatePathsTo(
            node: MapNodeBuilder,
            curLine: MapGeneratorLine
        ): MutableList<Int> {
            val posDirs: MutableList<Int> =
                node.dirNodes.mapIndexedNotNull { index, elem -> index.takeIf { elem == null } } as MutableList<Int>
            if (curLine.lineUp == null) posDirs.remove(Direction.UP.ordinal)
            if (curLine.lineDown == null) posDirs.remove(Direction.DOWN.ordinal)
            if (curLine.lineNodes.last() == node) posDirs.remove(Direction.RIGHT.ordinal)
            return posDirs
        }

        /**
         * calculates how many connections a [MapNodeBuilder] should build (at least one is existing before)
         *
         * @param max the probability for the maximum to be high
         */
        private fun getNbrOfConn(rnd: Random, max: Float): Int {
            return (sqrt(rnd.nextDouble()) * max * 3 + 0.8).coerceAtMost(3.0).toInt()
        }
    }

}

class Line(val start: Vector2, val end: Vector2) {
//    fun angle(): Float {
//        return start.sub(end).angleRad()
//    }

    fun intersection(other: Line): Vector2? {
        val intersectVector = Vector2()
        if (Intersector.intersectLines(start, end, other.start, other.end, intersectVector)) {
            if (intersectVector.x > min(start.x, end.x) && intersectVector.x < max(start.x, end.x)) {
                if (intersectVector.x > min(other.start.x, other.end.x)
                    && intersectVector.x < max(other.start.x, other.end.x)
                ) return intersectVector
            }
        }
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Line) return false
        return start.x == other.start.x && start.y == other.start.y &&
                end.x == other.end.x && end.y == other.end.y
    }

    override fun toString(): String {
        return javaClass.simpleName + "{start: ${start}, end: ${end}}"
    }

    override fun hashCode(): Int {
        var result = start.hashCode()
        result = 31 * result + end.hashCode()
        return result
    }
}

enum class Direction {
    UP {
        override fun getOpposite(): Direction {
            return DOWN
        }

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine? {
            return curLine.lineUp
        }
    },
    DOWN {
        override fun getOpposite(): Direction {
            return UP
        }

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine? {
            return curLine.lineDown
        }
    },
    LEFT {
        override fun getOpposite(): Direction {
            return RIGHT
        }

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine {
            return curLine
        }
    },
    RIGHT {
        override fun getOpposite(): Direction {
            return LEFT
        }

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine? {
            return LEFT.getOtherLine(curLine)
        }
    };

    abstract fun getOpposite(): Direction

    /**
     * returns the line from the opposite direction
     */
    abstract fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine?
}

/*class BezierCurve(
    private val seed: Long,
    restrict: MapRestriction,
    rnd: Random = Random(seed),
    nbrOfNodes: Int = 15,
    nbrOfPoints: Int = 15
) {
    private val points: MutableList<Vector2> = mutableListOf()
    private val max = (30.toFloat().pow(3 * restrict.compressProb))

    init {
        points.add(Vector2(0F, 0F))
        points.add(Vector2(0F, 0F))
        var lastX: Float = 0F
        while (points.size < nbrOfPoints - 1) {
            lastX += rnd.nextFloat() * 25 * nbrOfNodes / nbrOfPoints + 4
            points.add(Vector2(lastX + 1, rnd.nextFloat() * max - max / 2))
        }
        points.add(Vector2(lastX + rnd.nextFloat() * 25 + 4, 0F))
    }

    fun getPos(t: Float): Vector2 {
        val sum = Vector2()
        for (i in 0 until points.size) {
            val curExp = binCoefficient(points.size - 1, i).toDouble() * t.pow(i) * (1 - t).toDouble()
                .pow((points.size - 1 - i).toDouble())
            sum.x += (curExp * points[i].x).toFloat()
            sum.y += (curExp * points[i].y).toFloat()
        }
        return sum
    }

    private fun binCoefficient(n: Int, k: Int): Int {
        if (k > n) return 0
        if (k == 0 || k == n) return 1
        return binCoefficient(n - 1, k - 1) + binCoefficient(n - 1, k)
    }
}*/

sealed class DistributionFunction(
    private val seed: Long,
    protected val type: String,
    protected val density: Float,
    protected val baseWidth: Float,
    protected val baseHeight: Float,
    private val scaleMin: Float,
    private val scaleMax: Float,
    private val collidesOnlyWithNodes: Boolean,
) {
    protected val rnd: kotlin.random.Random = Random(seed)

    class Random(
        seed: Long, type: String, density: Float = 0.25F, base_width: Float,
        base_height: Float, scale_min: Float, scale_max: Float, collidesOnlyWithNodes: Boolean
    ) :
        DistributionFunction(
            seed,
            type,
            density,
            base_width,
            base_height,
            scale_min,
            scale_max,
            collidesOnlyWithNodes
        ) {
        override fun getPossiblePositions(
            xRange: ClosedFloatingPointRange<Float>,
            yRange: ClosedFloatingPointRange<Float>,
            restrict: MapRestriction
        ): List<Vector2> {

            val positions: MutableList<Vector2> = mutableListOf()
            var pointsLeft: Int = ((xRange.endInclusive - xRange.start) / baseWidth
                    * (yRange.endInclusive - yRange.start) / baseHeight * density).toInt()
            while (pointsLeft > 0) {
                positions.add(Vector2(xRange.random(rnd), yRange.random(rnd)))
                pointsLeft--
            }
            return positions
        }

    }

//    class SimplexNoise(seed: Long, type: String, moreLikelyAtEvents: List<Pair<Int, String>> = listOf()) :
//        DistributionFunction(seed, type, moreLikelyAtEvents) {
//        override fun getPossiblePositions(): List<Vector2> {
//            return mutableListOf()
//        }
//    }

    abstract fun getPossiblePositions(
        xRange: ClosedFloatingPointRange<Float>,
        yRange: ClosedFloatingPointRange<Float>,
        restrict: MapRestriction
    ): List<Vector2>

    fun getDecoration(
        nodes: List<MapNodeBuilder>,
        restrictions: MapRestriction,
        connections: MutableList<Line>
    ): DetailMap.MapDecoration {
        val xRange =
            ((nodes.minOf { it.x } - restrictions.decorationPadding)..(nodes.maxOf { it.x } + restrictions.decorationPadding))
        val yRange =
            ((nodes.minOf { it.y } - restrictions.decorationPadding)..(nodes.maxOf { it.y } + restrictions.decorationPadding))
        nodes.forEach { println(it.x.toString() + ", " + it.y) }
        val possiblePositions: List<Pair<Vector2, Float>> =
            getPossiblePositions(xRange, yRange, restrictions).map { it to 1F/*(scaleMin..scaleMax).random(rnd)*/ }
        return DetailMap.MapDecoration(
            type,
            baseWidth,
            baseHeight,
            possiblePositions.filter { isPossibleToPlaceNode(it, nodes, connections) }.sortedBy { -it.first.y }
        )
    }

    private fun isPossibleToPlaceNode(
        data: Pair<Vector2, Float>,
        nodes: List<MapNodeBuilder>,
        connections: MutableList<Line>
    ): Boolean {
        if (collidesOnlyWithNodes) {
            val rect = data.first to Vector2(baseWidth * data.second, baseHeight * data.second)
            for (it in nodes) {
                if (it.x == 143.5325F && it.y == -122.11233F) {
                    if (it.posAsVec().sub(data.first).len() < 20) {
                        println("Hi")
                    }
                }
                val tempRect = it.posAsVec() to it.sizeAsVec()
                if ((rect.first.x < tempRect.first.x + tempRect.second.x) &&
                    (rect.first.y < tempRect.first.y + tempRect.second.y) &&
                    (tempRect.first.x < rect.first.x + rect.second.x) &&
                    (tempRect.first.y < rect.first.y + rect.second.y)
                ) {
                    return false
                }
            }
        } else {
//           a paint ln("Needs to be implemented")
            return false
        }
        return true
    }
}


/**
 * all possible restrictions within the MapGenerator
 */
data class MapRestriction(
    /**
     * minimum number of nodes for main line
     */
    val maxNodes: Int = 10,
    /**
     * maximum number of nodes for main line
     */
    val minNodes: Int = 8,
    /**
     * how many lines are generated and are therefore possible
     */
    val maxLines: Int = 1,
    /**
     * how likely it is for nodes to split (min. of 0.3 is recommended)
     */
    val splitProb: Float = 0.9F,
    /**
     * how far it spreads into the "y" direction or is compressed
     */
    val compressProb: Float = 0.55F,
    /**
     * the average length between two nodes on one line (actually only preferred length, not avg.)
     */
    val averageLengthOfLineInBetween: Float = 26F,

    /**
     * max "y" width for first line, and for other lines: max distance for the x Point from one line to the x Point of another line
     */
    val maxWidth: Int = 40,
    /**
     * how strong the random points can go up and down (0 means straight to the right, 1 means 180 Degree)
     */
    val maxAnglePercent: Float = 0.6F,
    /**
     * the range from where nodes are checked if there are any other from another line
     */
    val rangeToCheckBetweenNodes: Float = 70F,
    val startArea: String = "Franz",
    val endArea: String = "Huber",
    val otherAreas: List<String> = listOf("test", "cool"),
    val minDistanceBetweenAreas: Float = 100F,
    /**
     * how far the areas are from the highest/lowest point of the road in a close area around the area
     */
    val distanceFromAreaToLine: Float = 100F,
    /**
     * how far the nodes can be away from the area to be selected as the connected node to that area (formula [MapRestriction.distanceFromAreaToLine] * (1+thisValue)
     */
    val percentageForAllowedNodesInRangeBetweenLineAndArea: Float = 0.4F,
    /**
     * the rotation of the road (0 means looking right, PI/2 means looking up, and so on)
     */
    val rotation: Double = .0,

    val fixedEvents: List<MapEvent> = listOf(EmptyMapEvent(), EmptyMapEvent()),
    val optionalEvents: List<Pair<Int, () -> MapEvent>> = listOf(
        10 to { EmptyMapEvent() },
        20 to { EmptyMapEvent() },
    ),
    val decorations: List<DistributionFunction> = listOf(
        DistributionFunction.Random(123, "enemy_texture", 5.25F, 8F, 13F, 0.75F, 1.2F, true),
    ),
    val decorationPadding: Float = 20F,
) {


    companion object {

        fun fromOnj(onj: OnjObject): MapRestriction = MapRestriction(
            maxNodes = onj.get<Long>("maxNodes").toInt(),
            minNodes = onj.get<Long>("minNodes").toInt(),
            maxLines = onj.get<Long>("maxSplits").toInt(),
            splitProb = onj.get<Double>("splitProbability").toFloat(),
            compressProb = onj.get<Double>("compressProbability").toFloat(),
        )
    }
}

