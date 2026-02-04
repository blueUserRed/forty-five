package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Affine2
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.screen.actors.*
import com.microwavestudios.fortyfive.utils.AdvancedTextParser
import com.microwavestudios.fortyfive.utils.splitAt
import com.microwavestudios.fortyfive.utils.zip
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.math.sin

open class AdvancedTextWidget(
    private val defaults: Triple<String, Color, Int>,
    screen: CustomScreen,
) : CustomGroup(screen), HasPaddingActor {

    override var fixedZIndex: Int = 0

    override var paddingTop: Float = 0F
    override var paddingBottom: Float = 0F
    override var paddingLeft: Float = 0F
    override var paddingRight: Float = 0F

    //TODO expand functionality of this
    // Space Around and space between not implemented
    var verticalTextAlign: CustomAlign = CustomAlign.END

    //TODO expand functionality of this
    // Space Around and space between not implemented
    var horizontalTextAlign: CustomAlign = CustomAlign.START

    var fitContentHeight: Boolean = false

    open var advancedText: AdvancedText = AdvancedText.EMPTY
        set(value) {
            if (field != AdvancedText.EMPTY) clearText() // to reset before, not after assigning
            field = value
            initText(value)
        }

    var wrap = true

    init {
        @Suppress("LeakingThis")
        initText(advancedText)

        height = Float.MAX_VALUE
    }

    constructor(
        defaults: OnjObject,
        screen: CustomScreen,
    ) : this(AdvancedText.defaultsFromOnj(defaults), screen)

    open fun setRawText(text: String, effects: List<AdvancedTextParser.AdvancedTextEffect>?) {
        advancedText = AdvancedTextParser(text, screen, defaults, effects ?: listOf()).parse()
    }

    override fun layout() {
        super.layout()

        children
            .filterIsInstance<Layout>()
            .onEach(Layout::validate)
            .filter { it !is KotlinStyledActor || it.positionType == PositionType.RELATIVE }
            .forEach { child ->
                child as Actor
                child.width = child.prefWidth
                child.height = child.prefHeight
            }
        var curX = paddingLeft
        val lines = if (wrap) {
            advancedText
                .parts
                .splitAt { part ->
                    val child = part.actor
                    val shouldSplit = curX + child.width > width - paddingRight
                    if (shouldSplit) {
                        curX = paddingLeft
                    } else if (part.breakLine) {
                        curX = width + 1f
                    }
                    curX += child.width
                    shouldSplit
                }
                .map { line -> line.map { it.actor } }
        } else {
            listOf(advancedText.parts.map { it.actor })
        }

        var curY = paddingBottom
        curX = paddingLeft
        lines
            .reversed()
            .zip { line -> line.maxOf { it.height } }
            .forEach { (line, height) ->
                line.forEach { actor ->
                    actor.setPosition(curX, curY)
                    curX += actor.width
                }
                curX = paddingLeft
                curY += height
            }

        alignVerticalTextAfterLayout(lines, curY + paddingTop)


        if (fitContentHeight) {
            height = curY + paddingTop
        } else {
            layoutPrefHeight =
                if ((verticalTextAlign == CustomAlign.SPACE_AROUND || verticalTextAlign == CustomAlign.SPACE_BETWEEN)
                    && curY + paddingTop < height
                ) {
                    height
                } else {
                    curY + paddingTop
                }

        }
        if (!wrap) {
            layoutPrefWidth = advancedText.parts.sumOf { it.actor.width.toDouble() }.toFloat()
        }
        if (lines.isNotEmpty()) {
            if (width == 0F) width = lines.maxOf { it.last().x + it.last().width } + paddingRight
        } else {
            paddingRight
        }
        alignHorizontalTextAfterLayout(lines)
    }

    private fun alignHorizontalTextAfterLayout(lines: List<List<Actor>>) {
        lines.forEach {
            val diff = width - it.maxOf { it.x + it.width }
            when (horizontalTextAlign) {
                CustomAlign.START -> {} //this is default, so no changes
                CustomAlign.CENTER -> it.forEach { it.setPosition(it.x + diff / 2, it.y) }
                CustomAlign.END -> it.forEach { it.setPosition(it.x + diff, it.y) }
                CustomAlign.SPACE_AROUND -> TODO()
                CustomAlign.SPACE_BETWEEN -> TODO()
            }
        }
    }

    private fun alignVerticalTextAfterLayout(lines: List<List<Actor>>, totalHeight: Float) {
        if (fitContentHeight) return
        val diff = height - totalHeight
        when (verticalTextAlign) {
            CustomAlign.START -> lines.forEach { it2 -> it2.forEach { it.setPosition(it.x, it.y + diff) } }
            CustomAlign.CENTER -> lines.forEach { it2 -> it2.forEach { it.setPosition(it.x, it.y + diff / 2) } }
            CustomAlign.END -> {} //this is default, so no changes
            CustomAlign.SPACE_AROUND -> TODO()
            CustomAlign.SPACE_BETWEEN -> TODO()
        }
    }

    private fun initText(value: AdvancedText) {
        value
            .parts
            .forEach { part -> addActor(part.actor) }
        invalidateHierarchy()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        advancedText.update()
        super.draw(batch, parentAlpha)
        invalidate()
    }

    private fun clearText() = advancedText.parts.forEach {
        removeActor(it.actor)
    }
}

