package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ZIndexActor
import com.fourinachamber.fourtyfive.utils.*
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

class DetailMapWidget(
    private val screen: OnjScreen,
    private val map: DetailMap,
    private val nodeDrawable: Drawable,
    private val edgeTexture: TextureRegion,
    private val playerDrawable: Drawable,
    private val playerWidth: Float,
    private val playerHeight: Float,
    private val nodeSize: Float,
    private val lineWidth: Float,
    private val playerMoveTime: Int,
    private val directionIndicator: TextureRegion,
    private val detailFont: BitmapFont,
    private val detailFontColor: Color,
    private val detailBackground: Drawable,
    var background: Drawable? = null
) : WidgetGroup(), ZIndexActor {

    override var fixedZIndex: Int = 0

    private var mapOffset: Vector2 = Vector2(0f, 0f)

    private var playerNode: MapNode = map.startNode
    private var playerPos: Vector2 = Vector2(map.startNode.x, map.startNode.y)
    private var movePlayerTo: MapNode? = null
    private var playerMovementStartTime: Long = 0L

    private var displayDetail: Boolean = false
    private val detailWidget: MapEventDetailWidget = MapEventDetailWidget(
        screen,
        detailFont,
        detailFontColor,
        detailBackground,
        this::onStartButtonClicked
    )

    private var pointToNode: MapNode? = null

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

        val maxClickTime: Long = 300

        private var lastTouchDownTime: Long = 0

        override fun mouseMoved(event: InputEvent?, x: Float, y: Float): Boolean {
            updateDirectionIndicator(Vector2(x, y))
            return super.mouseMoved(event, x, y)
        }

        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            lastTouchDownTime = TimeUtils.millis()
            return super.touchDown(event, x, y, pointer, button)
        }

        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            super.clicked(event, x, y)
            if (TimeUtils.millis() > lastTouchDownTime + maxClickTime) return
            if (movePlayerTo != null) {
                skipPlayerAnimation()
                return
            }
            pointToNode?.let { goToNode(it) }
        }
    }

    init {
        addListener(dragListener)
        addListener(clickListener)
        addActor(detailWidget)
    }

    private fun onStartButtonClicked() {
        playerNode.event?.start()
    }

    private fun updateDirectionIndicator(pointerPosition: Vector2) {
        var bestMatch: MapNode? = null
        var bestMatchValue = -1f
        val playerNodePosition = Vector2(playerNode.x, playerNode.y)
        val mouseDirection = ((pointerPosition - mapOffset) - playerNodePosition).unit
        playerNode.edgesTo.forEach { node ->
            val nodeDirection = (Vector2(node.x, node.y) - playerNodePosition).unit
            val result = mouseDirection dot nodeDirection
            if (result > bestMatchValue) {
                bestMatch = node
                bestMatchValue = result
            }
        }
        pointToNode = bestMatch
    }

    private fun goToNode(node: MapNode) {
        if (!playerNode.isLinkedTo(node)) return
        if (playerNode.event?.currentlyBlocks ?: false && node in playerNode.blockingEdges) return
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
        val playerX = x + playerPos.x + mapOffset.x + nodeSize / 2 - playerWidth / 2
        val playerY = y + playerPos.y + mapOffset.y + nodeSize / 2 - playerHeight / 2
        playerDrawable.draw(batch, playerX, playerY, playerWidth, playerHeight)
        drawDirectionIndicator(batch)
        drawDecorations(batch)
        super.draw(batch, parentAlpha)
    }

    private fun drawDirectionIndicator(batch: Batch) {
        val pointToNode = pointToNode ?: return
        val node = playerNode
        val indicatorOffset = 10f

        val yDiff = pointToNode.y - node.y
        val xDiff = pointToNode.x - node.x
        val length = (Vector2(pointToNode.x, pointToNode.y) - Vector2(node.x, node.y)).len()
        val angleRadians = asin((yDiff / length).toDouble()).toFloat()

        val dy = sin(angleRadians) * indicatorOffset
        var dx = cos(angleRadians) * indicatorOffset
        if (xDiff < 0) dx = -dx

        batch.draw(
            directionIndicator,
            x + node.x + mapOffset.x + dx,
            y + node.y + mapOffset.y + dy,
            (directionIndicator.regionWidth * 0.01f) / 2,
            (directionIndicator.regionHeight * 0.01f) / 2,
            directionIndicator.regionWidth * 0.01f,
            directionIndicator.regionHeight * 0.01f,
            1f,
            1f,
            if (xDiff >= 0) angleRadians.degrees else 360f - angleRadians.degrees
        )
    }


    private fun drawDecorations(batch: Batch) {
        val (offX, offY) = mapOffset
        map.decorations.forEach { decoration ->
            val drawable = decoration.getDrawable(screen)
            val width = decoration.baseWidth
            val height = decoration.baseHeight
            decoration.instances.forEach { instance ->
                drawable.draw(
                    batch,
                    offX + instance.first.x, offY + instance.first.y,
                    width * instance.second, height * instance.second
                )
            }
        }
    }

    override fun layout() {
        super.layout()
        if (!displayDetail) return
        val detail = detailWidget
        detail.height = height
        detail.width = detail.prefWidth
        detail.debug = true
        detail.setPosition(
            width - detail.width,
            0f
        )
    }

    private fun updatePlayerMovement() {
        val movePlayerTo = movePlayerTo ?: return
        val playerNode = playerNode
        val curTime = TimeUtils.millis()
        val movementFinishTime = playerMovementStartTime + playerMoveTime
        if (curTime >= movementFinishTime) {
            this.playerNode = movePlayerTo
            playerPos = Vector2(movePlayerTo.x, movePlayerTo.y)
            setupDetailFor(movePlayerTo)
            this.movePlayerTo = null
            return
        }
        val percent = (movementFinishTime - curTime) / playerMoveTime.toFloat()
        val movementPath = Vector2(movePlayerTo.x, movePlayerTo.y) - Vector2(playerNode.x, playerNode.y)
        val playerOffset = movementPath * (1f - percent)
        playerPos = Vector2(playerNode.x, playerNode.y) + playerOffset
    }

    private fun skipPlayerAnimation() {
        val movePlayerTo = movePlayerTo ?: return
        this.playerNode = movePlayerTo
        playerPos = Vector2(movePlayerTo.x, movePlayerTo.y)
        setupDetailFor(movePlayerTo)
        this.movePlayerTo = null
    }

    private fun setupDetailFor(node: MapNode?) {
        displayDetail = node?.event?.displayDescription ?: false
        detailWidget.isVisible = displayDetail
        if (!displayDetail) return
        detailWidget.setForEvent(node!!.event!!)
        invalidate()
    }

    private fun drawNodes(batch: Batch) {
        val uniqueNodes = map.uniqueNodes
        for (node in uniqueNodes) {
            val (nodeX, nodeY) = calcNodePosition(node)
            nodeDrawable.draw(batch, x + nodeX, y + nodeY, nodeSize, nodeSize)
        }
    }

    private fun drawEdges(batch: Batch) {
        val uniqueEdges = map.uniqueEdges
        for ((node1, node2) in uniqueEdges) {
            val dy = node2.y - node1.y
            val dx = node2.x - node1.x
            val length = Vector2(dx, dy).len()
            var angle = Math.toDegrees(asin((dy / length).toDouble())).toFloat() - 90f
            if (dx < 0) angle = 360 - angle
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