package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.keyInput.selection.SelectionGroup
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.CustomScrollableFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.between
import ktx.actors.alpha
import kotlin.math.max

//TODO (optional):
// widthFitContent and heightFitContent (including and/or exluding posType.absolute)
// positionTop ... for PosType.absolute
// VERY Optional:  FitParent (Fits the child-size within its line i guess and takes as much space as possible for multiple elements)
open class CustomBox(screen: OnjScreen) : CustomGroup(screen), ResourceBorrower, KotlinStyledActor, DisableActor,
    DraggableActor {

    override var positionType: PositionType = PositionType.RELATIV
    override var group: SelectionGroup? = null
    override var isFocusable: Boolean = false //needs touchable enabled to work
        set(value) {
            if (this.isFocused && !value) screen.focusedActor = null
            if (value) touchable = Touchable.enabled
            field = value
        }
    override var isFocused: Boolean = false
    override var isSelected: Boolean = false
    override var isSelectable: Boolean = false
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false
    override var isDisabled: Boolean = false
    override var inDragPreview: Boolean = false

    var verticalAlign: CustomAlign = CustomAlign.START      // top
    var horizontalAlign: CustomAlign = CustomAlign.START    // left

    var minHorizontalDistBetweenElements: Float = 0F
    var minVerticalDistBetweenElements: Float = 0F

    private var invalidSize: Boolean = true

    var flexDirection = FlexDirection.COLUMN
    var wrap = CustomWrap.NONE

    override var marginTop: Float = 0F
    override var marginBottom: Float = 0F
    override var marginLeft: Float = 0F
    override var marginRight: Float = 0F

    var paddingTop: Float = 0F
    var paddingBottom: Float = 0F
    var paddingLeft: Float = 0F
    var paddingRight: Float = 0F


    override var isDraggable: Boolean = false
        set(value) {
            field = value
            isFocusable = true
            isSelectable = true
        }
    override var targetGroups: List<String> = listOf()

    /**
     * only calculate it once per Layout call, not multiple times
     */
    private var cachedChildren: (Pair<List<Pair<Box, Actor>>, List<Pair<Box, Actor>>>)? = null

    init {
        touchable = Touchable.childrenOnly
        bindDefaultListeners(this, screen)
    }

    override fun layout() {
        super.layout()
        val children = childrenAsBoxes()
        checkSize(children)
        val prefWidth = (if (width == 0F) getPrefWidth() else width) - paddingLeft - paddingRight
        val prefHeight = (if (height == 0F) getPrefHeight() else height) - paddingTop - paddingBottom

        if (children.first.isNotEmpty()) {
            if (wrap == CustomWrap.NONE) layoutNoWrap(children.first, prefWidth, prefHeight)
            else layoutWrapped(children.first, prefWidth, prefHeight)
        }

        //TODO MAYBE,only MAYBE integrate the absolute ones into layoutPrefWidth/Height with widthFitContent and heightFitContent or so
        children.second.forEach {
            it.second.setBounds(it.first.x, it.first.y, it.first.w, it.first.h)
        }
        cachedChildren = null
    }

    protected fun checkSize(children: Pair<List<Pair<Box, Actor>>, List<Pair<Box, Actor>>>) {
        if (invalidSize) {
            invalidSize = false
            layoutSize(children.first.map { it.first })
        }
    }

    private fun layoutWrapped(
        children: List<Pair<Box, Actor>>,
        prefWidth: Float,
        prefHeight: Float,
    ) {
        val wrapLines: MutableList<List<Pair<Box, Actor>>> = mutableListOf()
        if (flexDirection.isColumn) {
            var curLine: MutableList<Pair<Box, Actor>> = mutableListOf()
            var curHeight = 0F
            children.forEach {
                if (curHeight != 0F && curHeight + it.first.h > prefHeight) {
                    wrapLines.add(curLine)
                    curLine = mutableListOf()
                    curHeight = 0F
                }
                curHeight += it.first.h
                curLine.add(it)
            }
            if (curLine.isNotEmpty()) wrapLines.add(curLine)

            if (wrap == CustomWrap.WRAP_REVERSE) wrapLines.reverse()
            val biggestPerColumns = wrapLines.map { it2 -> it2.maxOf { it.first.w } }
            val xColumnStarts =
                addDistInBetween(horizontalAlign, biggestPerColumns, prefWidth, minHorizontalDistBetweenElements)
            wrapLines.forEachIndexed { i, it ->
                layoutSingleColumn(it, prefHeight, biggestPerColumns[i], xColumnStarts[i])
            }
        } else {
            var curLine: MutableList<Pair<Box, Actor>> = mutableListOf()
            var curWidth = 0F
            children.forEach {
                if (curWidth != 0F && curWidth + it.first.w > prefWidth) {
                    wrapLines.add(curLine)
                    curLine = mutableListOf()
                    curWidth = 0F
                }
                curWidth += it.first.w
                curLine.add(it)
            }
            if (curLine.isNotEmpty()) wrapLines.add(curLine)

            if (wrap == CustomWrap.WRAP_REVERSE) wrapLines.reverse()
            val biggestPerColumns = wrapLines.map { it2 -> it2.maxOf { it.first.h } }
            val yColumnStarts =
                addDistInBetween(verticalAlign, biggestPerColumns, prefHeight, minVerticalDistBetweenElements)
            wrapLines.forEachIndexed { i, it ->
                layoutSingleRow(it, prefWidth, biggestPerColumns[i], yColumnStarts[i])
            }
        }
    }

    private fun addDistInBetween(align: CustomAlign, data: List<Float>, maxVal: Float, dist: Float): List<Float> {
        return align.getAlignedPos(data.flatMap { listOf(it, dist) }.dropLast(1), maxVal)
            .filterIndexed { i, _ -> ((i and 1) == 0) }
    }

    private fun layoutNoWrap(
        children: List<Pair<Box, Actor>>,
        prefWidth: Float,
        prefHeight: Float,
        offset: Float = 0F
    ) {
        if (flexDirection.isColumn) {
            layoutSingleColumn(children, prefHeight, prefWidth, offset)
        } else {
            layoutSingleRow(children, prefWidth, prefHeight, offset)
        }
    }

    private fun layoutSingleRow(
        children: List<Pair<Box, Actor>>,
        rowWidth: Float,
        rowHeight: Float,
        startY: Float
    ) {
        val flexChildren = if (flexDirection.isReverse) children.reversed() else children
        val xStarts = addDistInBetween(
            horizontalAlign,
            flexChildren.map { it.first.w },
            rowWidth,
            minHorizontalDistBetweenElements
        )
        flexChildren.forEachIndexed { i, it ->
            setChildBounds(
                xStarts[i],
                startY + verticalAlign.getAlignedPos(listOf(it.first.h), rowHeight)[0],
                it.second
            )
        }
    }

    private fun layoutSingleColumn(
        children: List<Pair<Box, Actor>>,
        columnHeight: Float,
        columnWidth: Float,
        startX: Float = 0F
    ) {
        val flexChildren = if (flexDirection.isReverse) children.reversed() else children
        val yStarts = addDistInBetween(
            verticalAlign,
            flexChildren.map { it.first.h },
            columnHeight,
            minVerticalDistBetweenElements
        )
        flexChildren.forEachIndexed { i, it ->
            setChildBounds(
                startX + horizontalAlign.getAlignedPos(listOf(it.first.w), columnWidth)[0],
                yStarts[i],
                it.second
            )
        }
    }

    private fun setChildBounds(
        x: Float,
        y: Float,
        actor: Actor
    ) {
        var x1 = x
        var y1 = y
        if (actor is KotlinStyledActor) {
            x1 += actor.marginLeft
            y1 += actor.marginTop
        }
        if (actor is OffSettable) {
            x1 += actor.logicalOffsetX
            y1 -= actor.logicalOffsetY // I don't know if this is the way
        }

        x1 += paddingLeft // I don't know how well this works with VerticalAlign.End if the children are bigger than the element
        y1 += paddingTop

        // let's hope the "height - y1 - actor.height" doesn't break at some point something (maybe it already does with padding shit)
        actor.setBounds(x1, height - y1 - actor.height, actor.width, actor.height)
    }

    private fun layoutSize(children: List<Box>) {
        if (wrap != CustomWrap.NONE) {
            if (flexDirection.isColumn) {
                layoutPrefHeight = (forcedPrefHeight ?: height)
                layoutPrefWidth = simulateChildrenFast(
                    children,
                    FlexDirection.COLUMN,
                    layoutPrefHeight - paddingTop - paddingBottom,
                    minHorizontalDistBetweenElements
                )
            } else {
                layoutPrefWidth = (forcedPrefWidth ?: width)
                layoutPrefHeight =
                    simulateChildrenFast(
                        children,
                        FlexDirection.ROW,
                        layoutPrefWidth - paddingLeft - paddingRight,
                        minVerticalDistBetweenElements
                    )
            }
        } else {
            if (flexDirection.isColumn) {
                layoutPrefWidth = max(width, (children.maxOfOrNull { it.w } ?: 0F) + paddingLeft + paddingRight)
                layoutPrefHeight = max(
                    height,
                    children.sumOf { it.h.toDouble() }.toFloat() + children.map { minVerticalDistBetweenElements }
                        .dropLast(1).sum() + paddingTop + paddingBottom
                )
            } else {
                layoutPrefWidth = max(
                    width,
                    children.sumOf { it.w.toDouble() }.toFloat() + children.map { minHorizontalDistBetweenElements }
                        .dropLast(1).sum() + paddingLeft + paddingRight
                )
                layoutPrefHeight = max(height, (children.maxOfOrNull { it.h } ?: 0F) + paddingTop + paddingBottom)
            }
        }
    }

    private fun simulateChildrenFast(
        children: List<Box>,
        dir: FlexDirection,
        maxSize: Float,
        distBetweenLine: Float,
    ): Float {

        var curSize = 0F
        var curMaxSizeOtherDir = 0F
        var finalPrefSize = 0F

        fun Box.sd(dir: FlexDirection) = if (dir.isColumn) this.h else this.w   // size of direction (width or height)
        fun FlexDirection.other() = if (this.isColumn) FlexDirection.ROW else FlexDirection.COLUMN

        children.forEach {
            if (curSize != 0F && curSize + it.sd(dir) > maxSize) {
                curSize = 0F
                finalPrefSize += curMaxSizeOtherDir + distBetweenLine
                curMaxSizeOtherDir = 0F

            }
            curSize += it.sd(dir)
            curMaxSizeOtherDir = max(curMaxSizeOtherDir, it.sd(dir.other()))
        }
        if (curSize != 0F) {
            finalPrefSize += curMaxSizeOtherDir
        }
        return finalPrefSize
    }

    /**
     * first element are all "relativ" children (the ones that are placed dynamically), second element all "absolute" children
     */
    protected fun childrenAsBoxes(): Pair<List<Pair<Box, Actor>>, List<Pair<Box, Actor>>> {
        var cachedChildren1 = cachedChildren
        if (cachedChildren1 != null) return cachedChildren1
        val lists =
            notZIndexedChildren.partition { it !is KotlinStyledActor || it.positionType == PositionType.RELATIV }
        cachedChildren1 = lists.first.map {
            var w = it.width
            var h = it.height
            if (it is KotlinStyledActor) {
                w += it.marginLeft
                w += it.marginRight

                h += it.marginBottom
                h += it.marginTop
            }
            Box(0F, 0F, w, h) to it //only w and h are needed
        } to lists.second.map { Box(it.x, it.y, it.width, it.height) to it }
        cachedChildren = cachedChildren1
        return cachedChildren1
    }

    data class Box(val x: Float, val y: Float, val w: Float, val h: Float)


    override fun invalidate() {
        invalidSize = true
        super.invalidate()
    }

    override fun getMinWidth(): Float = forcedPrefWidth ?: super.getMinWidth()
    override fun getMinHeight(): Float = forcedPrefHeight ?: super.getMinHeight()
    override fun getMaxWidth(): Float = forcedPrefWidth ?: super.getMaxWidth()
    override fun getMaxHeight(): Float = forcedPrefHeight ?: super.getMaxHeight()
}