data class AdvancedText(
    val parts: List<AdvancedTextPart>
) {

    private var currentPartIndex: Int = 0

    fun progress(): Boolean {
        if (currentPartIndex >= parts.size) return true
        val finished = parts[currentPartIndex].progress()
        if (!finished) return false
        currentPartIndex++
        return currentPartIndex >= parts.size
    }

    fun update() = parts.forEach { it.update() }

    fun resetProgress() {
        currentPartIndex = 0
        parts.forEach { it.resetProgress() }
    }

    companion object {
        val EMPTY = AdvancedText(listOf())

        fun readFromOnj(
            rawText: String,
            effects: OnjArray?,
            screen: CustomScreen,
            defaults: OnjObject
        ): AdvancedText = AdvancedTextParser(
            rawText,
            screen,
            defaultsFromOnj(defaults),
            effects
                ?.value
                ?.map { AdvancedTextParser.AdvancedTextEffect.getFromOnj(it as OnjNamedObject) }
                ?: listOf()
        ).parse()

        fun defaultsFromOnj(onj: OnjObject): Triple<String, Color, Int> = Triple(
            onj.get<String>("font"),
            onj.get<Color>("color"),
            onj.get<Long>("fontSize").toInt()
        )
    }

}

interface AdvancedTextPart : OffSettable {

    val actor: Actor

    val breakLine: Boolean

    fun progress(): Boolean
    fun resetProgress()
    fun addDialogAction(action: AdvancedTextPart.() -> Unit)

    fun calcTransformationMatrixForOffsets(oldTransform: Matrix4): Matrix4 {
        val worldTransform = Affine2()
        worldTransform.set(oldTransform)
        worldTransform.translate(drawOffsetX ?: 0f, drawOffsetY ?: 0f)
        val computed = Matrix4()
        computed.set(worldTransform)
        return computed
    }

    fun update() {}

}

