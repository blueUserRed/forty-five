package com.microwavestudios.fortyfive.map.generation

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.map.EnterMapMapEvent
import com.microwavestudios.fortyfive.map.MapNode
import com.microwavestudios.fortyfive.map.MapNodeBuilder
import com.microwavestudios.fortyfive.onjNamespaces.OnjInterpolation
import com.microwavestudios.fortyfive.utils.*
import onj.builder.OnjObjectBuilderDSL
import onj.builder.buildOnjObject
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

    protected lateinit var bounds: Rectangle
        private set

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
        val dist = decoration.distribution.get(bounds, random).iterator()
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
                FortyFive.logger.warn(logTag, "MaxIts reached when spawning decoration ${decoration.decoration} in map $name")
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

    protected fun doNodeImage(node: MapNodeBuilder, imagePosition: MapNode.ImagePosition) {
        node.imagePos = imagePosition
        val width = data.locationSignProtectedAreaWidth
        val height = data.locationSignProtectedAreaHeight
        val bounds = when (imagePosition) {
            MapNode.ImagePosition.LEFT -> Rectangle(
                node.x - width,
                node.y - height / 2f,
                width, height
            )
            MapNode.ImagePosition.RIGHT -> Rectangle(
                node.x,
                node.y - height / 2f,
                width, height
            )
            MapNode.ImagePosition.UP -> Rectangle(
                node.x - width / 2f,
                node.y,
                width, height
            )
            MapNode.ImagePosition.DOWN -> Rectangle(
                node.x - width / 2f,
                node.y - height,
                width, height
            )
        }
        addNodeCollider(bounds)
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
        node.event = EnterMapMapEvent(area, true)
        node.imageName = area
        node.nodeTexture = data.exitNodeTexture
    }

    protected fun connectNodes(node1: MapNodeBuilder, node2: MapNodeBuilder) {
        node1.connect(node2)
        lineColliders.add(Line2D(Vector2(node1.x, node1.y), Vector2(node2.x, node2.y)))
    }

    abstract fun asOnj(): OnjObject

    interface BaseMapGeneratorData {
        val seed: Long
        val nodeProtectedArea: Float
        val locationSignProtectedAreaWidth: Float
        val locationSignProtectedAreaHeight: Float
        val startArea: String
        val exitNodeTexture: String

        fun asOnj(): OnjObject

        fun OnjObjectBuilderDSL.includeBaseData() {
            "seed" with seed
            "nodeProtectedArea" with nodeProtectedArea
            "locationSignProtectedAreaWidth" with locationSignProtectedAreaWidth
            "locationSignProtectedAreaHeight" with locationSignProtectedAreaHeight
            "startArea" with startArea
            "exitNodeTexture" with exitNodeTexture
        }
    }

    data class MapGeneratorDecoration(
        val distribution: DecorationDistribution,
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

        fun asOnj(): OnjObject = buildOnjObject {
            "distribution" with distribution.asOnj()
            "decoration" with decoration
            "baseWidth" with baseWidth
            "baseHeight" with baseHeight
            "density" with density
            "checkNodeCollisions" with checkNodeCollisions
            "checkLineCollisions" with checkLineCollisions
            "checkDecorationCollisions" with checkDecorationCollisions
            "generateDecorationCollisions" with generateDecorationCollisions
            "onlyCheckCollisionsAtSpawnPoints" with onlyCheckCollisionsAtSpawnPoints
            "scale" with arrayOf(scale.start, scale.endInclusive)
            "shrinkBoundsWidth" with shrinkBoundsWidth
            "shrinkBoundsHeight" with shrinkBoundsHeight
            "sortByY" with sortByY
            "animated" with animated
        }

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

    sealed class DecorationDistribution {

        abstract fun get(bounds: Rectangle, random: Random): Sequence<Vector2>

        abstract fun asOnj(): OnjObject

        data object RandomDistribution : DecorationDistribution() {

            override fun asOnj(): OnjObject = buildOnjObject {
                name("Random")
            }

            override fun get(bounds: Rectangle, random: Random): Sequence<Vector2> = repeatingSequenceOf {
                Vector2(
                    (bounds.x..(bounds.x + bounds.width)).random(random),
                    (bounds.y..(bounds.y + bounds.height)).random(random),
                )
            }

        }

        data class FadeX(
            val start: Float,
            val end: Float,
            val interpolation: Interpolation
        ) : DecorationDistribution() {

            override fun asOnj(): OnjObject = buildOnjObject {
                name("FadeX")
                "start" with start
                "end" with end
                "interpolation" with OnjInterpolation(interpolation)
            }

            override fun get(bounds: Rectangle, random: Random): Sequence<Vector2> = repeatingSequenceOf {
                val x = interpolation.apply(start, end, random.nextFloat())
                val y = (bounds.y..(bounds.y + bounds.height)).random(random)
                Vector2(x, y)
            }

            companion object {

                fun fromOnj(onj: OnjObject): FadeX = FadeX(
                    onj.get<Double>("start").toFloat(),
                    onj.get<Double>("end").toFloat(),
                    onj.get<Interpolation>("interpolation")
                )
            }
        }

        companion object {

            fun fromOnj(
                onj: OnjNamedObject
            ): DecorationDistribution = when (val name = onj.name) {
                "Random" -> RandomDistribution
                "FadeX" -> FadeX.fromOnj(onj)
                else -> throw RuntimeException("unknown decoration distribution function $name")
            }
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
