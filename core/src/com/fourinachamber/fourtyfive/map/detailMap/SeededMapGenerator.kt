package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fourtyfive.utils.Vector2
import com.fourinachamber.fourtyfive.utils.random
import onj.value.OnjObject
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.pow
import kotlin.random.Random

class SeededMapGenerator(
    val seed: Long = 102,
//    val isHorizontal: Boolean = false,
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
        val boundary: Float = 50F
        nodes.add(MapNodeBuilder(0F, -boundary / 2))
        nodes.add(MapNodeBuilder(boundary, -boundary / 2))
        nodes.add(MapNodeBuilder(boundary, boundary / 2))
        nodes.add(MapNodeBuilder(0F, boundary / 2))
        nodes.add(MapNodeBuilder(0F, -boundary / 2))

        val l = MapGeneratorLine(seed, MapRestriction())
        for (i in l.points) {
            nodes.add(MapNodeBuilder(i.x, i.y))
        }
        for (i in 1 until nodes.size) {
            nodes[i].connect(nodes[i - 1])
        }
        this.nodes = nodes
        return DetailMap(build(), listOf())
    }


    fun generateBezier(): DetailMap {
        println("NOW TEST_OUTPUT")
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
}

class MapGeneratorLine(
    private val seed: Long,
    restrict: MapRestriction,
    val rnd: Random = Random(seed),
    nbrOfPoints: Int = 15,
    border: Int = 50,
) {
    lateinit var points: List<Vector2>

    init {
        val points: MutableList<Vector2> = mutableListOf()

        points.add(Vector2(0, -50))
        for (i in 2 until nbrOfPoints) {
            val length: Float = getMutliplier(5) * restrict.averageLengthOfLineInBetween
            val angle: Float = (0F..(Math.PI * 0.95).toFloat()).random(rnd)
            val posVec: Vector2 = Vector2(
                points.last().x + ((Math.cos(angle.toDouble())
                    .pow(2)).toFloat()) * length,
                points.last().y + ((Math.sin(angle.toDouble())
                    .pow(2)).toFloat()) * length * (if (Math.sin(angle.toDouble()) < 0) -1 else 1)
            )
            points.add(
                posVec
            )
        }
        this.points = points.toList()
    }

    fun getMutliplier(max: Int): Float {
        val a = rnd.nextInt(max - 1) + 1
        return a * a * (a * ((0.2F..1.05F).random(rnd))) / 10 / 5 + 1
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
        println(points)
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
    var maxSplits: Int = 4,
    var splitProb: Float = 0.25F,
    var compressProb: Float = 0.55F,
    var averageLengthOfLineInBetween: Float = 26F,
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