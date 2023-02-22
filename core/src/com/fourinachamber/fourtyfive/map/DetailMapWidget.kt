package com.fourinachamber.fourtyfive.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
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

class DetailMapWidget(
    private val screen: OnjScreen,
    private val nodeDrawable: Drawable,
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
        drawMap(batch)
    }

    fun setMap(start: MapNode?) {
        // this is a function instead of a kotlin setter to indicate that setting the map can be expensive
        this.start = start
        start ?: return
        uniqueNodes = start.getUniqueNodes()
        uniqueEdges = MapNode.getUniqueEdgesFor(uniqueNodes!!)
    }

    private fun drawMap(batch: Batch) {
        val uniqueNodes = uniqueNodes ?: return
        val uniqueEdges = uniqueEdges ?: return
        for (node in uniqueNodes) {
            val (nodeX, nodeY) = calcNodePosition(node)
            nodeDrawable.draw(batch, x + nodeX, y + nodeY, nodeSize, nodeSize)
        }
        val globalCoords = localToStageCoordinates(Vector2(0f, 0f))
        batch.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.projectionMatrix = screen.stage.viewport.camera.combined
        shapeRenderer.color = Color.BLACK
        Gdx.gl.glLineWidth(Gdx.graphics.height / lineWidth)
        for (edge in uniqueEdges) {
            val (node1, node2) = edge
            val (node1x, node1y) = calcNodePosition(node1)
            val (node2x, node2y) = calcNodePosition(node2)
            shapeRenderer.line(
                globalCoords + Vector2(node1x + nodeSize / 2, node1y + nodeSize / 2),
                globalCoords + Vector2(node2x + nodeSize / 2, node2y + nodeSize / 2)
            )
        }
        shapeRenderer.end()
        batch.begin()
    }

    private fun calcNodePosition(node: MapNode): Vector2 {
        return Vector2(node.x, node.y) + mapOffset
    }

}