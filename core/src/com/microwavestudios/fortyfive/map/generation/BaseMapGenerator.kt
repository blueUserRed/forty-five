package com.microwavestudios.fortyfive.map.generation

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.map.*
import com.microwavestudios.fortyfive.onjNamespaces.OnjInterpolation
import com.microwavestudios.fortyfive.run.EncounterGenerator
import com.microwavestudios.fortyfive.utils.*
import onj.builder.OnjObjectBuilderDSL
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.properties.Delegates
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

    protected var majorDifficulty by Delegates.notNull<Int>()

    private val nodeColliders: MutableList<Rectangle> = mutableListOf()
    private val decorationColliders: MutableList<Rectangle> = mutableListOf()
    private val lineColliders: MutableList<Line2D> = mutableListOf()

    protected var startNode: MapNodeBuilder? = null
        private set
    protected var endNode: MapNodeBuilder? = null
        private set

    abstract fun generate(name: String, seed: Long): DetailMap

    protected fun setup(name: String, data: BaseMapGeneratorData, seed: Long) {
        this.random = Random(seed)
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

    protected fun rotateNodes(angleRad: Double = data.rotation.toDouble() ) {
        _allNodes.forEach { node -> node.rotate(angleRad) }
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

    private fun setupLastNode(node: MapNodeBuilder) {
        node.event = data.lastNodeEvent()
        node.nodeTexture = data.lastNodeTexture
        endNode = node
    }

    private fun setupFirstNode(node: MapNodeBuilder) {
        node.event = data.firstNodeEvent()
        node.nodeTexture = data.firstNodeTexture
        startNode = node
    }

    private fun calculateDistances(startNode: MapNodeBuilder) {
        var currentNodes = listOf(startNode)
        var dist = 0
        while (currentNodes.isNotEmpty()) {
            val newNodes = mutableListOf<MapNodeBuilder>()
            currentNodes.forEach { node ->
                node.distance = dist
                node.edgesTo.filter { it.distance == -1 }.let { newNodes.addAll(it) }
            }
            currentNodes = newNodes
            dist++
        }
    }

    protected fun connectNodes(node1: MapNodeBuilder, node2: MapNodeBuilder) {
        node1.connect(node2)
        lineColliders.add(Line2D(Vector2(node1.x, node1.y), Vector2(node2.x, node2.y)))
    }

    fun generateMapEvents() {
        val startNode = _allNodes.random(random)
        setupFirstNode(startNode)
        val endNode = chooseEndNode(startNode)
        setupLastNode(endNode)
        calculateDistances(startNode)

        _allNodes.forEach { node ->
            if (node === startNode || node === endNode) return@forEach
            val distance = node.distance
            val allowedFillEvents = data
                .fillEvents
                .filter {
                    (it.minDistance == null || it.minDistance <= distance) &&
                            (it.maxDistance == null || it.maxDistance >= distance)
                }
            if (allowedFillEvents.isEmpty()) {
                FortyFive.logger.warn(logTag, "No allowed events for distance $distance in map $name")
                return@forEach
            }
            val chosen = allowedFillEvents
                .zipToFirst { it.weight }
                .weightedRandom(random)
            node.event = chosen.nodeEvent()
            node.nodeTexture = chosen.nodeTexture
        }

        val availableNodes = _allNodes.toMutableList()
        availableNodes.remove(startNode)
        availableNodes.remove(endNode)
        data.fixedEvents.forEach { event ->
            repeat(event.amount) {
                val possibleNodes = availableNodes.filter {
                    (event.minDistance == null || event.minDistance <= it.distance) &&
                            (event.maxDistance == null || event.maxDistance >= it.distance)
                }
                if (possibleNodes.isEmpty()) {
                    FortyFive.logger.warn(
                        logTag,
                        "Can't spawn event ${event.nodeEvent()} because there are no free nodes in the distance range"
                    )
                    return@forEach
                }
                val node = possibleNodes.random(random)
                node.nodeTexture = event.nodeTexture
                node.event = event.nodeEvent()
                availableNodes.remove(node)
            }
        }
    }

    private fun chooseEndNode(startNode: MapNodeBuilder): MapNodeBuilder {
        var cur = startNode
        var steps = data.randomStepsToLastNode
        // This random walk seems to always just return back to where it started,
        // maybe replace with a different system at some point, but it isn't too
        // much of an issue, the exit node is supposed to be close to the start node
        // anyway
        while (steps > 0 || cur === startNode) {
            cur = cur.edgesTo.random(random)
            steps--
        }
        return cur
    }

    fun generateEncounters() {
        val startNode = startNode
        requireNotNull(startNode) { "call generateMapEvents() before generateEncounters()" }
        _allNodes.forEach { node ->
            val event = node.event
            if (event !is EncounterPlaceholderMapEvent) return@forEach
            val encounter = EncounterGenerator.generate(event, node, startNode)
            val mapEvent = EncounterMapEvent(encounter, event.genExtraction)
            node.event = mapEvent
        }
    }

    abstract fun asOnj(): OnjObject

    interface BaseMapGeneratorData {
        val nodeProtectedArea: Float
        val locationSignProtectedAreaWidth: Float
        val locationSignProtectedAreaHeight: Float
        val firstNodeEvent: () -> MapEvent
        val firstNodeTexture: String
        val lastNodeEvent: () -> MapEvent
        val randomStepsToLastNode: Int
        val lastNodeTexture: String
        val majorDifficulty: Int
        val rotation: Float
        val fillEvents: List<MapGeneratorFillEvent>
        val fixedEvents: List<MapGeneratorFixedEvent>

        fun asOnj(): OnjObject

        fun OnjObjectBuilderDSL.includeBaseData() {
            "nodeProtectedArea" with nodeProtectedArea
            "locationSignProtectedAreaWidth" with locationSignProtectedAreaWidth
            "locationSignProtectedAreaHeight" with locationSignProtectedAreaHeight
            "firstNodeEvent" with firstNodeEvent().asOnjObject()
            "firstNodeTexture" with firstNodeTexture
            "lastNodeEvent" with lastNodeEvent().asOnjObject()
            "randomStepsToLastNode" with randomStepsToLastNode
            "lastNodeTexture" with lastNodeTexture
            "majorDifficulty" with majorDifficulty
            "rotation" with rotation
            "fillEvents" with fillEvents.map { it.asOnj() }
            "fixedEvents" with fixedEvents.map { it.asOnj() }
        }
    }

    data class MapGeneratorFixedEvent(
        val nodeEvent: () -> MapEvent,
        val nodeTexture: String,
        val minDistance: Int?,
        val maxDistance: Int?,
        val amount: Int
    ) {
        fun asOnj(): OnjObject = buildOnjObject {
            "nodeEvent" with nodeEvent().asOnjObject()
            "nodeTexture" with nodeTexture
            "minDistance" with minDistance
            "maxDistance" with maxDistance
            "amount" with amount
        }

        companion object {
            fun fromOnj(onj: OnjObject): MapGeneratorFixedEvent = MapGeneratorFixedEvent(
                { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("nodeEvent")) },
                onj.get<String>("nodeTexture"),
                onj.get<Long?>("minDistance")?.toInt(),
                onj.get<Long?>("maxDistance")?.toInt(),
                onj.get<Long>("amount").toInt(),
            )
        }
    }

    data class MapGeneratorFillEvent(
        val nodeEvent: () -> MapEvent,
        val nodeTexture: String,
        val minDistance: Int?,
        val maxDistance: Int?,
        val weight: Int
    ) {
        fun asOnj(): OnjObject = buildOnjObject {
            "nodeEvent" with nodeEvent().asOnjObject()
            "nodeTexture" with nodeTexture
            "minDistance" with minDistance
            "maxDistance" with maxDistance
            "weight" with weight
        }

        companion object {
            fun fromOnj(onj: OnjObject): MapGeneratorFillEvent = MapGeneratorFillEvent(
                { MapEventFactory.getMapEvent(onj.get<OnjNamedObject>("nodeEvent")) },
                onj.get<String>("nodeTexture"),
                onj.get<Long?>("minDistance")?.toInt(),
                onj.get<Long?>("maxDistance")?.toInt(),
                onj.get<Long>("weight").toInt(),
            )
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
            "StaticMap" -> StaticMapGenerator(onj.get<String>("name"))
            "PointCloud" -> PointCloudMapGenerator(PointCloudMapGenerator.PointCloudMapGeneratorData.fromOnj(onj))
            else -> throw RuntimeException("unknown MapGenerator: $name")
        }

    }

}
