package com.fourinachamber.fortyfive.map.detailMap.generation

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.map.detailMap.*
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.random.Random

abstract class BaseMapGenerator {

    private val _allNodes: MutableList<MapNodeBuilder> = mutableListOf()
    protected val allNodes: List<MapNodeBuilder>
        get() = _allNodes

    protected lateinit var random: Random
        private set
    private lateinit var data: BaseMapGeneratorData
    private lateinit var name: String

    private lateinit var bounds: Rectangle

    private val nodeColliders: MutableList<Rectangle> = mutableListOf()
    private val decorationColliders: MutableList<Rectangle> = mutableListOf()
    private val lineColliders: MutableList<Line2D> = mutableListOf()

    abstract fun generate(name: String): DetailMap

    protected fun setup(name: String, data: BaseMapGeneratorData) {
        this.random = Random(data.seed)
        this.data = data
        this.name = name
        nodeColliders.clear()
        decorationColliders.clear()
        lineColliders.clear()
    }

    protected fun setupBounds(horizontalExtension: Float, verticalExtension: Float) {
        val minX = allNodes.minOf { it.x }
        val maxX = allNodes.maxOf { it.x }
        val minY = allNodes.minOf { it.y }
        val maxY = allNodes.maxOf { it.y }
        bounds = Rectangle(
            minX - horizontalExtension,
            minY - verticalExtension,
            maxX - minX + horizontalExtension * 2,
            maxY - minY + verticalExtension * 2
        )
    }

    protected fun addNodeCollider(collider: Rectangle) {
        nodeColliders.add(collider)
    }

    protected fun generateDecoration(decoration: MapGeneratorDecoration): DetailMap.MapDecoration {
        val dist = decoration.distribution(bounds, random).iterator()
        val instances = mutableListOf<Pair<Vector2, Float>>()
        val targetAmount = (decoration.density * bounds.area()).toInt()

        fun checkCollision(collider: Rectangle, other: Rectangle): Boolean = if (decoration.onlyCheckCollisionsAtSpawnPoints) {
            other.contains(Vector2(collider.x, collider.y))
        } else {
            other.overlaps(collider)
        }

        fun checkCollision(collider: Rectangle, other: Line2D): Boolean = if (decoration.onlyCheckCollisionsAtSpawnPoints) {
            false
        } else {
            other.intersects(collider)
        }

        val maxIts = targetAmount * 3
        var iteration = 0
        var spawned = 0
        while (spawned <= targetAmount) {
            if (iteration > maxIts) {
                FortyFiveLogger.warn(logTag, "MaxIts reached when spawning decoration ${decoration.decoration} in map $name")
                break
            }
            iteration++
            if (!dist.hasNext()) break
            val pos = dist.next()
            val scale = decoration.scale.random()
            val shrinkWidth = decoration.baseWidth * scale * decoration.shrinkBoundsWidth
            val shrinkHeight = decoration.baseHeight * scale * decoration.shrinkBoundsHeight
            val thisCollision = Rectangle(
                pos.x + shrinkWidth / 2,
                pos.y + shrinkHeight / 2,
                decoration.baseWidth * scale - shrinkWidth,
                decoration.baseHeight * scale - shrinkHeight
            )
            if (decoration.checkNodeCollisions && nodeColliders.any { checkCollision(thisCollision, it) }) continue
            if (decoration.checkDecorationCollisions && decorationColliders.any { checkCollision(thisCollision, it) }) continue
            if (decoration.checkLineCollisions && lineColliders.any { checkCollision(thisCollision, it) }) continue
            if (decoration.generateDecorationCollisions) decorationColliders.add(thisCollision)
            instances.add(pos to scale)
            spawned++
        }
        if (decoration.sortByY) instances.sortByDescending { it.first.y }
        return DetailMap.MapDecoration(
            decoration.decoration,
            decoration.baseWidth,
            decoration.baseHeight,
            false,
            instances
        )
    }

    protected fun newNode(
        x: Float = 0f,
        y: Float = 0f,
    ): MapNodeBuilder = MapNodeBuilder(
        index = _allNodes.size,
        x = x,
        y = y
    ).also {
        _allNodes.add(it)
        val width = data.nodeProtectedArea
        val halfWidth = width / 2
        nodeColliders.add(Rectangle(x - halfWidth, y - halfWidth, width, width))
    }

