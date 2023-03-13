package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ZIndexActor
import com.fourinachamber.fourtyfive.screen.general.styleTest.StyleManager
import com.fourinachamber.fourtyfive.screen.general.styleTest.StyledActor
import com.fourinachamber.fourtyfive.screen.general.styleTest.addActorStyles
import com.fourinachamber.fourtyfive.utils.*
import io.github.orioncraftmc.meditate.YogaNode
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
    private val detailWidgetName: String,
    var background: Drawable? = null
) : Widget(), ZIndexActor, StyledActor {

    override var fixedZIndex: Int = 0

    override lateinit var styleManager: StyleManager
    override var isHoveredOver: Boolean = false

    private var mapOffset: Vector2 = Vector2(0f, 0f)

    private var playerNode: MapNode = map.startNode
    private var playerPos: Vector2 = Vector2(map.startNode.x, map.startNode.y)
    private var movePlayerTo: MapNode? = null
    private var playerMovementStartTime: Long = 0L

//    private val detailWidget: MapEventDetailWidget = MapEventDetailWidget(
//        screen,
//        detailFont,
//        detailFontColor,
//        detailBackground,
//        this::onStartButtonClicked
//    )

    private val detailWidget: MapEventDetailWidget by lazy {
        val widget = screen.namedActorOrError(detailWidgetName)
        if (widget !is MapEventDetailWidget) {
            throw RuntimeException("expected $detailWidgetName to be of type DetailMapWidget")
        }
        widget.onStartClickedListener = this::onStartButtonClicked
        widget
    }

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
        bindHoverStateListeners(this)
        addListener(dragListener)
        addListener(clickListener)
        invalidateHierarchy()
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

        val (screenX, screenY) = localToStageCoordinates(Vector2(0f, 0f))
        val bounds = Rectangle(screenX, screenY, width, height)
        val scissor = Rectangle()
        ScissorStack.calculateScissors(screen.stage.camera, batch.transformMatrix, bounds, scissor)
        if (!ScissorStack.pushScissors(scissor)) return

        background?.draw(batch, x, y, width, height)
        drawEdges(batch)
        drawNodes(batch)
        val playerX = x + playerPos.x + mapOffset.x + nodeSize / 2 - playerWidth / 2
        val playerY = y + playerPos.y + mapOffset.y + nodeSize / 2 - playerHeight / 2
        playerDrawable.draw(batch, playerX, playerY, playerWidth, playerHeight)
        drawDirectionIndicator(batch)
        drawDecorations(batch)
        super.draw(batch, parentAlpha)

        batch.flush()
        ScissorStack.popScissors()
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
                    x + offX + instance.first.x, y + offY + instance.first.y,
                    width * instance.second, height * instance.second
                )
            }
        }
    }

    private fun updatePlayerMovement() {
        val movePlayerTo = movePlayerTo ?: return
        val playerNode = playerNode
        val curTime = TimeUtils.millis()
        val movementFinishTime = playerMovementStartTime + playerMoveTime
        if (curTime >= movementFinishTime) {
            this.playerNode = movePlayerTo
            playerPos = Vector2(movePlayerTo.x, movePlayerTo.y)
            detailWidget.setForEvent(movePlayerTo.event)
            updateScreenState(movePlayerTo.event)
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
        detailWidget.setForEvent(movePlayerTo.event)
        updateScreenState(movePlayerTo.event)
        this.movePlayerTo = null
    }

    private fun updateScreenState(event: MapEvent?) {
        val enter = event?.displayDescription ?: false
        if (enter) {
            screen.enterState(displayEventDetailScreenState)
        } else {
            screen.leaveState(displayEventDetailScreenState)
        }
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

    override fun initStyles(node: YogaNode, screen: OnjScreen) {
        addActorStyles(node, screen)
    }

    companion object {
        const val displayEventDetailScreenState: String = "displayEventDetail"
    }

}