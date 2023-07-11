package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
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
    private val seed: Long,
    /**
     * all possible restrictions and features which the map should have
     */
    private val restrictions: MapRestriction
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
        FortyFiveLogger.debug(logTag, "generating map $name with seed $seed")
        val nodes: MutableList<MapNodeBuilder> = generateNodesPositions()
        val connections = checkAndChangeConnectionIntersection(nodes)
        addAreas(nodes, connections)
        addEvents(nodes)    //TODO check errors with current config

//        nodes.forEach { it.scale(1F, .6F) } //TODO add parameter for scaling
        nodes.forEach { it.rotate(restrictions.rotation) }
        val decos = generateDecorations(nodes)
        this.nodes = nodes
        build()
        return DetailMap(name, mainLine.lineNodes.first().asNode!!, mainLine.lineNodes.last().asNode!!, decos)
    }

    private fun generateDecorations(
        nodes: List<MapNodeBuilder>,
    ): List<DetailMap.MapDecoration> {
        val connections = getUniqueLinesFromNodes(nodes)
        val decos: MutableList<DetailMap.MapDecoration> = mutableListOf()
        val xRange =
            ((nodes.minOf { it.x } - restrictions.decorationPadding)..(nodes.maxOf { it.x } + restrictions.decorationPadding))
        val yRange =
            ((nodes.minOf { it.y } - restrictions.decorationPadding)..(nodes.maxOf { it.y } + restrictions.decorationPadding))
        restrictions.decorations.forEach {
            decos.add(it.getDecoration(nodes, restrictions, connections, xRange, yRange))
        }
        return decos
    }//auf main pushen?, templateWidgets?

    private fun addEvents(nodes: MutableList<MapNodeBuilder>) {
        val nodesWithoutEvents = nodes.filter { a -> a.event == null }.toMutableList()
        val deadEndsWithoutEvents = nodesWithoutEvents.filter { it.edgesTo.size == 1 }.toMutableList()
        val sortedFixedEvents = restrictions.fixedEvents.sortedBy { !it.second }
        nodesWithoutEvents.removeAll(deadEndsWithoutEvents)
        for (curEvent in sortedFixedEvents) {
            if (nodesWithoutEvents.isEmpty() && deadEndsWithoutEvents.isEmpty()) break
            if ((curEvent.second && deadEndsWithoutEvents.isNotEmpty()) || nodesWithoutEvents.isEmpty()) {
                val curNode = deadEndsWithoutEvents.random(rnd)
                curNode.event = curEvent.first
                deadEndsWithoutEvents.remove(curNode)
            } else {
                val curNode = nodesWithoutEvents.random(rnd)
                curNode.event = curEvent.first
                nodesWithoutEvents.remove(curNode)
            }
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
            if (restrictions.optionalEvents.isEmpty()) {
                curNode.event = null
            } else {
                curNode.event =
                    restrictions.optionalEvents[allWeightEnds.indexOf(allWeightEnds.first { it >= rndMy })].second()
            }
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
        mainLine.lineNodes.first().imagePos = MapNode.ImagePosition.LEFT
        mainLine.lineNodes.last().imagePos = MapNode.ImagePosition.RIGHT
        mainLine.lineNodes.first().imageName = restrictions.startArea
        mainLine.lineNodes.last().imageName = restrictions.endArea
        mainLine.lineNodes.first().event = EnterMapMapEvent(restrictions.startArea, true)
        mainLine.lineNodes.last().event = EnterMapMapEvent(restrictions.endArea, false)
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
                            + restrictions.distanceFromAreaToLine * (if (direction == Direction.UP) 1 else -1)
                )
                borderNodes = getBorderNodesInArea(direction, nodes, newPos)
            } while (isIllegalPositionForArea(newPos, areaNodes))
            val newArea = MapNodeBuilder(
                0,
                newPos.x,
                newPos.y,
                imagePos = MapNode.ImagePosition.valueOf(direction.name),
                imageName = areaName,
                event = EnterMapMapEvent(areaName, false),
            )
            borderNodes.random(rnd).connect(newArea, direction)
            connections.add(Line(newPos, newPos.sub(newArea.edgesTo.first().posAsVec())))
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
    ): Boolean = areaNodes.stream()
        .anyMatch { Vector2(it.x, it.y).sub(newPos).len() < restrictions.minDistanceBetweenAreas }

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
    ): List<MapNodeBuilder> = nodes.filter {
        Vector2(it.x, it.y).sub(position)
            .len() < restrictions.distanceFromAreaToLine * (1 + restrictions.percentageForAllowedNodesInRangeBetweenLineAndArea)
                && it.dirNodes[direction.ordinal] == null && it.edgesTo.size < 4 && it.event !is EnterMapMapEvent
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
        while (true) {
            var hadErrors = false
            val uniqueNodes: MutableList<MapNodeBuilder> = getUniqueNodesFromNodeRecursive(
                nodes[0], mutableListOf()
            )
            nodes.removeIf { it !in uniqueNodes }
            val uniqueLines = getUniqueLinesFromNodes(uniqueNodes)
            while (checkLinesNotIntercepting(uniqueLines, nodes)) hadErrors = true
            if (!hadErrors) return uniqueLines
        }
    }

    private fun getUniqueLinesFromNodes(uniqueNodes: List<MapNodeBuilder>): MutableList<Line> {
        val uniqueLines = mutableListOf<Line>()
        for (node in uniqueNodes) {
            for (other in node.edgesTo) {
                if (Line(Vector2(other.x, other.y), Vector2(node.x, node.y)) !in uniqueLines) {
                    uniqueLines.add(Line(Vector2(node.x, node.y), Vector2(other.x, other.y)))
                }
            }
        }
        return uniqueLines
    }

    /**
     * returns all nodes which are connected to the first one
     */
    private fun getUniqueNodesFromNodeRecursive(
        node: MapNodeBuilder,
        checkedNodes: MutableList<MapNodeBuilder>
    ): MutableList<MapNodeBuilder> {
        for (other in node.edgesTo) {
            if (other !in checkedNodes) {
                checkedNodes.add(other)
                getUniqueNodesFromNodeRecursive(other, checkedNodes)
            }
        }
        return checkedNodes
    }

    /**
     * checks if lines are intercepting, and if they are, they either get deleted, or a node is placed at the intersection point
     */
    private fun checkLinesNotIntercepting(uniqueLines: MutableList<Line>, nodes: MutableList<MapNodeBuilder>): Boolean {
        for (i in uniqueLines.indices) {
            val line1 = uniqueLines[i]
            for (j in (i + 1) until uniqueLines.size) {
                val line2 = uniqueLines[j]
                if (!line1.sharesPointWith(line2)) {
                    val interceptPoint = line1.addOnEachEnd(restrictions.pathTotalWidth)
                        .intersection(line2.addOnEachEnd(restrictions.pathTotalWidth))
                    if (interceptPoint != null && nodes.none { a -> a.x == interceptPoint.x && a.y == interceptPoint.y }) {
                        correctInterceptionNode(nodes, uniqueLines[i], uniqueLines[j], interceptPoint, uniqueLines)
                        return true
                    }
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
            val newVec = interceptPoint.clone()
                .sub(Vector2(i.x + restrictions.pathTotalWidth / 2, i.y + restrictions.pathTotalWidth / 2))
            if (newVec.len() < restrictions.pathTotalWidth) {
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
        possibleIntersectionNode: MapNodeBuilder,
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
        return (if (rnd.nextBoolean()) line1 else line2)
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
                val a = oldLine.getMinInRange(pointToAdd.x) - restrict.minDistanceBetweenNodes
                return ((a - restrict.maxWidth)..(a))
            }
            val a = oldLine.getMaxInRange(pointToAdd.x) + restrict.minDistanceBetweenNodes
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
                return lineNodes.minBy { o1 -> o1.x - x }.y
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
            if (maxPos == Float.MIN_VALUE) {
                return lineNodes.minBy { o1 -> o1.x - x }.y
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
                                    100F
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
        ): List<MapNodeBuilder> =
            lineNodes.filter { a -> a.x > node.x }.sortedBy { a -> a.x }.subListTillMax(numberOfNodes)
                .filter { a -> a.dirNodes[direction.ordinal] == null }

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

    companion object {
        const val logTag = "MapGenerator"
    }

}

class Line(val start: Vector2, val end: Vector2) {

    fun ang(): Float {
        return Vector2(start.x, start.y).sub(end).angleRad()
    }

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

    fun getAsRect(pathTotalWidth: Float, sizeToNodeCenter: Vector2): Array<Vector2> {
        val ang = ang()
        val a: Float = (cos(ang + Math.PI / 2) * pathTotalWidth / 2).toFloat()
        val b: Float = (sin(ang + Math.PI / 2) * pathTotalWidth / 2).toFloat()
        return arrayOf(
            Vector2(start.x - a, start.y - b).add(sizeToNodeCenter),
            Vector2(start.x + a, start.y + b).add(sizeToNodeCenter),
            Vector2(end.x + a, end.y + b).add(sizeToNodeCenter),
            Vector2(end.x - a, end.y - b).add(sizeToNodeCenter),
        )
    }

    fun addOnEachEnd(distance: Float): Line {
        val ang = ang()
        val result = Line(start.clone(), end.clone())
        if (start.x < end.x) {
            result.start.x -= (cos(ang) * distance)
            result.start.y -= (sin(ang) * distance)
            result.end.x += (cos(ang) * distance)
            result.end.y += (sin(ang) * distance)
        } else {
            result.start.x += (cos(ang) * distance)
            result.start.y += (sin(ang) * distance)
            result.end.x -= (cos(ang) * distance)
            result.end.y -= (sin(ang) * distance)
        }
        return result
    }

    fun sharesPointWith(line2: Line): Boolean {
        return line2.start == start || line2.start == end || line2.end == start || line2.end == end
    }
}

enum class Direction {
    UP {
        override fun getOpposite(): Direction = DOWN

        override fun getAngle(): Float = Math.PI.toFloat() / 2


        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine? {
            return curLine.lineUp
        }

        override fun getNextDirCounterClock(): Direction = LEFT
    },
    DOWN {
        override fun getOpposite(): Direction = UP

        override fun getAngle(): Float = Math.PI.toFloat() * 3 / 2

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine? {
            return curLine.lineDown
        }

        override fun getNextDirCounterClock(): Direction = RIGHT
    },
    LEFT {
        override fun getOpposite(): Direction = RIGHT

        override fun getAngle(): Float = Math.PI.toFloat()

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine {
            return curLine
        }

        override fun getNextDirCounterClock(): Direction = DOWN
    },
    RIGHT {
        override fun getOpposite(): Direction = LEFT

        override fun getAngle(): Float = 0F

        override fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine? {
            return LEFT.getOtherLine(curLine)
        }

        override fun getNextDirCounterClock(): Direction = UP

    };

    abstract fun getOpposite(): Direction
    abstract fun getAngle(): Float

    /**
     * returns the line from the opposite direction
     */
    abstract fun getOtherLine(curLine: SeededMapGenerator.MapGeneratorLine): SeededMapGenerator.MapGeneratorLine?
    abstract fun getNextDirCounterClock(): Direction
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

sealed class DecorationDistributionFunction(
    protected val seed: Long,
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
        seed: Long,
        type: String,
        density: Float,
        baseWidth: Float,
        baseHeight: Float,
        scaleMin: Float,
        scaleMax: Float,
        collidesOnlyWithNodes: Boolean
    ) : DecorationDistributionFunction(
        seed,
        type,
        density,
        baseWidth,
        baseHeight,
        scaleMin,
        scaleMax,
        collidesOnlyWithNodes
    ) {
        override fun getPossiblePositions(
            xRange: ClosedFloatingPointRange<Float>,
            yRange: ClosedFloatingPointRange<Float>,
            restrict: MapRestriction,
            rnd: kotlin.random.Random,
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


    class SingleCluster(
        seed: Long,
        type: String,
        density: Float,
        baseWidth: Float,
        baseHeight: Float,
        scaleMin: Float,
        scaleMax: Float,
        collidesOnlyWithNodes: Boolean,
        private val minCenterX: Float?,
        private val maxCenterX: Float?,
        private val minCenterY: Float?,
        private val maxCenterY: Float?,
        private val innerRadius: Float,
        private val outerRadius: Float,
        private val nbrOfInnerPoints: Int,
        private val nbrOfOuterPoints: Int,
    ) : DecorationDistributionFunction(
        seed,
        type,
        density,
        baseWidth,
        baseHeight,
        scaleMin,
        scaleMax,
        collidesOnlyWithNodes
    ) {
        override fun getPossiblePositions(
            xRange: ClosedFloatingPointRange<Float>,
            yRange: ClosedFloatingPointRange<Float>,
            restrict: MapRestriction,
            rnd: kotlin.random.Random,
        ): List<Vector2> {
            val positions: MutableList<Vector2> = mutableListOf()
            val center = getCenter(xRange, yRange, rnd)

            val borders = sequence {
                for (i in 0 until nbrOfOuterPoints) {
                    val ang: Double = (rnd.nextDouble() / nbrOfOuterPoints + (i + 0.0) / nbrOfOuterPoints) * Math.PI * 2
                    val len = rnd.nextDouble() * (outerRadius - innerRadius) + innerRadius
                    yield(Vector2((center.x + cos(ang) * len).toFloat(), (center.y + sin(ang) * len).toFloat()))
                }
            }.take(nbrOfInnerPoints).toList().toTypedArray()

            val centers = sequence {
                for (i in 0 until nbrOfInnerPoints) {
                    val ang: Double = (rnd.nextDouble() / nbrOfOuterPoints + (i + 0.0) / nbrOfOuterPoints) * Math.PI * 2
                    val len = rnd.nextDouble() * (innerRadius)
                    yield(Vector2((center.x + cos(ang) * len).toFloat(), (center.y + sin(ang) * len).toFloat()))
                }
            }.take(nbrOfInnerPoints).toList().toTypedArray()

            for (i in 0 until ((outerRadius * outerRadius * PI) / (baseWidth * baseHeight) * density).toInt()) {
                val ang = rnd.nextDouble() * Math.PI * 2
                val len =
                    sqrt(rnd.nextDouble(outerRadius * outerRadius.toDouble())) //TODO maybe log or so for better distribution
                val posPos = Vector2(
                    (center.x + cos(ang) * len).toFloat(),
                    (center.y + sin(ang) * len).toFloat()
                )
                val distances: Array<Float> = isLegalPos(posPos, centers, borders)
                if (distances.isNotEmpty()) {
                    if (rnd.nextDouble() < max(-distances[0] / distances[1] + 1.4, 1.0))
                        positions.add(posPos)
                }
            }
            return positions
        }

        private fun isLegalPos(posPos: Vector2, centers: Array<Vector2>, borders: Array<Vector2>): Array<Float> {
            val closestCenter = centers.minOf { posPos.clone().sub(it).len() }
            var closestBorder = Float.MAX_VALUE
            for (border in borders) {
                val dist: Float = posPos.clone().sub(border).len()
                if (dist < closestCenter) return arrayOf()
                if (dist < closestBorder) closestBorder = dist
            }
            return arrayOf(closestCenter, closestBorder)
        }

        private fun getCenter(
            xRange: ClosedFloatingPointRange<Float>,
            yRange: ClosedFloatingPointRange<Float>,
            rnd: kotlin.random.Random
        ): Vector2 {
            val center = Vector2()
            val minX = minCenterX ?: (xRange.start + innerRadius)
            val minY = minCenterY ?: (yRange.start + innerRadius)
            val maxX = maxCenterX ?: (xRange.endInclusive - innerRadius)
            val maxY = maxCenterY ?: (yRange.endInclusive - innerRadius)
            if (minX < maxX) center.x = (minX..maxX).random(rnd)
            else center.x = xRange.random(rnd)
            if (minX < maxX) center.y = (minY..maxY).random(rnd)
            else center.y = yRange.random(rnd)
            return center
        }

    }


    class MultiCluster(
        seed: Long,
        type: String,
        density: Float,
        baseWidth: Float,
        baseHeight: Float,
        scaleMin: Float,
        scaleMax: Float,
        collidesOnlyWithNodes: Boolean,
        private val blockSize: Float,
        private val prob: Float,
        private val additionalProbIfNeighbor: Float
    ) : DecorationDistributionFunction(
        seed,
        type,
        density,
        baseWidth,
        baseHeight,
        scaleMin,
        scaleMax,
        collidesOnlyWithNodes
    ) {
        override fun getPossiblePositions(
            xRange: ClosedFloatingPointRange<Float>,
            yRange: ClosedFloatingPointRange<Float>,
            restrict: MapRestriction,
            rnd: kotlin.random.Random,
        ): List<Vector2> {
            val positions: MutableList<Vector2> = mutableListOf()
            val pointsToTry: Int = ((xRange.endInclusive - xRange.start) / baseWidth
                    * (yRange.endInclusive - yRange.start) / baseHeight * density).toInt()
            val width = xRange.endInclusive - xRange.start
            val height = yRange.endInclusive - yRange.start
            val size: Int = max(width / blockSize, height / blockSize).toInt() + 1
            //TODO maybe separate into 2 variables to be more efficient
            val all: Array<MyBlock> = sequence {
                for (i in 0 until (size * size)) {
                    yield(
                        MyBlock(
                            Vector2(
                                (i / size * blockSize + rnd.nextDouble() * blockSize).toFloat(),
                                (i % size * blockSize + rnd.nextDouble() * blockSize).toFloat()
                            )
                        )
                    )
                }
            }.toList().toTypedArray()

            val allPos: MutableList<Int> = ArrayList()
            for (i in all.indices) allPos.add(i)

            while (allPos.size > 0) {
                val i = allPos[(rnd.nextDouble() * allPos.size).toInt()]
                all[i].setIsCluster(
                    rnd.nextDouble() < prob + (if (isNeighborCluster(
                            all,
                            i,
                            blockSize
                        )
                    ) additionalProbIfNeighbor else 0F)
                )
                allPos.remove(i)
            }
            for (i in 0 until pointsToTry) {
                val pos = Vector2((rnd.nextDouble() * width).toFloat(), (rnd.nextDouble() * height).toFloat())
                if (isPlaceable(pos, all)) {
                    positions.add(pos.add(xRange.start, yRange.start))
                }
            }
            return positions
        }

        private fun isNeighborCluster(all: Array<MyBlock>, i: Int, blockSize: Float): Boolean {
            for (b in all) {
                if (b.isCluster && b.pos.clone().sub(all[i].pos).len() < blockSize * 2) {
                    return true
                }
            }
            return false
        }

        private fun isPlaceable(pos: Vector2, all: Array<MyBlock>): Boolean {
            var curDist = Float.MAX_VALUE
            var curIsForest = false
            for (b in all) {
                val cur: Float = b.pos.clone().sub(pos).len()
                if (cur < curDist) {
                    curDist = cur
                    curIsForest = b.isCluster
                }
            }
            return curIsForest
        }

        internal class MyBlock(var pos: Vector2) {
            var isCluster = false
            fun setIsCluster(isForest: Boolean) {
                this.isCluster = isForest
            }
        }
    }

    abstract fun getPossiblePositions(
        xRange: ClosedFloatingPointRange<Float>,
        yRange: ClosedFloatingPointRange<Float>,
        restrict: MapRestriction,
        rnd: kotlin.random.Random,
    ): List<Vector2>

    fun getDecoration(
        nodes: List<MapNodeBuilder>,
        restrictions: MapRestriction,
        connections: MutableList<Line>,
        xRange: ClosedFloatingPointRange<Float>,
        yRange: ClosedFloatingPointRange<Float>
    ): DetailMap.MapDecoration {
        val possiblePositions: List<Pair<Vector2, Float>> =
            getPossiblePositions(xRange, yRange, restrictions, rnd)
                .filter { xRange.contains(it.x) && yRange.contains(it.y) }
                .map { it to (scaleMin..scaleMax).random(rnd) }
        return DetailMap.MapDecoration(
            type,
            baseWidth,
            baseHeight,
            possiblePositions
                .filter { isPossibleToPlaceNode(it, nodes, connections, restrictions.pathTotalWidth) }
                .sortedBy { -it.first.y }
        )
    }

    private fun isPossibleToPlaceNode(
        data: Pair<Vector2, Float>,
        nodes: List<MapNodeBuilder>,
        connections: MutableList<Line>,
        pathTotalWidth: Float
    ): Boolean {

        val rect = data.first to Vector2(baseWidth * data.second, baseHeight * data.second)
        for (it in nodes) {
            val tempRect = it.posAsVec() to it.sizeAsVec()
            if ((rect.first.x < tempRect.first.x + tempRect.second.x) &&
                (rect.first.y < tempRect.first.y + tempRect.second.y) &&
                (tempRect.first.x < rect.first.x + rect.second.x) &&
                (tempRect.first.y < rect.first.y + rect.second.y)
            ) {
                return false
            }
        }
        if (!collidesOnlyWithNodes) {
            val size = Vector2(baseWidth * data.second, baseHeight * data.second)
            val rectAbs = arrayOf(
                Vector2(data.first.x, data.first.y),
                Vector2(data.first.x, data.first.y).add(size.x, 0F),
                Vector2(data.first.x, data.first.y).add(size.x, size.y),
                Vector2(data.first.x, data.first.y).add(0F, size.y)
            )
            for (it in connections) {
                val tempRect = it.getAsRect(pathTotalWidth, Vector2(2.5F, 2.5F))
                if (isPolygonsIntersecting(rectAbs, tempRect)) return false
            }
        }
        return true
    }

    private fun isPolygonsIntersecting(a: Array<Vector2>, b: Array<Vector2>): Boolean {
        for (rect in arrayOf(a, b)) {
            for (i1 in rect.indices) {
                val i2: Int = (i1 + 1) % rect.size
                val p1: Vector2 = rect.get(i1)
                val p2: Vector2 = rect.get(i2)
                val normal = Vector2(p2.y - p1.y, p1.x - p2.x)
                var minA = Float.POSITIVE_INFINITY
                var maxA = Float.NEGATIVE_INFINITY
                for (p in a) {
                    val projected: Float = normal.x * p.x + normal.y * p.y
                    if (projected < minA) minA = projected
                    if (projected > maxA) maxA = projected
                }
                var minB = Float.POSITIVE_INFINITY
                var maxB = Float.NEGATIVE_INFINITY
                for (p in b) {
                    val projected: Float = normal.x * p.x + normal.y * p.y
                    if (projected < minB) minB = projected
                    if (projected > maxB) maxB = projected
                }
                if (maxA < minB || maxB < minA) return false
            }
        }
        return true
    }
}

object DecorationDistributionFunctionFactory {

    private val functions: Map<String, (OnjNamedObject, Long) -> DecorationDistributionFunction> = mapOf(
        "RandomDistributionFunction" to { onj, seed ->
            DecorationDistributionFunction.Random(
                seed,
                onj.get<String>("decoration"),
                onj.get<Double>("density").toFloat(),
                onj.get<Double>("baseWidth").toFloat(),
                onj.get<Double>("baseHeight").toFloat(),
                onj.get<Double>("scaleMin").toFloat(),
                onj.get<Double>("scaleMax").toFloat(),
                onj.get<Boolean>("onlyCollidesWithNodes"),
            )
        },
        "SingleClusterDistributionFunction" to { onj, seed ->
            DecorationDistributionFunction.SingleCluster(
                seed,
                onj.get<String>("decoration"),
                onj.get<Double>("density").toFloat(),
                onj.get<Double>("baseWidth").toFloat(),
                onj.get<Double>("baseHeight").toFloat(),
                onj.get<Double>("scaleMin").toFloat(),
                onj.get<Double>("scaleMax").toFloat(),
                onj.get<Boolean>("onlyCollidesWithNodes"),
                onj.get<Double?>("minCenterX")?.toFloat(),
                onj.get<Double?>("maxCenterX")?.toFloat(),
                onj.get<Double?>("minCenterY")?.toFloat(),
                onj.get<Double?>("maxCenterY")?.toFloat(),
                onj.get<Double>("innerRadius").toFloat(),
                onj.get<Double>("outerRadius").toFloat(),
                onj.get<Long>("nbrOfInnerPoints").toInt(),
                onj.get<Long>("nbrOfOuterPoints").toInt(),
            )
        },
        "MultiClusterDistributionFunction" to { onj, seed ->
            DecorationDistributionFunction.MultiCluster(
                seed,
                onj.get<String>("decoration"),
                onj.get<Double>("density").toFloat(),
                onj.get<Double>("baseWidth").toFloat(),
                onj.get<Double>("baseHeight").toFloat(),
                onj.get<Double>("scaleMin").toFloat(),
                onj.get<Double>("scaleMax").toFloat(),
                onj.get<Boolean>("onlyCollidesWithNodes"),
                onj.get<Double>("blockSize").toFloat(),
                onj.get<Double>("prob").toFloat(),
                onj.get<Double>("additionalProbIfNeighbor").toFloat(),
            )
        },
    )

    fun get(onj: OnjNamedObject, seed: Long): DecorationDistributionFunction =
        functions[onj.name]?.invoke(onj, seed)
            ?: throw RuntimeException("unknown decoration distribution function: ${onj.name}")
}

/**
 * all possible restrictions within the MapGenerator
 */
data class MapRestriction(
    /**
     * minimum number of nodes for main line
     */
    val maxNodes: Int,
    /**
     * maximum number of nodes for main line
     */
    val minNodes: Int,
    /**
     * how many lines are generated and are therefore possible
     */
    val maxLines: Int,
    /**
     * how likely it is for nodes to split (min. of 0.3 is recommended)
     */
    val splitProb: Float,
    /**
     * how far it spreads into the "y" direction or is compressed
     */
    val compressProb: Float,
    /**
     * the average length between two nodes on one line (actually only preferred length, not avg.)
     */
    val averageLengthOfLineInBetween: Float,

    /**
     * max "y" width for first line, and for other lines: max distance for the x Point from one line to the x Point of another line
     */
    val maxWidth: Int,
    /**
     * how strong the random points can go up and down (0 means straight to the right, 1 means 180 Degree)
     */
    val maxAnglePercent: Float,
    /**
     * the range from where nodes are checked if there are any other from another line
     */
    val rangeToCheckBetweenNodes: Float,
    val startArea: String,
    val endArea: String,
    val otherAreas: List<String>,
    val minDistanceBetweenAreas: Float,
    /**
     * how far the areas are from the highest/lowest point of the road in a close area around the area
     */
    val distanceFromAreaToLine: Float,

    /**
     * how far the nodes can be away from the area to be selected as the connected node to that area (formula [MapRestriction.distanceFromAreaToLine] * (1+thisValue)
     */
    val percentageForAllowedNodesInRangeBetweenLineAndArea: Float,
    /**
     * the rotation of the road (0 means looking right, PI/2 means looking up, and so on)
     */
    val rotation: Double,

    /**
     * the boolean means if it is a dead End
     */
    val fixedEvents: List<Pair<MapEvent, Boolean>>,
    val optionalEvents: List<Pair<Int, () -> MapEvent>>,
    val decorations: List<DecorationDistributionFunction>,// = listOf(
    val decorationPadding: Float, //TODO 4 parameters instead of 1 (each direction)
    val pathTotalWidth: Float = 7F,
    val minDistanceBetweenNodes: Float = 5F,
) {


    companion object {

        fun fromOnj(onj: OnjObject): MapRestriction = MapRestriction(
            maxNodes = onj.get<Long>("maxNodes").toInt(),
            minNodes = onj.get<Long>("minNodes").toInt(),
            maxLines = onj.get<Long>("maxSplits").toInt(),
            splitProb = onj.get<Double>("splitProbability").toFloat(),
            compressProb = onj.get<Double>("compressProbability").toFloat(),
            averageLengthOfLineInBetween = onj.get<Double>("averageLengthOfLineInBetween").toFloat(),
            decorationPadding = onj.get<Double>("decorationPadding").toFloat(),
            distanceFromAreaToLine = onj.get<Double>("distanceFromAreaToLine").toFloat(),
            endArea = onj.get<String>("endArea"),
            startArea = onj.get<String>("startArea"),
            maxAnglePercent = onj.get<Double>("maxAnglePercent").toFloat(),
            maxWidth = onj.get<Long>("maxWidth").toInt(),
            minDistanceBetweenAreas = onj.get<Double>("minDistanceBetweenAreas").toFloat(),
            rangeToCheckBetweenNodes = onj.get<Double>("rangeToCheckBetweenNodes").toFloat(),
            percentageForAllowedNodesInRangeBetweenLineAndArea =
            onj.get<Double>("percentageForAllowedNodesInRangeBetweenLineAndArea").toFloat(),
            rotation = onj.get<Double>("rotation"),
            otherAreas = onj.get<OnjArray>("otherAreas").value.map { it.value as String },
            fixedEvents = onj
                .get<OnjArray>("fixedEvents")
                .value.map { it as OnjObject }
                .map { MapEventFactory.getMapEvent(it.get<OnjNamedObject>("event")) to it.get<Boolean>("isDeadEnd") },
            optionalEvents = onj
                .get<OnjArray>("optionalEvents")
                .value
                .map { it as OnjObject }
                .map {
                    it.get<Long>("weight").toInt() to
                            { MapEventFactory.getMapEvent(it.get<OnjNamedObject>("event")) }
                },
            decorations = onj
                .get<OnjArray>("decorations")
                .value
                .mapIndexed { index, decoration ->
                    decoration as OnjNamedObject
                    DecorationDistributionFunctionFactory.get(
                        decoration,
                        onj.get<Long>("decorationSeed") * 289708 * index
                    )
                },
            minDistanceBetweenNodes = onj.get<Double>("minDistanceBetweenNodes").toFloat(),
            pathTotalWidth = onj.get<Double>("pathTotalWidth").toFloat(),
        )
    }
}

