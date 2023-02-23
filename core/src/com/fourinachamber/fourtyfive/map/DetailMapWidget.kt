package com.fourinachamber.fourtyfive.map

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ZIndexActor
import com.fourinachamber.fourtyfive.utils.*
import kotlin.math.asin

class DetailMapWidget(
    private val screen: OnjScreen,
    private val nodeDrawable: Drawable,
    private val edgeTexture: TextureRegion,
    private val playerDrawable: Drawable,
    private val playerWidth: Float,
    private val playerHeight: Float,
    private val nodeSize: Float,
    private val lineWidth: Float,
    private val playerMoveTime: Int,
    var background: Drawable? = null
) : Widget(), ZIndexActor {

    private var start: MapNode? = null
    private var uniqueNodes: List<MapNode>? = null
    private var uniqueEdges: List<Pair<MapNode, MapNode>>? = null

    override var fixedZIndex: Int = 0

    private var mapOffset: Vector2 = Vector2(0f, 0f)

    private var playerNode: MapNode? = null
    private var playerPos: Vector2? = null
    private var movePlayerTo: MapNode? = null
    private var playerMovementStartTime: Long = 0L

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

    private val clickListener = object : ClickListener() {

        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            super.clicked(event, x, y)
            val uniqueNodes = uniqueNodes ?: return
            for (node in uniqueNodes) {
                if (x in node.x..(node.x + nodeSize) && y in node.y..(node.y + nodeSize)) {
                    handleClick(node)
                }
            }
        }
    }

    init {
        addListener(dragListener)
        addListener(clickListener)
    }

    private fun handleClick(node: MapNode) {
        if (playerNode?.isLinkedTo(node) ?: false) return
        movePlayerTo = node
        playerMovementStartTime = TimeUtils.millis()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()
        updatePlayerMovement()
        batch ?: return
        background?.draw(batch, x, y, width, height)
        drawEdges(batch)
        drawNodes(batch)
        val playerPos = playerPos ?: return
        val playerX = x + playerPos.x + mapOffset.x + nodeSize / 2 - playerWidth / 2
        val playerY = y + playerPos.y + mapOffset.y + nodeSize / 2 - playerHeight / 2
        playerDrawable.draw(batch, playerX, playerY, playerWidth, playerHeight)
    }

    private fun updatePlayerMovement() {
        val movePlayerTo = movePlayerTo ?: return
        val playerNode = playerNode ?: return
        val curTime = TimeUtils.millis()
        val movementFinishTime = playerMovementStartTime + playerMoveTime
        if (curTime >= movementFinishTime) {
            this.playerNode = movePlayerTo
            playerPos = Vector2(movePlayerTo.x, movePlayerTo.y)
            this.movePlayerTo = null
            return
        }
        val percent = (movementFinishTime - curTime) / playerMoveTime.toFloat()
        val movementPath = Vector2(movePlayerTo.x, movePlayerTo.y) - Vector2(playerNode.x, playerNode.y)
        val playerOffset = movementPath * (1f - percent)
        playerPos = Vector2(playerNode.x, playerNode.y) + playerOffset
    }

    fun setMap(start: MapNode?) {
        // this is a function instead of a kotlin setter to indicate that setting the map can be expensive
        this.start = start
        start ?: return
        uniqueNodes = start.getUniqueNodes()
        uniqueEdges = MapNode.getUniqueEdgesFor(uniqueNodes!!)
        playerNode = start
        playerPos = Vector2(start.x, start.y)
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