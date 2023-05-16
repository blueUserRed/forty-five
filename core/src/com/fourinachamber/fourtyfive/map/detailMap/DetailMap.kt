package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.utils.MainThreadOnly
import onj.builder.buildOnjObject
import onj.builder.toOnjArray
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*

data class DetailMap(
    val name: String,
    val startNode: MapNode,
    val endNode: MapNode,
    val decorations: List<MapDecoration>
) {

    val uniqueNodes by lazy {
        startNode.getUniqueNodes()
    }

    val uniqueEdges by lazy {
        MapNode.getUniqueEdgesFor(uniqueNodes)
    }

    val uniqueEdgesWithOpposites by lazy {
        MapNode.getUniqueEdgesWithOppositesFor(uniqueNodes)
    }

    fun asOnjObject(): OnjObject = buildOnjObject {
        "nodes" with nodesAsOnjArray()
        "startNode" with startNode.index
        "endNode" with endNode.index
        "decorations" with decorations.map { it.asOnjObject() }
    }

    fun nodesAsOnjArray(): OnjArray {
        val uniqueNodes = uniqueNodes.sortedBy { it.index }
        return uniqueNodes
            .map { node ->
                buildOnjObject {
                    "x" with node.x
                    "y" with node.y
                    "isArea" with node.isArea
                    "edgesTo" with node.edgesTo.map { uniqueNodes.indexOf(it) }
                    "blockingEdges" with node.blockingEdges.map { uniqueNodes.indexOf(it) }
                    "event" with node.event?.asOnjObject()
                    node.imageName?.let {
                        "image" with it
                        "imagePos" with (node.imagePos?.name ?: "up")
                    }
                }
            }
            .toOnjArray()
    }


    companion object {

        fun readFromFile(file: FileHandle): DetailMap {
            val onj = OnjParser.parseFile(file.file())
            mapOnjSchema.assertMatches(onj)
            onj as OnjObject
            val nodes = mutableListOf<MapNodeBuilder>()
            val nodesOnj = onj.get<OnjArray>("nodes")
            nodesOnj
                .value
                .forEachIndexed { index, nodeOnj ->
                    nodeOnj as OnjObject
                    nodes.add(MapNodeBuilder(
                        index,
                        nodeOnj.get<Double>("x").toFloat(),
                        nodeOnj.get<Double>("y").toFloat(),
                        mutableListOf(),
                        mutableListOf(),
                        nodeOnj.get<Boolean>("isArea"),
                        nodeOnj.getOr<String?>("image", null),
                        MapNode.ImagePosition.valueOf(nodeOnj.getOr("imagePos", "up").uppercase()),
                        if (nodeOnj.hasKey<OnjNull>("event")) {
                            EmptyMapEvent()
                        } else {
                            MapEventFactory.getMapEvent(nodeOnj.get<OnjNamedObject>("event"))
                        }
                    ))
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
            nodesOnj
                .value
                .forEachIndexed { index, nodeOnj ->
                    nodeOnj as OnjObject
                    nodeOnj.get<OnjArray>("blockingEdges").value.forEach { blockingEdgeOnj ->
                        val blockingEdge = (blockingEdgeOnj as OnjInt).value.toInt()
                        nodes[index].blockingEdges.add(nodes[blockingEdge])
                    }
                }
            val startNodeIndex = onj.get<Long>("startNode").toInt()
            val decorations = onj.get<OnjArray>("decorations").value.map { MapDecoration.fromOnj(it as OnjObject) }
            return DetailMap(file.nameWithoutExtension(), nodes[startNodeIndex].build(), endNode.asNode!!, decorations)
        }

        private val mapOnjSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/detail_map.onjschema").file())
        }

    }

    data class MapDecoration(
        val drawableHandle: String,
        val baseWidth: Float,
        val baseHeight: Float,
        val instances: List<Pair<Vector2 /* = Position */, Float /* = scale */>>
    ) {

        private var drawable: Drawable? = null

        @MainThreadOnly
        fun getDrawable(screen: OnjScreen): Drawable {
            drawable?.let { return it }
            val drawable = ResourceManager.get<Drawable>(screen, drawableHandle)
            this.drawable = drawable
            return drawable
        }

        fun asOnjObject(): OnjObject = buildOnjObject {
            "texture" with drawableHandle
            "baseWidth" with baseWidth
            "baseHeight" with baseHeight
            "positions" with instances.map { buildOnjObject {
                "x" with it.first.x
                "y" with it.first.y
                "scale" with it.second
            } }
        }

        companion object {
            fun fromOnj(obj: OnjObject): MapDecoration = MapDecoration(
                obj.get<String>("texture"),
                obj.get<Double>("baseWidth").toFloat(),
                obj.get<Double>("baseHeight").toFloat(),
                obj
                    .get<OnjArray>("positions")
                    .value
                    .map {
                        it as OnjObject
                        Vector2(
                            it.get<Double>("x").toFloat(),
                            it.get<Double>("y").toFloat()
                        ) to it.get<Double>("scale").toFloat()
                    }
            )
        }

    }

}