    protected fun setupExitNode(node: MapNodeBuilder, area: String) {
        node.event = EnterMapMapEvent(area)
        node.imageName = area
        node.nodeTexture = data.exitNodeTexture
    }

    protected fun connectNodes(node1: MapNodeBuilder, node2: MapNodeBuilder) {
        node1.connect(node2)
        lineColliders.add(Line2D(Vector2(node1.x, node1.y), Vector2(node2.x, node2.y)))
    }

    interface BaseMapGeneratorData {
        val seed: Long
        val nodeProtectedArea: Float
        val locationSignProtectedAreaWidth: Float
        val locationSignProtectedAreaHeight: Float
        val startArea: String
        val exitNodeTexture: String
        val progress: ClosedFloatingPointRange<Float>
    }

    data class MapGeneratorDecoration(
        val distribution: (bounds: Rectangle, random: Random) -> Sequence<Vector2>,
        val decoration: String,
        val baseWidth: Float,
        val baseHeight: Float,
        val density: Float,
        val checkNodeCollisions: Boolean,
        val checkLineCollisions: Boolean,
        val checkDecorationCollisions: Boolean,
        val generateDecorationCollisions: Boolean,
        val onlyCheckCollisionsAtSpawnPoints: Boolean,
        val scale: ClosedFloatingPointRange<Float>,
        val shrinkBoundsWidth: Float,
        val shrinkBoundsHeight: Float,
        val sortByY: Boolean,
        val animated: Boolean,
    ) {

        companion object {

            fun fromOnj(onj: OnjObject): MapGeneratorDecoration = MapGeneratorDecoration(
                DecorationDistribution.fromOnj(onj.get<OnjNamedObject>("distribution")),
                onj.get<String>("decoration"),
                onj.get<Double>("baseWidth").toFloat(),
                onj.get<Double>("baseHeight").toFloat(),
                onj.get<Double>("density").toFloat(),
                onj.get<Boolean>("checkNodeCollisions"),
                onj.get<Boolean>("checkLineCollisions"),
                onj.get<Boolean>("checkDecorationCollisions"),
                onj.get<Boolean>("generateDecorationCollisions"),
                onj.get<Boolean>("onlyCheckCollisionsAtSpawnPoints"),
                onj.get<OnjArray>("scale").toFloatRange(),
                onj.getOr<Double>("shrinkBoundsWidth", 0.0).toFloat(),
                onj.getOr<Double>("shrinkBoundsHeight", 0.0).toFloat(),
                onj.get<Boolean>("sortByY"),
                onj.get<Boolean>("animated"),
            )
        }
    }

    object DecorationDistribution {

        fun random(bounds: Rectangle, random: Random): Sequence<Vector2> = repeatingSequenceOf { Vector2(
            (bounds.x..(bounds.x + bounds.width)).random(random),
            (bounds.y..(bounds.y + bounds.height)).random(random),
        ) }

        fun fadeX(
            bounds: Rectangle,
            random: Random,
            start: Float,
            end: Float,
            interpolation: Interpolation
        ): Sequence<Vector2> = repeatingSequenceOf {
            val x = interpolation.apply(start, end, random.nextFloat())
            val y = (bounds.y..(bounds.y + bounds.height)).random(random)
            Vector2(x, y)
        }

        fun fromOnj(
            onj: OnjNamedObject
        ): (bounds: Rectangle, random: Random) -> Sequence<Vector2> = when (val name = onj.name) {
            "Random" -> DecorationDistribution::random
            "FadeX" -> { bounds, random -> fadeX(
                bounds, random,
                onj.get<Double>("start").toFloat(),
                onj.get<Double>("end").toFloat(),
                onj.get<Interpolation>("interpolation")
            ) }
            else -> throw RuntimeException("unknown decoration distribution function $name")
        }
    }

    companion object {

        const val logTag = "NewMapGenerator"

        fun fromOnj(onj: OnjNamedObject): BaseMapGenerator = when (val name = onj.name) {
            "ThreeLine" -> ThreeLineMapGenerator(ThreeLineMapGenerator.ThreeLineMapGeneratorData.fromOnj(onj))
            "Radial" -> RadialMapGenerator(RadialMapGenerator.RadialMapGeneratorData.fromOnj(onj))
            else -> throw RuntimeException("unknown MapGenerator: $name")
        }

    }

}
