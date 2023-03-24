package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fourtyfive.utils.Vector2
import com.fourinachamber.fourtyfive.utils.random
import onj.value.OnjObject
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class SeededMapGenerator(
    val seed: Long = 103,
    val restictions: MapRestriction = MapRestriction()
) {
    lateinit var nodes: List<MapNodeBuilder>
    val rnd: Random = Random(seed)

    fun build(): MapNode {
        return nodes[0].build()
    }

    fun generate(): DetailMap {
        val nodes: MutableList<MapNodeBuilder> = mutableListOf()
        val nbrOfNodes = (restictions.minNodes..restictions.maxNodes).random(rnd)

        val mainLine: MapGeneratorLine = MapGeneratorLine(seed, restictions, rnd)
        mainLine.generateNextLine(false)
        mainLine.lineDown?.generateNextLine()
        mainLine.connectEachTestRec(null, nodes)
//        val lines: Array<MapGeneratorLine?> = arrayOfNulls(restictions.maxLines)
//        var cur =
//            if ((restictions.maxLines and 1) == 0)
//                (restictions.maxLines / 2 - (0..1).random(rnd))
//            else (restictions.maxLines - 1) / 2
//        lines[cur] = MapGeneratorLine(seed, restictions, rnd)
//
//        for (i in (cur - 1) downTo 1) {
//            lines[i] = MapGeneratorLine(seed, restictions, rnd, false, true)
//        }
//
//        for (i in (cur + 1)..restictions.maxLines) {
//            lines[i] = MapGeneratorLine(seed, restictions, rnd, false, false)
//        }
//        for (i in l.points) {
//            nodes.add(MapNodeBuilder(i.x, i.y))
//        }

//        for (i in 1 until 13) {
//            nodes[i].connect(nodes[i - 1])
////            nodes[i + 14].connect(nodes[i - 1 + 14])
////            if (i < 12)
////                nodes[i + 15 * 2].connect(nodes[i - 1 + 15 * 2])
//        }
        this.nodes = nodes

        println(nodes.size)
        var no = build()
        println("build")
        return DetailMap(no, listOf())
    }

    fun generateBezier(): DetailMap {
//        println("NOW TEST_OUTPUT")
        val nodes: MutableList<MapNodeBuilder> = mutableListOf()
        val nbrOfNodes = (restictions.minNodes..restictions.maxNodes).random(rnd)
//        val boundary: Float = 50F
        nodes.add(MapNodeBuilder(0F, 0F))
//        nodes.add(MapNodeBuilder(boundary, 0F))
//        nodes.add(MapNodeBuilder(boundary, boundary))
//        nodes.add(MapNodeBuilder(0F, boundary))
//        nodes.add(MapNodeBuilder(0F, 0F))
        val curve = BezierCurve(seed, restictions, rnd, nbrOfNodes)

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
//        printNodesAndNeighbours(nodes)
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
    }

    private fun printNodesAndNeighbours(nodes: MutableList<MapNodeBuilder>) {
        for (i in nodes) {
            println(i.x.toString() + " " + i.y + ": ")
            for (j in i.edgesTo) {
                println("  " + j.x.toString() + " " + j.y)
            }
            println()
        }
    }

//    private fun addNode(nodes: MutableList<MapNodeBuilder>, nbrOfNodes: Int, mapNodeBuilder: MapNodeBuilder) {
//    }


    class MapGeneratorLine(
        private val seed: Long,
        private val restrict: MapRestriction,
        private val rnd: Random = Random(seed),
        oldLine: MapGeneratorLine? = null,
        isOldLineMin: Boolean = false,
        nbrOfPoints: Int = 15,
    ) {
        //        var points: List<Vector2>
        var lineNodes: List<MapNodeBuilder> = mutableListOf()
        var lineUp: MapGeneratorLine? = null
        var lineDown: MapGeneratorLine? = null

        init {
            val points: MutableList<Vector2> = mutableListOf()
            if (oldLine != null) {
                if (isOldLineMin) {
                    lineUp = oldLine;
                } else {
                    lineDown = oldLine;
                }



            } /*else {*/
                points.add(Vector2(0, 0))
                for (i in 2 until nbrOfPoints) {
                    val length: Float = getMutliplier(5) * restrict.averageLengthOfLineInBetween
                    val angle: Float =
                        (((1 - restrict.maxAnglePercent) / 2)..(1 - (1 - restrict.maxAnglePercent) / 2)).random(rnd) * Math.PI.toFloat()
                    val posVec: Vector2 = Vector2(
                        (sin(angle.toDouble()) * length).toFloat(),
                        (cos(angle.toDouble()) * 0.5 * length).toFloat()
                    )
                    if (abs(points.last().y + posVec.y) > restrict.maxWidth / 2) {
                        posVec.y *= -1
                    }
                    points.add(
                        posVec.add(points.last())
                    )
                }
//            }

            val mu: MutableList<MapNodeBuilder> = mutableListOf()
            points.forEach() { p -> mu.add(MapNodeBuilder(p.x, p.y)) }
            this.lineNodes = mu
        }

        fun generateNextLine(generateUp: Boolean = true) {
            if (lineDown != null && lineUp == null)
                lineUp = MapGeneratorLine(seed, restrict, rnd, this, false)
            if (lineUp != null && lineDown == null)
                lineDown = MapGeneratorLine(seed, restrict, rnd, this, true)

            if (lineUp == null && lineDown == null) {
                if (generateUp) {
                    lineUp = MapGeneratorLine(seed, restrict, rnd, this, false)
                } else {
                    lineDown = MapGeneratorLine(seed, restrict, rnd, this, true)
                }
            }
        }

        fun getMutliplier(max: Int): Float {
            val a = rnd.nextInt(max - 1) + 1
            return a * a * (a * ((0.2F..1.05F).random(rnd))) / 10 / 5 + 1
        }

        fun connectEachTestRec(lastOne: MapGeneratorLine?, nodes: MutableList<MapNodeBuilder>) {
            for (i in 1 until lineNodes.size) lineNodes[i].connect(lineNodes[i - 1])
            for (i in lineNodes) if (i !in nodes) nodes.add(i)
            if (lineUp != lastOne && lineUp != null) {
                for (i in lineNodes.indices) lineUp!!.lineNodes[i].connect(lineNodes[i])
                lineUp!!.connectEachTestRec(this, nodes)
            }
            if (lineDown != lastOne && lineDown != null) {
                for (i in lineNodes.indices) lineDown!!.lineNodes[i].connect(lineNodes[i])
                lineDown!!.connectEachTestRec(this, nodes)
            }
        }
    }

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
    var maxNodes: Int = 22,
    var minNodes: Int = 15,
    var maxLines: Int = 4,
    var splitProb: Float = 0.25F,
    var compressProb: Float = 0.55F,
    var averageLengthOfLineInBetween: Float = 26F,
    var maxWidth: Int = 60,
    var maxAnglePercent: Float = 0.6F,
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