sealed class CustomAlign {
    data object START : CustomAlign() {
        override fun getAlignedPos(sizes: List<Float>, totalSize: Float): List<Float> {
//            if (cur.get() > totalSize) throw RuntimeException("too many objects for Alignment without wrap")
            return sizes.dropLast(1).scan(0F) { acc, fl -> acc + fl }
        }
    }

    data object CENTER : CustomAlign() {
        override fun getAlignedPos(sizes: List<Float>, totalSize: Float): List<Float> {
            val temp = START.getAlignedPos(sizes, totalSize)
            val max = sizes.last() + temp.last()
            val step = (totalSize - max) / 2
            return temp.map { it + step }
        }
    }

    data object END : CustomAlign() {
        override fun getAlignedPos(sizes: List<Float>, totalSize: Float): List<Float> {
            val temp = START.getAlignedPos(sizes, totalSize)
            val max = sizes.last() + temp.last()
            val step = (totalSize - max)
            return temp.map { it + step }
        }
    }

    data object SPACE_BETWEEN : CustomAlign() {
        override fun getAlignedPos(sizes: List<Float>, totalSize: Float): List<Float> {
            val temp = START.getAlignedPos(sizes, totalSize)
            val max = sizes.last() + temp.last()
            if (max > totalSize || sizes.size <= 1) return CENTER.getAlignedPos(sizes, totalSize)
            val step = (totalSize - max) / (sizes.size - 1)
            return temp.mapIndexed { i, it -> it + step * i }
        }
    }

