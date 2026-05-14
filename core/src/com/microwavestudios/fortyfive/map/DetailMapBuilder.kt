package com.microwavestudios.fortyfive.map

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.map.DetailMap.MapDecoration
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.utils.Promise

class DetailMapBuilder(
    var name: String = "",
    var startNode: MapNodeBuilder,
    var endNode: MapNodeBuilder,
    val decorations: MutableList<MapDecorationBuilder> = mutableListOf(),
    var isArea: Boolean = false,
    var biome: String = "wasteland",
    var scrollable: Boolean = true,
    var camPosOffset: Vector2 = Vector2(0f, 0f),
    var majorDifficulty: Int = 0,
) {

    fun uniqueNodes(): Set<MapNodeBuilder> {

        fun nodesFor(node: MapNodeBuilder, nodes: MutableSet<MapNodeBuilder>) {
            if (!nodes.add(node)) return
            node.edgesTo.forEach { nodesFor(it, nodes) }
        }

        val nodes = mutableSetOf<MapNodeBuilder>()
        nodesFor(startNode, nodes)
        return nodes
    }

    fun uniqueEdges(uniqueNodes: Set<MapNodeBuilder>): Set<Pair<MapNodeBuilder, MapNodeBuilder>> {
        val edges = mutableSetOf<Pair<MapNodeBuilder, MapNodeBuilder>>()
        uniqueNodes.forEach { node ->
            node.edgesTo.forEach { edgeNode ->
                require(node.index != edgeNode.index)
                val edge = if (node.index > edgeNode.index) node to edgeNode else edgeNode to node
                edges.add(edge)
            }
        }
        return edges
    }

    fun build(): DetailMap {
        startNode.build()
        return DetailMap(
            name,
            startNode.asNode!!,
            endNode.asNode!!,
            decorations.filter { !it.animated }.map { it.build() },
            decorations.filter { it.animated }.map { it.build() },
            isArea,
            biome,
            scrollable,
            camPosOffset,
            majorDifficulty
        )
    }

    companion object {

        fun from(map: DetailMap): DetailMapBuilder {
            val (startNode, endNode) = MapNodeBuilder.fromNode(map.startNode, map)
            val decorations =
                map.decorations.map { MapDecorationBuilder.fromMapDecoration(it, false) } +
                map.animatedDecorations.map { MapDecorationBuilder.fromMapDecoration(it, true) }
            return DetailMapBuilder(
                map.name,
                startNode,
                endNode,
                decorations.toMutableList(),
                map.isArea,
                map.biome,
                map.scrollable,
                map.camPosOffset,
                map.majorDifficulty,
            )
        }
    }

}

data class MapDecorationBuilder(
    val drawableHandle: ResourceHandle,
    val baseWidth: Float,
    val baseHeight: Float,
    val drawInBackground: Boolean,
    val animated: Boolean,
    val instances: MutableList<MapDecorationBuilderInstance>
) {

    private var drawableCache: Promise<Drawable>? = null

    fun requestDrawable(screen: RenderableScreen, borrower: ResourceBorrower) {
        val handle = if (animated) {
            MapEditorWidget.animatedDecoPreviewDrawables[drawableHandle]!!
        } else {
            drawableHandle
        }
        drawableCache = FortyFive.resourceManager.request<Drawable>(borrower, screen.lifetime, handle)
    }

    fun getDrawable(screen: RenderableScreen, borrower: ResourceBorrower): Promise<Drawable> {
        drawableCache?.let { return it }
        requestDrawable(screen, borrower)
        return drawableCache!!
    }

    fun invalidateCachedAssets() {
        drawableCache = null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MapDecorationBuilder
        if (baseWidth != other.baseWidth) return false
        if (baseHeight != other.baseHeight) return false
        if (drawInBackground != other.drawInBackground) return false
        if (drawableHandle != other.drawableHandle) return false
        return true
    }

    override fun hashCode(): Int {
        var result = baseWidth.hashCode()
        result = 31 * result + baseHeight.hashCode()
        result = 31 * result + drawInBackground.hashCode()
        result = 31 * result + drawableHandle.hashCode()
        return result
    }

    fun build(): MapDecoration = MapDecoration(
        drawableHandle,
        baseWidth, baseHeight,
        drawInBackground,
        instances.map { it.position to it.scale }
    )

    companion object {

        fun fromMapDecoration(decoration: MapDecoration, animated: Boolean): MapDecorationBuilder = MapDecorationBuilder(
            decoration.drawableHandle,
            decoration.baseWidth, decoration.baseHeight,
            decoration.drawInBackground,
            animated,
            decoration.instances.map { MapDecorationBuilderInstance(it.first, it.second) }.toMutableList()
        )
    }

}

data class MapDecorationBuilderInstance(
    var position: Vector2,
    var scale: Float
)