class TextAdvancedTextPart(
    private val rawText: String,
    font: String,
    fontColor: Color,
    fontSize: Int,
    screen: CustomScreen,
    override val breakLine: Boolean
) : NewLabel(
    screen,
    rawText,
), AdvancedTextPart {

    override val actor: Actor = this

    var progress: Int = text.length
        private set

    private val actions: MutableList<AdvancedTextPart.() -> Unit> = mutableListOf()

    init {
        this.fontSize = fontSize
        this.fontGroup = font
        this.fontColor = fontColor
    }

    override fun addDialogAction(action: AdvancedTextPart.() -> Unit) {
        actions.add(action)
    }

    override var drawOffsetX: Float = 0F
    override var drawOffsetY: Float = 0F
    override var logicalOffsetX: Float = 0F
    override var logicalOffsetY: Float = 0F

    override fun progress(): Boolean {
        progress++
        if (progress > rawText.length) return true
        setText(rawText.take(progress))
        return progress >= rawText.length
    }

    override fun resetProgress() {
        progress = 0
        setText("")
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {

        actions.forEach { it(this) }
        if (batch == null) {
            super.draw(null, parentAlpha)
            return
        }

        val shouldTransform = drawOffsetX != 0f || drawOffsetY != 0f

        val oldTransform = batch.transformMatrix.cpy()
        if (shouldTransform) {
            batch.transformMatrix = calcTransformationMatrixForOffsets(oldTransform)
        }

        super.draw(batch, parentAlpha)

        if (shouldTransform) {
            batch.transformMatrix = oldTransform
        }
    }
}

class IconAdvancedTextPart(
    private val resourceHandle: ResourceHandle,
    screen: CustomScreen,
    private val fontSize: Int,
    override val breakLine: Boolean
) : CustomImageActor(resourceHandle, screen), AdvancedTextPart, ResourceBorrower {


    override val actor: Actor = this

    private var isShown: Boolean = true

    private var iconHeight: Float = 0f
    private var iconWidth: Float = 0f

    private val actions: MutableList<AdvancedTextPart.() -> Unit> = mutableListOf()

    private var calculatedLayout = false

    init {
        reportDimensionsWithScaling = true
        ignoreScalingWhenDrawing = true
    }

    private fun recalcLayout() {
        iconHeight = fontSize.toFloat()
        val drawable = loadedDrawable!!
        val aspectRatio = drawable.minWidth / drawable.minHeight
        iconWidth = aspectRatio * iconHeight
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        actions.forEach { it(this) }
        super.draw(batch, parentAlpha)
    }

    override fun update() {
        if (calculatedLayout && !isVisible) {
            isVisible = true
        }
        if (!calculatedLayout && loadedDrawable != null && isShown) {
            recalcLayout()
            calculatedLayout = true
            invalidateHierarchy()
        }
    }

    override fun progress(): Boolean {
        isShown = true
        return true
    }

    override fun resetProgress() {
        isShown = false
        calculatedLayout = false
        iconWidth = 0f
        iconHeight = 0f
    }

    override fun addDialogAction(action: AdvancedTextPart.() -> Unit) {
        actions.add(action)
    }

    override fun getMinHeight(): Float = iconHeight
    override fun getPrefHeight(): Float = iconHeight
    override fun getMaxHeight(): Float = iconHeight

    override fun getMinWidth(): Float = iconWidth
    override fun getPrefWidth(): Float = iconWidth
    override fun getMaxWidth(): Float = iconWidth

    override fun getWidth(): Float = iconWidth
    override fun getHeight(): Float = iconHeight
}

object AdvancedTextPartActionFactory {

    private val actions: Map<String, (onj: OnjObject) -> AdvancedTextPart.() -> Unit> = mapOf(
        "ShakeTextAction" to { onj ->
            val xSpeed = onj.get<Double>("xSpeed").toFloat()
            val xMagnitude = onj.get<Double>("xMagnitude").toFloat()
            val ySpeed = onj.get<Double>("ySpeed").toFloat()
            val yMagnitude = onj.get<Double>("yMagnitude").toFloat()
            ;
            {
                drawOffsetX = sin(TimeUtils.millis().toDouble() * xSpeed).toFloat() * xMagnitude
                drawOffsetY = sin(TimeUtils.millis().toDouble() * ySpeed + Math.PI.toFloat()).toFloat() * yMagnitude
            }
        }
    )

    fun getAction(onj: OnjNamedObject): AdvancedTextPart.() -> Unit = actions[onj.name]?.invoke(onj)
        ?: throw RuntimeException("unknown dialog action: ${onj.name}")

}