    data object SPACE_AROUND : CustomAlign() {
        override fun getAlignedPos(sizes: List<Float>, totalSize: Float): List<Float> {
            val temp = START.getAlignedPos(sizes, totalSize)
            val max = sizes.last() + temp.last()
            if (max > totalSize) return CENTER.getAlignedPos(sizes, totalSize)
            val step = (totalSize - max) / (sizes.size + 1)
            return temp.mapIndexed { i, it -> it + step * (i + 1) }
        }
    }

    /**
     * takes the widths or height of a few elements and places them into their respective space
     * the sum of the sizes HAVE to be smaller than the total size
     */
    abstract fun getAlignedPos(sizes: List<Float>, totalSize: Float): List<Float>
}

enum class CustomWrap {
    NONE,
    WRAP,
    WRAP_REVERSE,
}

enum class FlexDirection(val isColumn: Boolean, val isReverse: Boolean) {
    ROW(false, false),
    ROW_REVERSE(false, true),
    COLUMN(true, false),
    COLUMN_REVERSE(true, true),
}

enum class CustomDirection(val isHorizontal: Boolean) {
    TOP(false),
    BOTTOM(false),
    LEFT(true),
    RIGHT(true),
}

enum class PositionType {
    ABSOLUTE, RELATIV
}


class CustomScrollableBox(screen: OnjScreen) : CustomBox(screen) {
    // TODO focusable actors within scroll stuff important (especially the focus next part of it)
    // TODO make sure elements outside which are hidden are not shown
    // TODO drag and drop and stuff for children
    /**
     * scrollDirection: LEFT_TO_RIGHT, RIGHT_TO_LEFT, UP_TO_DOWN, DOWN_TO_UP, this allows reverse directions as well
     */
    var scrollDirectionStart: CustomDirection = CustomDirection.TOP
        set(value) {
            field = value
            checkFlexDirection(field)
            invalidate()
        }


