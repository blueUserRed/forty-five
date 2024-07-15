package com.fourinachamber.fortyfive.map.detailMap.generation

import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.map.detailMap.DetailMap
import com.fourinachamber.fortyfive.map.detailMap.MapNodeBuilder
import com.fourinachamber.fortyfive.utils.minus
import com.fourinachamber.fortyfive.utils.random
import onj.value.OnjObject
import kotlin.math.cos
import kotlin.math.sin

class RadialMapGenerator(val data: RadialMapGeneratorData) : BaseMapGenerator() {

    override fun generate(name: String): DetailMap {
        setup(name, data)
        val startNode = newNode(0f, 0f)

        val circles = listOf(
            Circle(20f, 3),
            Circle(40f, 6),
            Circle(60f, 9)
        )
        val nodes = generateNodes(circles)
        connectNodes(startNode, nodes)

        startNode.build()

        return DetailMap(
            name = name,
            startNode = startNode.asNode!!,
            endNode = allNodes.last().asNode!!,
            decorations = listOf(),
            animatedDecorations = listOf(),
//            decorations = genDecorations,
//            animatedDecorations = genAnimatedDecorations,
            isArea = false,
            biome = data.biome,
            progress = 0f..10f,
            tutorialText = mutableListOf(),
            scrollable = true,
            camPosOffset = Vector2(0f, 0f)
        )
    }

    private fun generateNodes(circles: List<Circle>): List<List<MapNodeBuilder>> = circles.map { circle ->
        val amountNodes = circle.numNodes
        val anglePerNode = Math.PI * 2 / amountNodes
        val shift = (0f..(Math.PI.toFloat() * 2f)).random(random)
        val variance = -0.001f..0.001f
        val nodes = mutableListOf<MapNodeBuilder>()
        repeat(amountNodes) { i ->
            val angle = (anglePerNode * i + shift + variance.random(random)).toFloat()
            val node = nodeOnCircle(circle.radius, angle)
            nodes.add(node)
        }
        nodes
    }

    private fun connectNodes(startNode: MapNodeBuilder, nodes: List<List<MapNodeBuilder>>) {
        val circles = mutableListOf(
            listOf(startNode)
        )
        circles.addAll(nodes)
        circles.reverse()
        circles.forEachIndexed { index, circle ->
            if (index + 1 >= circles.size) return@forEachIndexed
            val innerCircle = circles[index + 1]
            circle.forEach { node ->
                var bestMatch: MapNodeBuilder? = null
                var smallestDist = Float.MAX_VALUE
                innerCircle.forEach candidateSearch@{ candidateNode ->
                    val dist = (node.posAsVec() - candidateNode.posAsVec()).len()
                    if (dist > smallestDist) return@candidateSearch
                    bestMatch = candidateNode
                    smallestDist = dist
                }
                connectNodes(node, bestMatch!!)
            }
        }
    }

    private fun nodeOnCircle(radius: Float, angle: Float): MapNodeBuilder = newNode(
        x = sin(angle) * radius,
        y = cos(angle) * radius
    )

    private fun randomAngle(): Float = (0f..(2f * Math.PI.toFloat())).random(random)

    data class Circle(
        val radius: Float,
        val numNodes: Int,
    )

    data class RadialMapGeneratorData(
        override val seed: Long,
        override val nodeProtectedArea: Float,
        val biome: String
    ) : BaseMapGeneratorData {

        companion object {

            fun fromOnj(onj: OnjObject) = RadialMapGeneratorData(
                seed = onj.get<Long>("seed"),
                nodeProtectedArea = onj.get<Double>("nodeProtectedArea").toFloat(),
                biome = onj.get<String>("biome")
            )
        }
    }

}
