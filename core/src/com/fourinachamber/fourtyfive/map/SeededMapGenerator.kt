package com.fourinachamber.fourtyfive.map

import com.badlogic.gdx.math.Vector2
import java.lang.Float.min
import java.util.Random
import kotlin.math.pow

class SeededMapGenerator(
    val seed: Long = 102,
    val isHorizontal: Boolean = false,
    val restictions: MapRestriction = MapRestriction()
) {
    lateinit var nodes: List<MapNodeBuilder>
    val rnd: Random = Random(seed)

    companion object {
        @JvmStatic
        fun generateDef(): SeededMapGenerator {
            val r: SeededMapGenerator = SeededMapGenerator()
            r.generate()
            return r
        }
    }

    fun build(): MapNode {
        return nodes[0].build()
    }

    fun generate() {
        println("NOW TEST_OUTPUT")
        val nodes: MutableList<MapNodeBuilder> = mutableListOf()
        val size = rnd.nextInt(restictions.minSize, restictions.maxSize + 1)
        val nbrOfNodes = rnd.nextInt(restictions.minNodes, restictions.maxNodes + 1)
//        val boundary: Float = 50F
        nodes.add(MapNodeBuilder(0F, 0F))
//        nodes.add(MapNodeBuilder(boundary, 0F))
//        nodes.add(MapNodeBuilder(boundary, boundary))
//        nodes.add(MapNodeBuilder(0F, boundary))
//        nodes.add(MapNodeBuilder(0F, 0F))
        val curve: BezierCurve = BezierCurve(seed, restictions, rnd)

        val accuracy = 10
        for (i in 1 until accuracy) {
            addNodeFromCurve(
                curve,
                (min(i + rnd.nextFloat(-0.5F, 0.5F), accuracy.toFloat()) / accuracy.toFloat()),
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
//        TODO("Not yet implemented")
//    }
}

class BezierCurve(
    val seed: Long,
    private val restri: MapRestriction,
    val rnd: Random = Random(seed),
    val nbrOfPoints: Int = 20
) {
    val points: MutableList<Vector2> = mutableListOf()
    val max = (30.toFloat().pow(3 * restri.compressProb))

    init {
        points.add(Vector2(0F, 0F))
        var lastX: Float = 0F
        while (points.size < nbrOfPoints - 1) {
            lastX += rnd.nextFloat() * 25 + 4
            points.add(Vector2(lastX + 1, rnd.nextFloat() * max - max / 2))
        }
        points.add(Vector2(300F, 0F))
        println(points)
    }

    fun getPos(t: Float): Vector2 {
        val sum: Vector2 = Vector2()
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

class MapRestriction(
    var maxNodes: Int = 10,
    var minNodes: Int = 3,
    var maxSplits: Int = 4,
    var splitProb: Float = 0.25F,
    var compressProb: Float = 0.5F,
    var minSize: Int = 750,
    var maxSize: Int = 1250,
) {
}