    var scrollDistancePerScroll: Float = 30F
    private var scrollBar: Actor? = null
        set(value) {
            if (field != null) removeActor(field)
            field = value
            field?.let { addActor(it) }
        }
    private var scrollBarBackground: Actor? = null
        set(value) {
            if (field != null) removeActor(field)
            field = value
            field?.let { addActor(it) }
        }
    private var scrolledDistance = 0F
        set(value) {
            field = value.between(0F, maxScrollableDistanceInDirection)
            invalidate()
        }
    private var maxScrollableDistanceInDirection = 0F

    private var defaultsForScrollLayout: (() -> Unit)? = null

    private val scrollListener = object : InputListener() {
        override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
            stage.scrollFocus = this@CustomScrollableBox
        }

        override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
            if (x > width || x < 0 || y > height || y < 0) stage?.scrollFocus = null
        }

        override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
            this@CustomScrollableBox.scrolledBy(amountY)
            return true
        }
    }


    var overflowHidden = true //TODO change back to true

    fun scrolledBy(amount: Float) {
        //it just feels wrong if it starts at the bottom to scroll like that, for everything else it is okay
        //TODO ask phillip which is better, let him test it a few times
        if (scrollDirectionStart != CustomDirection.BOTTOM) scrolledDistance += amount * scrollDistancePerScroll
        else scrolledDistance -= amount * scrollDistancePerScroll
    }

    fun setScrollBar(bar: Actor?, barBackground: Actor?) {
        setScrollBar(bar, barBackground, false)
    }

    private fun setScrollBar(
        bar: Actor?,
        barBackground: Actor?,
        fromDefaults: Boolean
    ) {// important that this is private, and the other isn't
        if (!fromDefaults) defaultsForScrollLayout = null
        this.scrollBarBackground = barBackground
        this.scrollBar = bar
        invalidate() //maybe call layout instead of invalidate OR JUST DO IT BETTER (but idk how)
    }

    init {
        addListener(scrollListener)
        touchable = Touchable.enabled
        checkFlexDirection(scrollDirectionStart)
        //draglistener for bar
    }

    // -------------------------------------------------------------------------------------------actual code from here
    fun addScrollbarFromDefaults(
        position: CustomDirection,
        handle: String,
        handleBackground: String? = null,

        barWidth: Float = 15F,
        marginAtStart: Float = 10F,
        marginAtEnd: Float = 10F,
        marginInner: Float = 15F,
        marginOuter: Float = 20F,
    ) {
        val bar = CustomImageActor(handle, screen)
        val barBackground = CustomImageActor(handleBackground, screen)

        bar.positionType = PositionType.ABSOLUTE
        barBackground.positionType = PositionType.ABSOLUTE

        if (position.isHorizontal) {
            if (scrollDirectionStart.isHorizontal) scrollDirectionStart = CustomDirection.TOP
            if (position == CustomDirection.RIGHT) {
                defaultsForScrollLayout = {
                    barBackground.width = barWidth
                    barBackground.height = this.height - marginAtStart - marginAtEnd
                    barBackground.y = marginAtEnd

                    barBackground.x = this.width - barWidth - marginOuter
                    paddingRight = barWidth + marginInner + marginOuter
                }
            } else {
                defaultsForScrollLayout = {
                    barBackground.width = barWidth
                    barBackground.height = this.height - marginAtStart - marginAtEnd
                    barBackground.y = marginAtEnd

                    barBackground.x = marginOuter
                    paddingLeft = barWidth + marginInner + marginOuter
                }
            }
        } else {
            if (!scrollDirectionStart.isHorizontal) scrollDirectionStart = CustomDirection.LEFT
            if (position == CustomDirection.TOP) {
                defaultsForScrollLayout = {
                    barBackground.height = barWidth
                    barBackground.width = this.width - marginAtStart - marginAtEnd
                    barBackground.x = marginAtStart

                    barBackground.y = this.height - barWidth - marginOuter
                    paddingTop = barWidth + marginInner + marginOuter
                }
            } else {
                defaultsForScrollLayout = {
                    barBackground.height = barWidth
                    barBackground.width = this.width - marginAtStart - marginAtEnd
                    barBackground.x = marginAtStart

                    barBackground.y = marginOuter
                    paddingBottom = barWidth + marginInner + marginOuter
                }
            }
        }
        setScrollBar(bar, barBackground, true)
    }

    override fun layout() {
        val children = childrenAsBoxes()
        checkSize(children)
        layoutScrollDistance()
        if (maxScrollableDistanceInDirection != 0F) {
            layoutChildren()
            super.layout()
            defaultsForScrollLayout?.invoke()
            layoutScrollBar()
        } else {
            scrollBar?.isVisible = false
            scrollBarBackground?.isVisible = false
            super.layout()
        }
    }

    private fun layoutScrollDistance() {
        maxScrollableDistanceInDirection = if (scrollDirectionStart.isHorizontal) {
            max(0F, prefWidth - width + paddingLeft + paddingRight)
        } else {
            max(0F, prefHeight - height + paddingTop + paddingBottom)
        }
    }

    private fun layoutScrollBar() {
        val scrollBar = this.scrollBar
        val scrollBarBackground = this.scrollBarBackground
        if (scrollBar == null || scrollBarBackground == null) return

        scrollBar.isVisible = true
        scrollBarBackground.isVisible = true

        var completedPart = scrolledDistance / maxScrollableDistanceInDirection

        if (scrollDirectionStart.isHorizontal) {
            if (wrap == CustomWrap.WRAP_REVERSE) completedPart = 1 - completedPart

            scrollBar.width = width * scrollBarBackground.width / (maxScrollableDistanceInDirection + width)
            scrollBar.y = scrollBarBackground.y
            scrollBar.height = scrollBarBackground.height
            scrollBar.x = scrollBarBackground.x +
                    completedPart * (scrollBarBackground.width - scrollBar.width)
        } else {
            if (wrap != CustomWrap.WRAP_REVERSE) completedPart = 1 - completedPart

            scrollBar.x = scrollBarBackground.x
            scrollBar.width = scrollBarBackground.width
            scrollBar.height = height * scrollBarBackground.height / (maxScrollableDistanceInDirection + height)
            scrollBar.y = scrollBarBackground.y +
                    completedPart * (scrollBarBackground.height - scrollBar.height)
        }
    }

    private fun layoutChildren() {
        var scrolledPart = scrolledDistance
        if (wrap == CustomWrap.WRAP_REVERSE) scrolledPart = -scrolledDistance

        if (scrollDirectionStart.isHorizontal) {
            children.filterIsInstance<OffSettable>().forEach { it.logicalOffsetX = scrolledPart }
        } else children.filterIsInstance<OffSettable>().forEach { it.logicalOffsetY = scrolledPart }
    }

    private fun checkFlexDirection(scrollDirection: CustomDirection) {
        if (scrollDirection.isHorizontal && !flexDirection.isColumn) {
            flexDirection = if (flexDirection.isReverse) {
                FlexDirection.COLUMN_REVERSE
            } else {
                FlexDirection.COLUMN
            }
        } else if (!scrollDirection.isHorizontal && flexDirection.isColumn) {
            flexDirection = if (flexDirection.isReverse) {
                FlexDirection.ROW_REVERSE
            } else {
                FlexDirection.ROW
            }
        }
        wrap =
            if (scrollDirection == CustomDirection.BOTTOM || scrollDirection == CustomDirection.RIGHT) CustomWrap.WRAP_REVERSE
            else CustomWrap.WRAP


        if (scrollDirection.isHorizontal) {
            horizontalAlign = if (scrollDirection == CustomDirection.RIGHT) CustomAlign.END else CustomAlign.START
        } else {
            verticalAlign = if (scrollDirection == CustomDirection.BOTTOM) CustomAlign.END else CustomAlign.START
        }
    }


    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (!overflowHidden) {
            super.draw(batch, parentAlpha)
            return
        }

        batch ?: return
        batch.flush()
        val viewport = screen.stage.viewport
        background?.draw(batch, x, y, width, height)
        if (drawItemsWithScissor(viewport, batch, parentAlpha)) return

        if (maxScrollableDistanceInDirection != 0F) {
            if (isTransform) applyTransform(batch, computeTransform())
            scrollBarBackground?.draw(batch, parentAlpha)
            scrollBar?.draw(batch, alpha)
            if (isTransform) resetTransform(batch)
        }
