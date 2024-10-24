package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.Affine2
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.HasPaddingActor
import com.fourinachamber.fortyfive.screen.general.customActor.HoverStateActor
import com.fourinachamber.fortyfive.screen.general.customActor.OffSettable
import com.fourinachamber.fortyfive.screen.general.styles.*
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.math.sin

open class AdvancedTextWidget(
    private val defaults: Triple<String, Color, Float>,
    screen: OnjScreen,
    private val isDistanceField: Boolean,
) : CustomGroup(screen), HoverStateActor, StyledActor, HasPaddingActor {

    override var fixedZIndex: Int = 0

    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false
    override var styleManager: StyleManager? = null

    override var paddingTop: Float = 0F
    override var paddingBottom: Float = 0F
    override var paddingLeft: Float = 0F
    override var paddingRight: Float = 0F

    //TODO expand functionality of this
    // Space Around and space between not implemented
    var verticalTextAlign: CustomAlign = CustomAlign.END

    var fitContentHeight: Boolean = false

    open var advancedText: AdvancedText = AdvancedText.EMPTY
        set(value) {
            if (field != AdvancedText.EMPTY) clearText() // to reset before, not after assigning
            field = value
            initText(value)
        }

    private var initialisedStyleInstruction: Boolean = false

    init {
        @Suppress("LeakingThis")
        bindHoverStateListeners(this)
        @Suppress("LeakingThis")
        initText(advancedText)

        height = Float.MAX_VALUE
    }

    constructor(
        defaults: OnjObject,
        screen: OnjScreen,
        isDistanceFiled: Boolean
    ) : this(AdvancedText.defaultsFromOnj(defaults), screen, isDistanceFiled)

    open fun setRawText(text: String, effects: List<AdvancedTextParser.AdvancedTextEffect>?) {
        advancedText = AdvancedTextParser(text, screen, defaults, isDistanceField, effects ?: listOf()).parse()
    }

    override fun layout() {
        super.layout()

        children
            .filterIsInstance<Layout>()
            .onEach(Layout::validate)
            .forEach { child ->
                child as Actor
                child.width = child.prefWidth
                child.height = child.prefHeight
            }
        var curX = paddingLeft
        val lines = advancedText
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

        alignTextAfterLayout(lines, curY + paddingTop)


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
        if (width == 0F) width = lines.maxOf { it.last().x + it.last().width } + paddingRight
    }

    private fun alignTextAfterLayout(lines: List<List<Actor>>, totalHeight: Float) {
        if (fitContentHeight) return
        val diff = height - totalHeight
        when (verticalTextAlign) {
            CustomAlign.START -> lines.forEach { it2 -> it2.forEach { it.setPosition(it.x, it.y + diff) } }
            CustomAlign.CENTER -> lines.forEach { it2 -> it2.forEach { it.setPosition(it.x, it.y + diff/2) } }
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
    }

    private fun clearText() = advancedText.parts.forEach {
        removeActor(it.actor)
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
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
            isDistanceField: Boolean,
            effects: OnjArray?,
            screen: OnjScreen,
            defaults: OnjObject
        ): AdvancedText {
            return AdvancedTextParser(
                rawText,
                screen,
                defaultsFromOnj(defaults),
                isDistanceField,
                effects
                    ?.value
                    ?.map { AdvancedTextParser.AdvancedTextEffect.getFromOnj(it as OnjNamedObject) }
                    ?: listOf()
            ).parse()
        }

        fun defaultsFromOnj(onj: OnjObject): Triple<String, Color, Float> = Triple(
            onj.get<String>("font"),
            onj.get<Color>("color"),
            onj.get<Double>("fontScale").toFloat()
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
    rawText: String,
    font: String,
    fontColor: Color,
    fontScale: Float,
    screen: OnjScreen,
    isDistanceFiled: Boolean,
    override val breakLine: Boolean
) : TemplateStringLabel(
    screen,
    TemplateString(rawText),
    LabelStyle(
        ResourceManager.forceGet(object : ResourceBorrower {}, screen, font),
        fontColor
    ), // TODO: better way to do ResourceBorrowers
    isDistanceFiled
), AdvancedTextPart {

    override val actor: Actor = this

    var progress: Int = templateString.string.length
        private set

    private val actions: MutableList<AdvancedTextPart.() -> Unit> = mutableListOf()

    init {
        templateString = TemplateString(rawText)
        setFontScale(fontScale)
        skipTextCheck = true
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
        val text = templateString.string
        if (progress > text.length) return true
        setText(text.substring(0, progress))
        return progress >= text.length
    }

    override fun resetProgress() {
        progress = 0
        setText("")
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val newText = templateString.string
        if (progress >= newText.length) setText(newText)
        actions.forEach { it(this) }
        if (batch == null) {
            super.draw(null, parentAlpha)
            return
        }

        val shouldTransform = drawOffsetX != null || drawOffsetY != null

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
    private val font: String,
    screen: OnjScreen,
    private val dialogFontScale: Float,
    override val breakLine: Boolean
) : CustomImageActor(resourceHandle, screen, false), AdvancedTextPart, ResourceBorrower {


    override val actor: Actor = this

    private var isShown: Boolean = true

    private var iconHeight: Float = 0f
    private var iconWidth: Float = 0f

    private val actions: MutableList<AdvancedTextPart.() -> Unit> = mutableListOf()

    private var calculatedLayout = false

    private val fontPromise: Promise<BitmapFont> = ResourceManager.request(this, screen, font)

    init {
        reportDimensionsWithScaling = true
        ignoreScalingWhenDrawing = true
        fontPromise.then { calculatedLayout = false }
    }

    private fun recalcLayout() {
        val font = fontPromise.getOrNull() ?: return
        val layout = GlyphLayout(font, "qh")
        iconHeight = layout.height * dialogFontScale * 1.5f
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
