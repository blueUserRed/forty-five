package com.fourinachamber.fortyfive.map.detailMap.generation

import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.map.detailMap.DetailMap
import com.fourinachamber.fortyfive.map.detailMap.MapNodeBuilder
import com.fourinachamber.fortyfive.utils.random
import onj.value.OnjObject
import kotlin.math.cos
import kotlin.math.sin

class RadialMapGenerator(val data: RadialMapGeneratorData) : BaseMapGenerator() {

    override fun generate(name: String): DetailMap {
        setup(name, data)
        val startNode = newNode(0f, 0f)

        val circles = listOf(30f, 80f, 150f)
        generateBranch(circles, startNode, randomAngle())

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

    private fun generateBranch(
        circles: List<Float>,
        startNode: MapNodeBuilder,
        firstAngle: Float,
    ) {
        val firstNode = nodeOnCircle(circles.first(), firstAngle)
        connectNodes(startNode, firstNode)
        val remainingCircles = circles.drop(1)
        if (remainingCircles.isEmpty()) return
        val angleOffset = (0.1f..0.2f).random(random)
        generateBranch(remainingCircles, firstNode, firstAngle + angleOffset)
        generateBranch(remainingCircles, firstNode, firstAngle - angleOffset)
    }

    private fun nodeOnCircle(radius: Float, angle: Float): MapNodeBuilder = newNode(
        x = sin(angle) * radius,
        y = cos(angle) * radius
    )

    private fun randomAngle(): Float = (0f..(2f * Math.PI.toFloat())).random(random)

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
