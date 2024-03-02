package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.*
import onj.builder.buildOnjObject
import onj.builder.toOnjArray
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.schema.OnjSchemaException
import onj.value.*

/**
 * represents a detailMap
 * @param name the name of the map
 * @param startNode the first node of the map
 * @param endNode the last node of the map
 * @param decorations a list of decorations that are placed on the map
 */
data class DetailMap(
    val name: String,
    val startNode: MapNode,
    val endNode: MapNode,
    val decorations: List<MapDecoration>,
    val animatedDecorations: List<MapDecoration>,
    val isArea: Boolean,
    val biome: String,
    val progress: ClosedFloatingPointRange<Float>,
    val tutorialText: MutableList<MapScreenController.MapTutorialTextPart>,
    val scrollable: Boolean,
    val camPosOffset: Vector2
) {

    /**
     * all unique nodes on the map
     */
    val uniqueNodes by lazy {
        startNode.getUniqueNodes()
    }

    /**
     * all unique edges on the map (a -> b and b -> a count as the same)
     */
    val uniqueEdges by lazy {
        MapNode.getUniqueEdgesFor(uniqueNodes)
    }


    /**
     * all unique edges on the map (a -> b and b -> a count as different ones)
     */
    val uniqueEdgesWithOpposites by lazy {
        MapNode.getUniqueEdgesWithOppositesFor(uniqueNodes)
    }

    /**
     * some structures in the map cache assets for easier accessibility. This function marks them as invalid, and they
     * will be reloaded the next time they are used. Should be called when assets are returned by the ResourceBorrower
     * (e.g. after a screen transition)
     */
    fun invalidateCachedAssets() {
        uniqueNodes.forEach { it.invalidateCachedAssets() }
        decorations.forEach { it.invalidateCachedAssets() }
    }

    /**
     * returns a representation of this map as an OnjObject
     */
    fun asOnjObject(): OnjObject = buildOnjObject {
        "version" with mapVersion
        "nodes" with nodesAsOnjArray()
        "startNode" with startNode.index
        "endNode" with endNode.index
        "decorations" with decorations.map { it.asOnjObject() }
        "animatedDecorations" with animatedDecorations.map { it.asOnjObject() }
        "isArea" with isArea
        "biome" with biome
        "progress" with progress.asArray()
        "tutorialText" with tutorialText.map { it.asOnjObject() }
        "scrollable" with scrollable
        "camPosOffset" with camPosOffset.toArray()
    }

    private fun nodesAsOnjArray(): OnjArray {
        val uniqueNodes = uniqueNodes.sortedBy { it.index }
        return uniqueNodes
            .map { node ->
                buildOnjObject {
                    "x" with node.x
                    "y" with node.y
                    "edgesTo" with node.edgesTo.map { uniqueNodes.indexOf(it) }
                    "event" with node.event?.asOnjObject()
                    node.nodeTexture?.let { "nodeTexture" with node.nodeTexture }
                    node.imageName?.let {
                        "image" with it
                        "imagePos" with (node.imagePos?.name ?: "up")
                    }
                }
            }
            .toOnjArray()
    }

    class InvalidMapFileException : RuntimeException()

    companion object {

        const val mapVersion: Int = 0
        const val logTag = "Map"

        /**
         * reads a DetailMap from an onj-file
         */
        fun readFromFile(file: FileHandle): DetailMap {
            val onj = try {
                val onj = OnjParser.parseFile(file.file())
                mapOnjSchema.assertMatches(onj)
                onj
            } catch (e: OnjParserException) {
                FortyFiveLogger.warn(logTag, "invalid map loaded")
                FortyFiveLogger.stackTrace(e)
                throw InvalidMapFileException()
            } catch (e: OnjSchemaException) {
                FortyFiveLogger.warn(logTag, "invalid map loaded")
                FortyFiveLogger.stackTrace(e)
                throw InvalidMapFileException()
            }
            onj as OnjObject
            if (onj.get<Long>("version").toInt() != mapVersion) {
                FortyFiveLogger.warn(logTag, "map version mismatch: found: ${onj.get<Long>("version")} expected: $mapVersion")
                throw InvalidMapFileException()
            }
            val nodes = mutableListOf<MapNodeBuilder>()
            val nodesOnj = onj.get<OnjArray>("nodes")
            nodesOnj
                .value
                .forEachIndexed { index, nodeOnj ->
                    nodeOnj as OnjObject
                    nodes.add(
                        MapNodeBuilder(
                            index,
                            nodeOnj.get<Double>("x").toFloat(),
                            nodeOnj.get<Double>("y").toFloat(),
                            mutableListOf(),
                            nodeOnj.getOr<String?>("image", null),
                            MapNode.ImagePosition.valueOf(nodeOnj.getOr("imagePos", "up").uppercase()),
                            nodeOnj.getOr<String?>("nodeTexture", null),
                            if (nodeOnj.hasKey<OnjNull>("event")) {
                                EmptyMapEvent()
                            } else {
                                MapEventFactory.getMapEvent(nodeOnj.get<OnjNamedObject>("event"))
                            }
                        )
                    )
                }
            val endNodeIndex = onj.get<Long>("endNode").toInt()
            val endNode = nodes.getOrElse(endNodeIndex) {
                throw RuntimeException("no node with index $endNodeIndex in file $file")
            }
            nodesOnj
                .value
                .forEachIndexed { index, nodeOnj ->
                    nodeOnj as OnjObject
                    nodeOnj.get<OnjArray>("edgesTo").value.forEach { edgeToOnj ->
                        val edgeTo = (edgeToOnj as OnjInt).value.toInt()
                        nodes[index].edgesTo.add(nodes[edgeTo])
                    }
                }
            val startNodeIndex = onj.get<Long>("startNode").toInt()
            val decorations = onj
                .get<OnjArray>("decorations")
                .value
                .map { MapDecoration.fromOnj(it as OnjObject) }
            val animatedDecorations = onj
                .get<OnjArray>("animatedDecorations")
                .value
                .map { MapDecoration.fromOnj(it as OnjObject) }
            return DetailMap(
                file.nameWithoutExtension(),
                nodes[startNodeIndex].build(),
                endNode.asNode!!,
                decorations,
                animatedDecorations,
                onj.get<Boolean>("isArea"),
                onj.get<String>("biome"),
                onj.get<OnjArray>("progress").toFloatRange(),
                onj.getOr<OnjArray?>("tutorialText", null)
                    ?.value
                    ?.map { MapScreenController.MapTutorialTextPart.fromOnj(it as OnjObject) }
                    ?.toMutableList()
                    ?: mutableListOf(),
                onj.getOr("scrollable", true),
                if (onj.hasKey<OnjArray>("camPosOffset")) {
                    onj.get<OnjArray>("camPosOffset").toVector2()
                } else {
                    Vector2()
                }
            )
        }

        private val mapOnjSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/detail_map.onjschema").file())
        }

    }

    /**
     * represents a type of decoration on the map
     * @param drawableHandle the handle for the drawable used for drawing the decoration, or the name of the decoration
     * when it is animated
     * @param baseWidth the base width of the drawable, can be adjusted by scaling
     * @param baseHeight the base height of the drawable, can be adjusted by scaling
     * @param instances all instances of the decoration on the map. Contains both the position and the scale
     */
    data class MapDecoration(
        val drawableHandle: ResourceHandle,
        val baseWidth: Float,
        val baseHeight: Float,
        val drawInBackground: Boolean,
        val instances: List<Pair<Vector2 /* = Position */, Float /* = scale */>>
    ) {

        private var drawableCache: Drawable? = null

        @MainThreadOnly
        fun getDrawable(screen: OnjScreen): Drawable {
            drawableCache?.let { return it }
            val drawable = ResourceManager.get<Drawable>(screen, drawableHandle)
            this.drawableCache = drawable
            return drawable
        }

        fun invalidateCachedAssets() {
            drawableCache = null
        }

        /**
         * returns a representation of this decoration as an OnjObject
         */
        fun asOnjObject(): OnjObject = buildOnjObject {
            "texture" with drawableHandle
            "baseWidth" with baseWidth
            "baseHeight" with baseHeight
            "drawInBackground" with drawInBackground
            "positions" with instances.map { arrayOf(it.first.x, it.first.y, it.second) }
        }

        companion object {

            /**
             * reads a decoration from an OnjObject
             */
            fun fromOnj(obj: OnjObject): MapDecoration = MapDecoration(
                obj.get<String>("texture"),
                obj.get<Double>("baseWidth").toFloat(),
                obj.get<Double>("baseHeight").toFloat(),
                obj.getOr("drawInBackground", false),
                obj
                    .get<OnjArray>("positions")
                    .value
                    .map {
                        it as OnjArray
                        Vector2(
                            it.get<Double>(0).toFloat(),
                            it.get<Double>(1).toFloat()
                        ) to it.get<Double>(2).toFloat()
                    }
            )
        }

    }

}