//        val curChild = this.currentlyDraggedChild //TODO maybe this, this needs to be checked if it is nessessary
//        if (curChild != null) {
//            val coordinates = curChild.parent.localToStageCoordinates(Vector2())
//            curChild.x += coordinates.x
//            curChild.y += coordinates.y
//            curChild.draw(batch, alpha)
//            curChild.x -= coordinates.x
//            curChild.y -= coordinates.y
//        }
    }

    private fun drawItemsWithScissor(
        viewport: Viewport,
        batch: Batch,
        parentAlpha: Float
    ): Boolean {
        val xPixel =
            (Gdx.graphics.width - viewport.leftGutterWidth - viewport.rightGutterWidth) / viewport.worldWidth
        val yPixel =
            (Gdx.graphics.height - viewport.topGutterHeight - viewport.bottomGutterHeight) / viewport.worldHeight
        val pos = localToStageCoordinates(Vector2(0f, 0f))
        val scissor = Rectangle(
            xPixel * (pos.x + paddingLeft) + viewport.leftGutterWidth,
            yPixel * (pos.y + paddingBottom) + viewport.bottomGutterHeight,
            xPixel * (width - paddingLeft - paddingRight),
            yPixel * (height - paddingTop - paddingBottom)
        )
        batch.flush()
        if (!ScissorStack.pushScissors(scissor)) return true
//        currentlyDraggedChild?.isVisible = false
        super.draw(batch, parentAlpha)
//        currentlyDraggedChild?.isVisible = true
        batch.flush()
        ScissorStack.popScissors()
        batch.flush()
        return false
    }


    companion object {
        fun isInsideScrollableParents(actor: Actor, x: Float, y: Float): Boolean { //TODO check if this is actually correct
            var cur: Actor? = actor
            while (cur != null) {
                cur = cur.parent
                if (cur is CustomScrollableBox && cur.maxScrollableDistanceInDirection != 0F) {
                    val coordinates = actor.localToActorCoordinates(cur, Vector2(x, y))
                    if (coordinates.x < 0 || coordinates.y < 0 || coordinates.x > cur.width || coordinates.y > cur.height)
                        return false
                }
            }
            return true
        }
    }
}