package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.animation.AnimationDrawable
import com.fourinachamber.fortyfive.animation.createAnimation
import com.fourinachamber.fortyfive.game.GameDirector
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.statusbar.StatusbarWidget
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.rendering.MapDebugMenuPage
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.BackgroundActor
import com.fourinachamber.fortyfive.screen.general.customActor.DisableActor
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.screen.general.styles.addMapStyles
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjString
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
    private val defaultNodeDrawableHandle: ResourceHandle,
    private val edgeTextureHandle: ResourceHandle,
    private val playerDrawableHandle: ResourceHandle,
    private val playerWidth: Float,
    private val playerHeight: Float,
    private val playerHeightOffset: Float,
    private val nodeSize: Float,
    private val lineWidth: Float,
    private val playerMoveTime: Int,
    private val directionIndicatorHandle: ResourceHandle,
    private val startButtonName: String,
    private val encounterModifierParentName: String,
    private val encounterModifierDisplayTemplateName: String,
    private var screenSpeed: Float,
    private val scrollMargin: Float,
    private val disabledDirectionIndicatorAlpha: Float,
    private val mapScale: Float
) : Widget(), ZIndexActor, StyledActor, BackgroundActor, ResourceBorrower {

    override var fixedZIndex: Int = 0

    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    private val mapBounds: Rectangle by lazy {
        val nodes = map.uniqueNodes.map { scaledNodePos(it) }
        val lowX = nodes.minOf { it.x }
        val lowY = nodes.minOf { it.y }
        val highX = nodes.maxOf { it.x }
        val highY = nodes.maxOf { it.y }
        Rectangle(lowX, lowY, highX - lowX, highY - lowY)
    }

    var mapOffset: Vector2 = Vector2(50f, 50f)
        private set(value) {
            val bounds = mapBounds
            val lowX = bounds.x
            val lowY = bounds.y
            val highX = bounds.x + bounds.width
            val highY = bounds.y + bounds.height
            field = value.clampIndividual(
                -highX + width / 2 - scrollMargin, -lowX + width / 2 + scrollMargin,
                -highY + height / 2 - scrollMargin, -lowY + height / 2 + scrollMargin
            )
        }

    private var playerNode: MapNode = MapManager.currentMapNode
    private var playerPos: Vector2 = scaledNodePos(playerNode)
    private var movePlayerTo: MapNode? = null
    private var playerMovementStartTime: Long = 0L

    private val nodeDrawable: Promise<Drawable> = ResourceManager.request(this, screen, defaultNodeDrawableHandle)
    private val playerDrawable: Promise<Drawable> = ResourceManager.request(this, screen, playerDrawableHandle)

    override var backgroundHandle: ResourceHandle? = null
        set(value) {
            field = value
            if (background != null) return
            background = if (value == null) {
                null
            } else {
                ResourceManager.request(this, screen, value)
            }
        }

    private var background: Promise<Drawable>? = null

    var backgroundScale: Float? = null
        set(value) {
            if (field == null) {
                field = value
            }
        }

    private val edgeTexture: Promise<TextureRegion> = ResourceManager.request(this, screen, edgeTextureHandle)
    private val directionIndicator: Promise<TextureRegion> = ResourceManager.request(this, screen, directionIndicatorHandle)

    private var moveScreenToPoint: Vector2? = null

    private var setupStartButtonListener: Boolean = false

    private var pointToNode: MapNode? = null
    private var lastPointerPosition: Vector2 = Vector2(0f, 0f)
    private var screenDragged: Boolean = false

    // I hate this
    private val animatedDecorations: List<Pair<DetailMap.MapDecoration, List<Triple<Vector2, Float, AnimationDrawable>>>> = map
        .animatedDecorations
        .zip { decoration ->
            decoration.instances.map { Triple(it.first, it.second, createDecorationAnimation(decoration.drawableHandle)) }
        }

    private val encounterModifierParent: CustomFlexBox by lazy {
        screen.namedActorOrError(encounterModifierParentName) as? CustomFlexBox
            ?: throw RuntimeException("actor named $encounterModifierParentName must be a CustomFlexBox")
    }


    private val dragListener = object : DragListener() {

        private var dragStartPosition: Vector2? = null
        private var mapOffsetOnDragStart: Vector2? = null

        override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            if (StatusbarWidget.OVERLAY_NAME in screen.screenState) return
            if (!map.scrollable) return
            super.dragStart(event, x, y, pointer)
            dragStartPosition = Vector2(x, y)
            mapOffsetOnDragStart = mapOffset
            screenDragged = true
        }

        override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            if (StatusbarWidget.OVERLAY_NAME in screen.screenState) return
            if (!map.scrollable) return
            super.drag(event, x, y, pointer)
            val dragStartPosition = dragStartPosition ?: return
            val mapOffsetOnDragStart = mapOffsetOnDragStart ?: return
            val draggedDistance = dragStartPosition - Vector2(x, y)
            mapOffset = mapOffsetOnDragStart - draggedDistance
            moveScreenToPoint = null
        }

        override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            if (StatusbarWidget.OVERLAY_NAME in screen.screenState) return
            if (!map.scrollable) return
            super.dragStop(event, x, y, pointer)
            dragStartPosition = null
            mapOffsetOnDragStart = null
        }
    }

    private val clickListener = object : ClickListener() {

        val maxClickTime: Long = 300

        private var lastTouchDownTime: Long = 0

        override fun mouseMoved(event: InputEvent?, x: Float, y: Float): Boolean {
            if (StatusbarWidget.OVERLAY_NAME in screen.screenState) return false
            updateDirectionIndicator(Vector2(x, y))
            lastPointerPosition = Vector2(x, y)
            return super.mouseMoved(event, x, y)
        }

        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            if (StatusbarWidget.OVERLAY_NAME in screen.screenState) return false
            lastTouchDownTime = TimeUtils.millis()
            return super.touchDown(event, x, y, pointer, button)
        }

        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            if (StatusbarWidget.OVERLAY_NAME in screen.screenState) return
            val screenDragged = screenDragged
            this@DetailMapWidget.screenDragged = false
            if (screenDragged || TimeUtils.millis() > lastTouchDownTime + maxClickTime) return
            if (movePlayerTo != null) {
                finishMovement()
                return
            }
            pointToNode?.let { goToNode(it) }
        }
    }

    val debugMenuPage = MapDebugMenuPage()
    private var walkEverywhere: Boolean by debugMenuPage.walkEverywhere

    init {
        map.decorations.forEach { it.requestDrawable(screen, this) }
        bindHoverStateListeners(this)
        addListener(dragListener)
        addListener(clickListener)
        invalidateHierarchy()

        animatedDecorations.forEach { (_, instances) ->
            instances.forEach { (_, _, animation) ->
                animation.start()
            }
        }

        // doesn't work when the map doesn't take up most of the screenspace, but width/height
        // are not initialised yet
        val nodePos = scaledNodePos(playerNode)
        val idealPos = -nodePos + Vector2(screen.viewport.worldWidth, screen.viewport.worldHeight) / 2f
        mapOffset.set(
            if (map.scrollable) idealPos else map.camPosOffset
        )
    }

    fun moveToNextNode(mapNode: MapNode) {
        if (movePlayerTo != null) {
            finishMovement()
            return
        }
        goToNode(mapNode)
    }

    override fun act(delta: Float) {
        super.act(delta)
        animatedDecorations.forEach { (_, drawables) ->
            drawables.forEach { (_, _, drawable) -> drawable.update() }
        }
    }

    fun onStartButtonClicked(startButton: Actor? = null) {
        val btn = startButton ?: screen.namedActorOrError(startButtonName)
        if (btn is DisableActor && btn.isDisabled) return
        if (StatusbarWidget.OVERLAY_NAME in screen.screenState) return
        if (playerNode.event?.canBeStarted?.not() ?: true) return
        playerNode.event?.start()
    }

    private fun updateDirectionIndicator(pointerPosition: Vector2) {
        var bestMatch: MapNode? = null
        var bestMatchValue = -1f
        val playerNodePosition = scaledNodePos(playerNode)
        val mouseDirection = ((pointerPosition - mapOffset) - playerNodePosition).unit
        playerNode.edgesTo.forEach { node ->
            val nodeDirection = (scaledNodePos(node) - playerNodePosition).unit
            val result = mouseDirection dot nodeDirection
            if (result > bestMatchValue) {
                bestMatch = node
                bestMatchValue = result
            }
        }
        pointToNode = bestMatch
    }

    private fun createDecorationAnimation(name: String): AnimationDrawable = when (name) {

        "sheep" -> createAnimation(this, screen) {
            val anim = deferredAnimation("map_decoration_sheep_animation")
            val still = stillFrame("map_decoration_bewitched_forest_sheep_1", 500)
            order {
                if (Utils.coinFlip(0.5f)) flipX()
                while (true) yield(if (Utils.coinFlip(0.1f)) anim else still)
            }
        }

        "tree" -> createAnimation(this, screen) {
            val anim = deferredAnimation("map_decoration_tree_animation")
            val still = stillFrame("map_decoration_bewitched_forest_tree1", 100)
            val cycleOffset = (0..30).random()
            order {
                while (true) {
                    val now = System.currentTimeMillis()
                    val curCycle = (now % 10_000).toInt()
                    if (curCycle >= 9_000) {
                        repeat(cycleOffset) { yield(still) }
                        yield(anim)
                    } else {
                        yield(still)
                    }
                }
            }
        }

        "grass" -> createAnimation(this, screen) {
            val anim = deferredAnimation("map_decoration_grass_animation")
            order {
                loop(anim)
            }
        }

        else -> throw RuntimeException("unknown animated decoration: $name")
    }

    private fun goToNode(node: MapNode) {
        val lastMapNode = MapManager.lastMapNode
        if (lastMapNode == null || !lastMapNode.isLinkedTo(playerNode)) {
            FortyFiveLogger.warn(logTag, "lastMapNode is $lastMapNode; currentNode = $playerNode")
        }
        if (!walkEverywhere && !canGoTo(node)) return
        movePlayerTo = node
        playerMovementStartTime = TimeUtils.millis()
        val nodePos = scaledNodePos(node)
        val idealPos = -nodePos + Vector2(width, height) / 2f
        SoundPlayer.situation("walk", screen)
        if (idealPos.compare(mapOffset, epsilon = 200f) || !map.scrollable) return
        moveScreenToPoint = idealPos
    }

    private fun canGoTo(node: MapNode): Boolean {
        val lastNode = MapManager.lastMapNode
        if (lastNode == null || !lastNode.isLinkedTo(playerNode)) return true // trap player ? idk
        if (!playerNode.isLinkedTo(node)) return false
        if (node == lastNode) return true
        if (playerNode.event?.currentlyBlocks == true) return false
        return true
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (!setupStartButtonListener) {
            val startButton = screen.namedActorOrError(startButtonName)
            startButton.onButtonClick {
                onStartButtonClicked(startButton)
            }
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
            (Gdx.graphics.width / viewport.worldWidth) * width,
            ((Gdx.graphics.height - viewport.topGutterHeight - viewport.bottomGutterHeight) / viewport.worldHeight) * height
        )
        if (!ScissorStack.pushScissors(scissor)) return
        drawBackground(batch)
        drawBackgroundDecorations(batch)
        drawEdges(batch)
        drawNodes(batch)
        drawForegroundDecorations(batch)
        drawAnimatedDecorations(batch)
        drawDirectionIndicator(batch)
        drawNodeImages(batch)
        val playerX = x + playerPos.x + mapOffset.x + nodeSize / 2 - playerWidth / 2
        val playerY = y + playerPos.y + mapOffset.y + nodeSize / 2 - playerHeight / 2
        playerDrawable.getOrNull()?.draw(batch, playerX, playerY + playerHeightOffset, playerWidth, playerHeight)
        super.draw(batch, parentAlpha)

        batch.flush()
        ScissorStack.popScissors()
    }

    private fun updateScreenMovement() {
        if (!map.scrollable) return
        val moveScreenToPoint = moveScreenToPoint ?: return
        val movement = (moveScreenToPoint - mapOffset).withMag(screenSpeed)
        mapOffset += movement
        if (mapOffset.compare(moveScreenToPoint, 60f)) this.moveScreenToPoint = null
    }

    private fun drawBackground(batch: Batch) {
        val background = background?.getOrNull() ?: return
        val minWidth = background.minWidth * (backgroundScale ?: 1F)
        val minHeight = background.minHeight * (backgroundScale ?: 1F)
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
            val (nodeX, nodeY) = scaledNodePos(node)
            image.getOrNull()?.draw(
                batch,
                x + mapOffset.x + nodeX + offset.x,
                y + mapOffset.y + nodeY + offset.y,
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
        val indicatorOffset = 120f

        val (pointToNodeX, pointToNodeY) = scaledNodePos(pointToNode)
        val (nodeX, nodeY) = scaledNodePos(node)

        val yDiff = pointToNodeY - nodeY
        val xDiff = pointToNodeX - nodeX
        val length = (Vector2(pointToNodeX, pointToNodeY) - Vector2(nodeX, nodeY)).len()
        val angleRadians = asin((yDiff / length).toDouble()).toFloat()

        val dy = sin(angleRadians) * indicatorOffset
        var dx = cos(angleRadians) * indicatorOffset
        if (xDiff < 0) dx = -dx

        val canGoToNode = canGoTo(pointToNode)
        val old = batch.color.cpy()
        if (!canGoToNode) {
            batch.flush()
            batch.setColor(old.r, old.g, old.b, disabledDirectionIndicatorAlpha)
        }
        directionIndicator.getOrNull()?.let { directionIndicator ->
            batch.draw(
                directionIndicator,
                x + nodeX + mapOffset.x + dx,
                y + nodeY + mapOffset.y + dy,
                (directionIndicator.regionWidth * 0.1f) / 2,
                (directionIndicator.regionHeight * 0.1f) / 2,
                directionIndicator.regionWidth * 0.1f,
                directionIndicator.regionHeight * 0.1f,
                1f,
                1f,
                if (xDiff >= 0) angleRadians.degrees else 360f - angleRadians.degrees + 180f,
            )
        }
        if (!canGoToNode) {
            batch.flush()
            batch.color = old
        }
    }

    fun screenSpacePlayerBounds(): Rectangle {
        val playerPos = scaledNodePos(playerNode) + mapOffset
        return Rectangle(playerPos.x, playerPos.y, playerHeight, playerWidth)
    }

    private fun drawForegroundDecorations(batch: Batch) {
        val (offX, offY) = mapOffset
        val decorations = map.decorations.filter { !it.drawInBackground }
        decorations.forEach { decoration ->
            drawDecoration(decoration, batch, offX, offY)
        }
    }

    private fun drawBackgroundDecorations(batch: Batch) {
        val (offX, offY) = mapOffset
        val decorations = map.decorations.filter { it.drawInBackground }
        decorations.forEach { decoration ->
            drawDecoration(decoration, batch, offX, offY)
        }
    }

    private fun drawDecoration(
        decoration: DetailMap.MapDecoration,
        batch: Batch,
        offX: Float,
        offY: Float
    ) {
        val drawable = decoration.getDrawable(screen, this)
        val width = decoration.baseWidth
        val height = decoration.baseHeight
        decoration.instances.forEach { instance ->
            drawable.getOrNull()?.draw(
                batch,
                x + offX + instance.first.x * mapScale, y + offY + instance.first.y * mapScale,
                width * instance.second * mapScale, height * instance.second * mapScale
            )
        }
    }

    private fun drawAnimatedDecorations(batch: Batch) {
        val (offX, offY) = mapOffset
        animatedDecorations.forEach { (decoration, instances) ->
            val width = decoration.baseWidth
            val height = decoration.baseHeight
            instances.forEach { (position, scale, drawable) ->
                drawable.draw(
//                map.decorations[0].getDrawable(screen).draw(
                    batch,
                    x + offX + position.x * mapScale, y + offY + position.y * mapScale,
                    width * scale * mapScale, height * scale * mapScale
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
            finishMovement()
            return
        }
        val percent = (movementFinishTime - curTime) / playerMoveTime.toFloat()
        val movementPath = scaledNodePos(movePlayerTo) - scaledNodePos(playerNode)
        val playerOffset = movementPath * (1f - percent)
        playerPos = scaledNodePos(playerNode) + playerOffset
    }

    private fun finishMovement() {
        val movePlayerTo = movePlayerTo ?: return
        val playerNode = playerNode
        this.playerNode = movePlayerTo
        MapManager.currentMapNode = movePlayerTo
        MapManager.lastMapNode = playerNode
        playerPos = scaledNodePos(movePlayerTo)
        setupMapEvent(movePlayerTo.event)
        updateScreenState(movePlayerTo.event)
        this.movePlayerTo = null
        updateDirectionIndicator(lastPointerPosition)
    }

    private fun setupMapEvent(event: MapEvent?) {
        if (event == null || !event.displayDescription) {
            screen.leaveState(displayEventDetailScreenState)
            screen.leaveState(eventCanBeStartedScreenState)
        } else {
            screen.enterState(displayEventDetailScreenState)
        }
        event ?: return
        if (event.canBeStarted) {
            screen.enterState(eventCanBeStartedScreenState)
        } else {
            screen.leaveState(eventCanBeStartedScreenState)
        }
        TemplateString.updateGlobalParam("map.cur_event.displayName", event.displayName)
        TemplateString.updateGlobalParam("map.cur_event.buttonText", event.buttonText)
        TemplateString.updateGlobalParam(
            "map.cur_event.description",
            if (event.isCompleted) event.completedDescriptionText else event.descriptionText
        )
        screen.removeAllStyleManagersOfChildren(encounterModifierParent)
        encounterModifierParent.clear()
        screen.enterState(noEncounterModifierScreenState)
        if (event !is EncounterMapEvent) return
        val encounter = GameDirector.encounters[event.encounterIndex]
        val encounterModifiers = encounter.encounterModifier
        if (encounterModifiers.isNotEmpty()) screen.leaveState(noEncounterModifierScreenState)
        encounterModifiers.forEach { modifier ->
            screen.screenBuilder.generateFromTemplate(
                encounterModifierDisplayTemplateName,
                mapOf(
                    "symbol" to OnjString(GraphicsConfig.encounterModifierIcon(modifier)),
                    "modifierName" to OnjString(GraphicsConfig.encounterModifierDisplayName(modifier)),
                    "modifierDescription" to OnjString(GraphicsConfig.encounterModifierDescription(modifier)),
                ),
                encounterModifierParent,
                screen
            )!!
        }
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
        val shaderPromise = visitedNodeShader
        val nodeDrawer: (MapNode) -> Unit = { node ->
            val (nodeX, nodeY) = scaledNodePos(node) + mapOffset
            val drawable = node.getNodeTexture(screen) ?: nodeDrawable
            drawable.getOrNull()?.draw(batch, x + nodeX, y + nodeY, nodeSize, nodeSize)
        }
        val (grayNodes, normalNodes) = uniqueNodes.splitInTwo { it.event?.canBeStarted?.not() ?: false }
        batch.flush()
        shaderPromise.getOrNull()?.let { shader ->
            shader.shader.bind()
            shader.prepare(screen)
            batch.shader = shader.shader
            grayNodes.forEach(nodeDrawer)
            batch.flush()
            batch.shader = null
        }
        normalNodes.forEach(nodeDrawer)
    }

    // TODO: remove
    private val visitedNodeShader: Promise<BetterShader> = ResourceManager.request(this, screen, "grayscale_shader")

    // TODO: remove
    private val edgeShader: Promise<BetterShader> = ResourceManager.request(this, screen, "map_edge_shader")

    private fun drawEdges(batch: Batch) {
        val uniqueEdges = map.uniqueEdges
        batch.flush()
        val edgeShader = edgeShader.getOrNull() ?: return
        val edgeTexture = edgeTexture.getOrNull() ?: return
        edgeShader.prepare(screen)
        batch.shader = edgeShader.shader
        for ((node1, node2) in uniqueEdges) {
            val node1Pos = scaledNodePos(node1)
            val node2Pos = scaledNodePos(node2)
            val dy = node2Pos.y - node1Pos.y
            val dx = node2Pos.x - node1Pos.x
            val length = Vector2(dx, dy).len()
            var angle = Math.toDegrees(asin((dy / length).toDouble())).toFloat() - 90f
            if (dx < 0) angle = 360 - angle
            edgeShader.shader.setUniformf("u_lineLength", length)
            batch.draw(
                edgeTexture,
                x + node1Pos.x + mapOffset.x + nodeSize / 2 - lineWidth / 2,
                y + node1Pos.y + mapOffset.y + nodeSize / 2,
                lineWidth / 2, 0f,
                lineWidth,
                length,
                1.0f, 1.0f,
                angle
            )
            batch.flush()
        }
        batch.shader = null
    }

    private fun scaledNodePos(node: MapNode): Vector2 = Vector2(node.x, node.y) * mapScale

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
        addMapStyles(screen)
    }

    companion object {
        const val displayEventDetailScreenState: String = "displayEventDetail"
        const val eventCanBeStartedScreenState: String = "canStartEvent"
        const val noEncounterModifierScreenState: String = "noEncounterModifier"
        const val logTag = "Map"
    }

}