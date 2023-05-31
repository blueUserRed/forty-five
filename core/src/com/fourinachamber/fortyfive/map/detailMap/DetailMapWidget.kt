package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.onButtonClick
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.utils.*
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * the widget used for displaying a [DetailMap]
 */
class DetailMapWidget(
    private val screen: OnjScreen,
    private val map: DetailMap,
    private val nodeDrawableHandle: ResourceHandle,
    private val edgeTextureHandle: ResourceHandle,
    private val playerDrawableHandle: ResourceHandle,
    private val playerWidth: Float,
    private val playerHeight: Float,
    private val nodeSize: Float,
    private val lineWidth: Float,
    private val playerMoveTime: Int,
    private val directionIndicatorHandle: ResourceHandle,
    private val startButtonName: String,
    var backgroundHandle: ResourceHandle,
    private var screenSpeed: Float,
    private var backgroundScale: Float,
    private val leftScreenSideDeadSection: Float
) : Widget(), ZIndexActor, StyledActor {

    override var fixedZIndex: Int = 0

    override lateinit var styleManager: StyleManager
    override var isHoveredOver: Boolean = false

    private var mapOffset: Vector2 = Vector2(0f, 50f)

    private var playerNode: MapNode = MapManager.currentMapNode
    private var playerPos: Vector2 = Vector2(playerNode.x, playerNode.y)
    private var movePlayerTo: MapNode? = null
    private var playerMovementStartTime: Long = 0L

    private val nodeDrawable: Drawable by lazy {
        ResourceManager.get(screen, nodeDrawableHandle)
    }

    private val playerDrawable: Drawable by lazy {
        ResourceManager.get(screen, playerDrawableHandle)
    }

    private val background: Drawable by lazy {
        ResourceManager.get(screen, backgroundHandle)
    }

    private val edgeTexture: TextureRegion by lazy {
        ResourceManager.get(screen, edgeTextureHandle)
    }

    private val directionIndicator: TextureRegion by lazy {
        ResourceManager.get(screen, directionIndicatorHandle)
    }

    private var moveScreenToPoint: Vector2? = null

    private var setupStartButtonListener: Boolean = false

    private var pointToNode: MapNode? = null
    private var lastPointerPosition: Vector2 = Vector2(0f, 0f)
    private var screenDragged: Boolean = false

    private val dragListener = object : DragListener() {

        private var dragStartPosition: Vector2? = null
        private var mapOffsetOnDragStart: Vector2? = null

        override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            super.dragStart(event, x, y, pointer)
            dragStartPosition = Vector2(x, y)
            mapOffsetOnDragStart = mapOffset
            screenDragged = true
        }

        override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            super.drag(event, x, y, pointer)
            val dragStartPosition = dragStartPosition ?: return
            val mapOffsetOnDragStart = mapOffsetOnDragStart ?: return
            val draggedDistance = dragStartPosition - Vector2(x, y)
            mapOffset = mapOffsetOnDragStart - draggedDistance
            moveScreenToPoint = null
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
            lastPointerPosition = Vector2(x, y)
            return super.mouseMoved(event, x, y)
        }

        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            lastTouchDownTime = TimeUtils.millis()
            return super.touchDown(event, x, y, pointer, button)
        }

        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            val screenDragged = screenDragged
            this@DetailMapWidget.screenDragged = false
            if (screenDragged || TimeUtils.millis() > lastTouchDownTime + maxClickTime) return
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
        if (!setupStartButtonListener) {
            screen.namedActorOrError(startButtonName).onButtonClick { onStartButtonClicked() }
            setupStartButtonListener = true
            setupMapEvent(playerNode.event)
        }

        validate()
        updatePlayerMovement()
        updateScreenMovement()
        batch ?: return

        batch.flush()
        val viewport = screen.stage.viewport
        val scissor = Rectangle(
            0f, viewport.bottomGutterHeight.toFloat(),
            (Gdx.graphics.width / 160f) * width,
            ((Gdx.graphics.height - viewport.topGutterHeight - viewport.bottomGutterHeight) / 90f) * height
        )
        if (!ScissorStack.pushScissors(scissor)) return
        drawBackground(batch)
        drawEdges(batch)
        drawNodes(batch)
        drawNodeImages(batch)
        val playerX = x + playerPos.x + mapOffset.x + nodeSize / 2 - playerWidth / 2
        val playerY = y + playerPos.y + mapOffset.y + nodeSize / 2 - playerHeight / 2
        playerDrawable.draw(batch, playerX, playerY, playerWidth, playerHeight)
        drawDirectionIndicator(batch)
        drawDecorations(batch)
        super.draw(batch, parentAlpha)

        batch.flush()
        ScissorStack.popScissors()
    }

    private fun updateScreenMovement() {
        val moveScreenToPoint = moveScreenToPoint ?: return
        val movement = (moveScreenToPoint - mapOffset).withMag(screenSpeed)
        mapOffset += movement
        if (mapOffset.compare(moveScreenToPoint, 6f)) this.moveScreenToPoint = null
    }

    private fun drawBackground(batch: Batch) {
        val minWidth = background.minWidth * backgroundScale
        val minHeight = background.minHeight * backgroundScale
        val amountX = ceil(width / minWidth).toInt() + 2
        val amountY = ceil(height / minHeight).toInt() + 2
        var curX = x - minWidth + (mapOffset.x % minWidth)
        var curY = y - minHeight + (mapOffset.y % minHeight)
        repeat(amountX) {
            repeat(amountY) {
                background.draw(batch, curX, curY, minWidth, minHeight)
                curY += minHeight
            }
            curY = y - minHeight + (mapOffset.y % minHeight)
            curX += minWidth
        }
    }

    private fun drawNodeImages(batch: Batch): Unit = map
        .uniqueNodes
        .filter { it.imageName != null }
        .forEach { node ->
            val image = node.getImage(screen) ?: return@forEach
            val imageData = node.getImageData() ?: return@forEach
            val offset = getNodeImageOffset(node.imagePos ?: return@forEach, imageData.width, imageData.height)
            image.draw(
                batch,
                x + mapOffset.x + node.x + offset.x,
                y + mapOffset.y + node.y + offset.y,
                imageData.width, imageData.height
            )
        }

    private fun getNodeImageOffset(pos: MapNode.ImagePosition, width: Float, height: Float): Vector2 = when (pos) {
        MapNode.ImagePosition.UP -> Vector2(nodeSize / 2 - width / 2, 2 * nodeSize)
        MapNode.ImagePosition.DOWN -> Vector2(nodeSize / 2 - width / 2, -height - nodeSize)
        MapNode.ImagePosition.LEFT -> Vector2(-width - nodeSize, nodeSize / 2 - height / 2)
        MapNode.ImagePosition.RIGHT -> Vector2(2 * nodeSize, nodeSize / 2 - height / 2)
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
            MapManager.currentMapNode = movePlayerTo
            playerPos = Vector2(movePlayerTo.x, movePlayerTo.y)
            setupMapEvent(movePlayerTo.event)
            updateScreenState(movePlayerTo.event)
            this.movePlayerTo = null
            updateDirectionIndicator(lastPointerPosition)
            return
        }
        val percent = (movementFinishTime - curTime) / playerMoveTime.toFloat()
        val movementPath = Vector2(movePlayerTo.x, movePlayerTo.y) - Vector2(playerNode.x, playerNode.y)
        val playerOffset = movementPath * (1f - percent)
        playerPos = Vector2(playerNode.x, playerNode.y) + playerOffset
        val screenWidth = screen.viewport.worldWidth
        val screenHeight = screen.viewport.worldHeight
        val screenRectangle = Rectangle(
            -mapOffset.x - leftScreenSideDeadSection, -mapOffset.y,
            screenWidth - leftScreenSideDeadSection, screenHeight
        )
        if (!screenRectangle.contains(playerPos)) {
            moveScreenToPoint = -playerPos + Vector2(screenWidth, screenHeight) / 2f
        }
    }

    private fun skipPlayerAnimation() {
        val movePlayerTo = movePlayerTo ?: return
        this.playerNode = movePlayerTo
        playerPos = Vector2(movePlayerTo.x, movePlayerTo.y)
        setupMapEvent(movePlayerTo.event)
        updateScreenState(movePlayerTo.event)
        this.movePlayerTo = null
        updateDirectionIndicator(lastPointerPosition)
    }

    private fun setupMapEvent(event: MapEvent?) {
        if (event == null || !event.displayDescription) {
            screen.leaveState(displayEventDetailScreenState)
        } else {
            screen.enterState(displayEventDetailScreenState)
        }
        event ?: return
        TemplateString.updateGlobalParam("map.curEvent.displayName", event.displayName)
        TemplateString.updateGlobalParam("map.curEvent.description", event.descriptionText)
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

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

    companion object {
        const val displayEventDetailScreenState: String = "displayEventDetail"
    }

}