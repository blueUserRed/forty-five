package com.fourinachamber.fourtyfive.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ZIndexActor
import com.fourinachamber.fourtyfive.utils.minus
import com.fourinachamber.fourtyfive.utils.plus
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import kotlin.math.asin

class DetailMapWidget(
    private val screen: OnjScreen,
    private val nodeDrawable: Drawable,
    private val edgeTexture: TextureRegion,
    private val nodeSize: Float,
    private val lineWidth: Float,
    var background: Drawable? = null
) : Widget(), ZIndexActor {

    private var start: MapNode? = null
    private var uniqueNodes: List<MapNode>? = null
    private var uniqueEdges: List<Pair<MapNode, MapNode>>? = null

    override var fixedZIndex: Int = 0

    private val shapeRenderer: ShapeRenderer = ShapeRenderer()

    private var mapOffset: Vector2 = Vector2(0f, 0f)

    private val dragListener = object : DragListener() {

        private var dragStartPosition: Vector2? = null
        private var mapOffsetOnDragStart: Vector2? = null

        override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            super.dragStart(event, x, y, pointer)
            dragStartPosition = Vector2(x, y)
            mapOffsetOnDragStart = mapOffset
        }

        override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            super.drag(event, x, y, pointer)
            val dragStartPosition = dragStartPosition ?: return
            val mapOffsetOnDragStart = mapOffsetOnDragStart ?: return
            val draggedDistance = dragStartPosition - Vector2(x, y)
            mapOffset = mapOffsetOnDragStart - draggedDistance
        }

        override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            super.dragStop(event, x, y, pointer)
            dragStartPosition = null
            mapOffsetOnDragStart = null
        }
    }

    init {
        addListener(dragListener)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()
        batch ?: return
        background?.draw(batch, x, y, width, height)
        drawEdges(batch)
        drawNodes(batch)
    }

    fun setMap(start: MapNode?) {
        // this is a function instead of a kotlin setter to indicate that setting the map can be expensive
        this.start = start
        start ?: return
        uniqueNodes = start.getUniqueNodes()
        uniqueEdges = MapNode.getUniqueEdgesFor(uniqueNodes!!)
    }

    private fun drawNodes(batch: Batch) {
        val uniqueNodes = uniqueNodes ?: return
        for (node in uniqueNodes) {
            val (nodeX, nodeY) = calcNodePosition(node)
            nodeDrawable.draw(batch, x + nodeX, y + nodeY, nodeSize, nodeSize)
        }
    }

    private fun drawEdges(batch: Batch) {
        val uniqueEdges = uniqueEdges ?: return
        for ((node1, node2) in uniqueEdges) {
            val dy = node2.y - node1.y
            val dx = node2.x - node1.x
            val length = Vector2(dx, dy).len()
            val angle = Math.toDegrees(asin((dy / length).toDouble())).toFloat() - 90f
            batch.draw(
                edgeTexture,
                x + node1.x + mapOffset.x + nodeSize / 2, y + node1.y + mapOffset.y + nodeSize / 2 + lineWidth / 2,
                0f, 0f,
                lineWidth,
                length,
                1.0f, 1.0f,
                angle
            )
        }
    }

    private fun calcNodePosition(node: MapNode): Vector2 {
        return Vector2(node.x, node.y) + mapOffset
    }

}