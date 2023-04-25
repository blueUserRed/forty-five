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


class SeededMapGenerator(
    private val seed: Long = 103,
    private val restrictions: MapRestriction = MapRestriction()
) {
    private lateinit var nodes: List<MapNodeBuilder>
    private val rnd: Random = Random(seed)
    private lateinit var mainLine: MapGeneratorLine

    private fun build(): MapNode {
        return nodes[0].build()
    }

    fun generate(): DetailMap {
        val nodes: MutableList<MapNodeBuilder> = generateNodesPositions()
        checkAndChangeConnectionIntersection(nodes)
//        addAreas(nodes)
        this.nodes = nodes
        return DetailMap(build(), listOf())
    }

    private fun addAreas(nodes: MutableList<MapNodeBuilder>) {
        var areaNodes: MutableList<MapNodeBuilder> = mutableListOf()
        areaNodes.add(mainLine.lineNodes.first())
        areaNodes.add(mainLine.lineNodes.last())
        mainLine.lineNodes.first().event = EnterMapMapEvent(restrictions.startArea)
        mainLine.lineNodes.last().event = EnterMapMapEvent(restrictions.endArea)
        for (areaName in restrictions.otherAreas) {
            var direction: Direction
            var borderNodes: List<MapNodeBuilder>
            var newPos: Vector2 = Vector2(0, 0)
            do {
                val x: Float = 105F
//                    (restrictions.rangeToCheckBetweenAreas..(mainLine.lineNodes.last().x - restrictions.rangeToCheckBetweenAreas)).random(
//                        rnd
//                    )
                direction = arrayOf(Direction.DOWN, Direction.UP).random(rnd)
                newPos = Vector2(
                    x,
                    getLimitInRange(x, nodes, direction)
                            + restrictions.distanceFromAreaToLine * if (direction == Direction.UP) 1 else -1
                )
                borderNodes = getBorderNodesInArea(direction, nodes, x)
                if (borderNodes.isEmpty()) continue
            } while (/*isIllegalPositionForArea(borderNodes, newPos, areaNodes)*/false);
            val newArea: MapNodeBuilder = MapNodeBuilder(newPos.x, newPos.y, event = EnterMapMapEvent(areaName))
            borderNodes.random(rnd).connect(newArea, direction)
            areaNodes.add(newArea)
        }
    }

    private fun getLimitInRange(x: Float, nodes: MutableList<MapNodeBuilder>, direction: Direction): Float {
        val nodesInRange: List<Float> =
            nodes.filter { abs(it.x - x) < restrictions.rangeToCheckBetweenNodes }.map { it.y }
        return if (direction == Direction.UP) nodesInRange.max() else nodesInRange.min()

    }

    private fun getBorderNodesInArea(
        direction: Direction,
        nodes: MutableList<MapNodeBuilder>,
        xPos: Float
    ): List<MapNodeBuilder> {


        return listOf()
    }


    private fun generateNodesPositions(): MutableList<MapNodeBuilder> {
        val nodes: MutableList<MapNodeBuilder> = mutableListOf()
        val nbrOfNodes = (restrictions.minNodes..restrictions.maxNodes).random(rnd)

        println(restrictions.otherAreas)
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

    private fun checkAndChangeConnectionIntersection(nodes: MutableList<MapNodeBuilder>) {
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

    }

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
            val newNode = MapNodeBuilder(interceptPoint.x, interceptPoint.y)
            addNodeInBetween(line1, nodes, newNode, uniqueLines)
            addNodeInBetween(line2, nodes, newNode, uniqueLines)
        } else {
            deleteLineInBetween(
                getLineToDelete(nodes, line1, line2, intersectionNode, interceptPoint),
                nodes,
                uniqueLines
            )
        }
    }

    private fun getLineToDelete(
        nodes: MutableList<MapNodeBuilder>,
        line1: Line,
        line2: Line,
        possibleIntersectionNode: MapNodeBuilder,
        interceptPoint: Vector2
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

    private fun printNodesAndNeighbours(nodes: MutableList<MapNodeBuilder>) {
//        for (i in nodes) {
//            println(i.x.toString() + " " + i.y + ": ")
//            for (j in i.edgesTo) {
//                println("  " + j.x.toString() + " " + j.y)
//            }
//            println()
//        }
    }


    class MapGeneratorLine(
        private val seed: Long,
        private val restrict: MapRestriction,
        private val rnd: Random = Random(seed),
        oldLine: MapGeneratorLine? = null,
        isOldLineMin: Boolean = false,
        val nbrOfPoints: Int = 15,
    ) {
        //        var points: List<Vector2>
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
            points.forEach() { p -> nodesList.add(MapNodeBuilder(p.x, p.y)) }
            this.lineNodes = nodesList
        }

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


        private fun getMinInRange(x: Float): Float {
            var minPos: Float = Float.MAX_VALUE
            lineNodes.forEach() { a ->
                if (abs(x - a.x) < restrict.rangeToCheckBetweenNodes / 2) minPos = min(minPos, a.y)
            }
            if (minPos == Float.MAX_VALUE) {
                minPos = lineNodes.stream().min { o1, o2 -> (abs(o1.x - x) compareTo abs(o2.x - x)) }.orElse(null).y
            }
            return minPos
        }


        private fun getMaxInRange(x: Float): Float {
            var maxPos: Float = Float.MIN_VALUE
            lineNodes.forEach() { a ->
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

        private fun generateRandomPoint(): Vector2 {
            val length: Float =
                getMultiplier(5) * restrict.averageLengthOfLineInBetween * (1 + (rnd.nextFloat() * 0.1F))
            val angle: Float =
                (((1 - restrict.maxAnglePercent) / 2)..(1 - (1 - restrict.maxAnglePercent) / 2)).random(rnd) * Math.PI.toFloat()
            return Vector2(
                (sin(angle.toDouble()) * length).toFloat(),
                (cos(angle.toDouble()) * 0.5 * length).toFloat()
            )
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

        private fun getMultiplier(max: Int): Float {
            val a = rnd.nextInt(max - 1) + 1
            return a * a * (a * ((0.2F..1.05F).random(rnd))) / 10 / 5 + 1
        }

        /* fun connectEachTestRec(lastOne: MapGeneratorLine?, nodes: MutableList<MapNodeBuilder>) {
 //            for (i in 1 until lineNodes.size) lineNodes[i].connect(lineNodes[i - 1])
 //            for (i in lineNodes) if (i !in nodes) nodes.add(i)
 //            if (lineUp != lastOne && lineUp != null) {
 //                for (i in lineUp?.lineNodes!!.indices) lineUp!!.lineNodes[i].connect(lineNodes[i])
 //                lineUp!!.connectEachTestRec(this, nodes)
 //                lineUp?.lineNodes!![nbrOfPoints - 2].connect(lineNodes[nbrOfPoints - 1])
 //            }
 //            if (lineDown != lastOne && lineDown != null) {
 //                for (i in lineDown?.lineNodes!!.indices) lineDown!!.lineNodes[i].connect(lineNodes[i])
 //                lineDown!!.connectEachTestRec(this, nodes)
 //                lineDown?.lineNodes!![nbrOfPoints - 2].connect(lineNodes[nbrOfPoints - 1])
 //            }
             for (i in 1 until lineNodes.size) lineNodes[i].connect(lineNodes[i - 1], Direction.LEFT)
             for (i in lineNodes) if (i !in nodes) nodes.add(i)
             if (lineUp != lastOne && lineUp != null) {
                 lineUp?.connectEachTestRec(this, nodes)
                 lineUp?.lineNodes!![0].connect(lineNodes[0])
             }
             if (lineDown != lastOne && lineDown != null) {
                 lineDown?.connectEachTestRec(this, nodes)
                 lineDown?.lineNodes!![0].connect(lineNodes[0])
             }
         }*/

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
                    ) //TODO hier wieder auskommentieren und testen (für first Line, damit da die Chancen höher sind zu splitten)
                    // TODO und auslagern nicht vergessen, auf MapBuilderNode (Nicht so wichtig, aber schöner und lesbarer)
                    if (numberOfConnections > 1) {
                        createConnection(lineNodes[i], numberOfConnections - 1, this, this, nodes, true)
                    }
                }
            }
        }

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

        private fun getPossiblePointsToConnect(
            node: MapNodeBuilder,
            curLine: MapGeneratorLine,
            curDir: Direction,
            nodes: MutableList<MapNodeBuilder>
        ): List<MapNodeBuilder> {
            val posNodes: MutableList<MapNodeBuilder> = mutableListOf()
            when (curDir) {
                Direction.UP -> curLine.lineUp?.getNextXNodesWithoutSpecialConnection(node, curDir.getOpp())
                    ?.let { posNodes.addAll(it) }

                Direction.DOWN -> curLine.lineDown?.getNextXNodesWithoutSpecialConnection(node, curDir.getOpp())
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
                node.dirNodes.mapIndexedNotNull() { index, elem -> index.takeIf { elem == null } } as MutableList<Int>
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

public class Line(val start: Vector2, val end: Vector2) {
    fun angle(): Float {
        return start.sub(end).angleRad()
    }

    fun intersection(other: Line): Vector2? {
        val intersectVector: Vector2 = Vector2()
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
}

enum class Direction {
    UP {
        override fun getOpp(): Direction {
            return DOWN
        }

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine? {
            return curLine.lineUp
        }
    },
    DOWN {
        override fun getOpp(): Direction {
            return UP
        }

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine? {
            return curLine.lineDown
        }
    },
    LEFT {
        override fun getOpp(): Direction {
            return RIGHT
        }

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine {
            return curLine
        }
    },
    RIGHT {
        override fun getOpp(): Direction {
            return LEFT
        }

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine? {
            return LEFT.getOtherLine(curLine)
        }
    };

    abstract fun getOpp(): Direction;
    abstract fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine?;
}

class BezierCurve(
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
}

data class MapRestriction(
    val maxNodes: Int = 22,
    val minNodes: Int = 17,
//    val maxLines: Int = 6,
    val maxLines: Int = 3,
//    val splitProb: Float = 0.91F,
    val splitProb: Float = 0.9F,
    val compressProb: Float = 0.55F,
    val averageLengthOfLineInBetween: Float = 26F,
    val maxWidth: Int = 40,
    val maxAnglePercent: Float = 0.6F,
    val rangeToCheckBetweenNodes: Float = 70F,
    val startArea: String = "Franz",
    val endArea: String = "Huber",
    val otherAreas: List<String> = listOf(),
    val rangeToCheckBetweenAreas: Float = 100F,
    val distanceFromAreaToLine: Float = 100F,
) {

    companion object {

        fun fromOnj(onj: OnjObject): MapRestriction = MapRestriction(
            onj.get<Long>("maxNodes").toInt(),
            onj.get<Long>("minNodes").toInt(),
            onj.get<Long>("maxSplits").toInt(),
            onj.get<Double>("splitProbability").toFloat(),
            onj.get<Double>("compressProbability").toFloat(),
        )
    }
}
