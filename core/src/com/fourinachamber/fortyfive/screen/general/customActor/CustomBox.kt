package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import kotlin.math.max

//TODO (optional):
// widthFitContent and heightFitContent (including and/or exluding posType.absolute)
// positionTop ... for PosType.absolute
// VERY Optional:  FitParent (Fits the child-size within its line i guess and takes as much space as possible for multiple elements)
class CustomBox(screen: OnjScreen) : CustomGroup(screen), ResourceBorrower, KotlinStyledActor {

    override var positionType: PositionType = PositionType.RELATIV

    var verticalAlign: CustomAlign = CustomAlign.START      // top
    var horizontalAlign: CustomAlign = CustomAlign.START    // left

    var minHorizontalDistBetweenElements: Float = 0F
    var minVerticalDistBetweenElements: Float = 0F

    private var invalidSize: Boolean = true

    var flexDirection = FlexDirection.COLUMN
    var wrap = CustomWrap.NONE

    override var marginTop: Float=0F
    override var marginBottom: Float=0F
    override var marginLeft: Float=0F
    override var marginRight: Float=0F

    var paddingTop: Float=0F
    var paddingBottom: Float=0F
    var paddingLeft: Float=0F
    var paddingRight: Float=0F


    init {
        touchable = Touchable.childrenOnly
    }

    override fun layout() {
        super.layout()
        val children = childrenAsBoxes()
        if (invalidSize) {
            invalidSize = false
            layoutSize(children.first.map { it.first })
        }
        val prefWidth = (if (width == 0F) getPrefWidth() else width) - paddingLeft - paddingRight
        val prefHeight = (if (height == 0F) getPrefHeight() else height) - paddingTop - paddingBottom
        if (prefHeight > 500) {
            println()
        }

        if (children.first.isNotEmpty()) {
            if (wrap == CustomWrap.NONE) layoutNoWrap(children.first, prefWidth, prefHeight)
            else layoutWrapped(children.first, prefWidth, prefHeight)
        }

        //TODO MAYBE,only MAYBE integrate the absolute ones into layoutPrefWidth/Height with widthFitContent and heightFitContent or so
        children.second.forEach {
            it.second.setBounds(it.first.x, it.first.y, it.first.w, it.first.h)
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
                layoutSingleRow(it, prefWidth, biggestPerColumns[i],  yColumnStarts[i])
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
            y1 -= actor.logicalOffsetY // i don't know if this is the way
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
                        minVerticalDistBetweenElements)
            }
        } else {
            if (flexDirection.isColumn) {
                layoutPrefWidth = max(width, (children.maxOfOrNull { it.w } ?: 0F)+paddingLeft+paddingRight)
                layoutPrefHeight = max(height, children.sumOf { it.h.toDouble() }.toFloat() + children.map { minHorizontalDistBetweenElements }.dropLast(1).sum() + paddingTop + paddingBottom)
            } else {
                layoutPrefWidth = max(width, children.sumOf { it.w.toDouble() }.toFloat() + children.map { minVerticalDistBetweenElements }.dropLast(1).sum() +paddingLeft+paddingRight)
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
    private fun childrenAsBoxes(): Pair<List<Pair<Box, Actor>>, List<Pair<Box, Actor>>> {
        val lists = notZIndexedChildren.partition { it !is KotlinStyledActor || it.positionType == PositionType.RELATIV }
        return lists.first.map {
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

enum class CustomDirection {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
}

enum class PositionType {
    ABSOLUTE, RELATIV
}