package com.fourinachamber.fourtyfive.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ZIndexActor
import com.fourinachamber.fourtyfive.utils.plus

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
            nodeDrawable.draw(batch, x + node.x, y + node.y, nodeSize, nodeSize)
        }
        val globalCoords = localToStageCoordinates(Vector2(0f, 0f))
        batch.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.projectionMatrix = screen.stage.viewport.camera.combined
        shapeRenderer.color = Color.BLACK
        Gdx.gl.glLineWidth(Gdx.graphics.height / lineWidth)
        for (edge in uniqueEdges) {
            val (node1, node2) = edge
            shapeRenderer.line(
                globalCoords + Vector2(node1.x + nodeSize / 2, node1.y + nodeSize / 2),
                globalCoords + Vector2(node2.x + nodeSize / 2, node2.y + nodeSize / 2)
            )
        }
        shapeRenderer.end()
        batch.begin()
    }

}