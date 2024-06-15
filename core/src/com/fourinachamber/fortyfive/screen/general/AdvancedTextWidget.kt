package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.Affine2
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.customActor.HoverStateActor
import com.fourinachamber.fortyfive.screen.general.customActor.OffSettable
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.styles.*
import com.fourinachamber.fortyfive.utils.*
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.math.sin

open class AdvancedTextWidget(
    private val defaults: Triple<BitmapFont, Color, Float>,
    val screen: OnjScreen,
    private val isDistanceField: Boolean,
) : WidgetGroup(), ZIndexActor, HoverStateActor, StyledActor {

    override var fixedZIndex: Int = 0

    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false
    override var styleManager: StyleManager? = null

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
    }

    constructor(
        defaults: OnjObject,
        screen: OnjScreen,
        isDistanceFiled: Boolean
    ) : this(AdvancedText.defaultsFromOnj(defaults, screen), screen, isDistanceFiled)

    fun setRawText(text: String, effects: List<AdvancedTextParser.AdvancedTextEffect>?) {
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

        var curX = 0f
        val lines = advancedText
            .parts
            .splitAt { part ->
                val child = part.actor
                val shouldSplit = curX + child.width > width
                if (shouldSplit) {
                    curX = 0f
                } else if (part.breakLine) {
                    curX = width + 1f
                }
                curX += child.width
                shouldSplit
            }
            .map { line -> line.map { it.actor } }

        var curY = 0f
        curX = 0f
        lines
            .reversed()
            .zip { line -> line.maxOf { it.height } }
            .forEach { (line, height) ->
                line.forEach { actor ->
                    actor.setPosition(curX, curY)
                    curX += actor.width
                }
                curX = 0f
                curY += height
            }
        height = curY
    }

    private fun initText(value: AdvancedText) {
        value
            .parts
            .forEach { part -> addActor(part.actor) }
        invalidateHierarchy()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (!initialisedStyleInstruction) run initStyleInstruction@{
            val styleManager = styleManager ?: run {
                initialisedStyleInstruction = true
                return@initStyleInstruction
            }
            styleManager.addInstruction(
                "height",
                ObservingStyleInstruction(
                    Integer.MAX_VALUE,
                    StyleCondition.Always,
                    YogaValue::class,
                    observer = { YogaValue(height, YogaUnit.POINT) }
                ),
                YogaValue::class
            )
            initialisedStyleInstruction = true
        }
        advancedText.update()
        super.draw(batch, parentAlpha)
    }

    private fun clearText() = advancedText.parts.forEach { removeActor(it.actor) }

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

        fun readFromOnj(rawText: String, isDistanceField: Boolean, effects: OnjArray?, screen: OnjScreen, defaults: OnjObject): AdvancedText {
            return AdvancedTextParser(
                rawText,
                screen,
                defaultsFromOnj(defaults, screen),
                isDistanceField,
                effects
                ?.value
                ?.map { AdvancedTextParser.AdvancedTextEffect.getFromOnj(screen, it as OnjNamedObject) }
                    ?: listOf()).parse()
        }

        fun defaultsFromOnj(onj: OnjObject, screen: OnjScreen): Triple<BitmapFont, Color, Float> = Triple(
            ResourceManager.get(screen, onj.get<String>("font")) as BitmapFont,
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
        worldTransform.translate(offsetX ?: 0f, offsetY ?: 0f)
        val computed = Matrix4()
        computed.set(worldTransform)
        return computed
    }

    fun update() {}

}

class TextAdvancedTextPart(
    rawText: String,
    font: BitmapFont,
    fontColor: Color,
    fontScale: Float,
    screen: OnjScreen,
    isDistanceFiled: Boolean,
    override val breakLine: Boolean
) : TemplateStringLabel(screen, TemplateString(rawText), LabelStyle(font, fontColor), isDistanceFiled), AdvancedTextPart {

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

    override var offsetX: Float = 0F
    override var offsetY: Float = 0F

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

        val shouldTransform = offsetX != null || offsetY != null

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
    private val font: BitmapFont,
    override val screen: OnjScreen,
    private val dialogFontScale: Float,
    override val breakLine: Boolean
) : CustomImageActor(resourceHandle, screen, false), AdvancedTextPart {


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
        if (loadedDrawable == null) forceLoadDrawable()
        if (drawable == null) forceLoadDrawable()
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
                offsetX = sin(TimeUtils.millis().toDouble() * xSpeed).toFloat() * xMagnitude
                offsetY = sin(TimeUtils.millis().toDouble() * ySpeed + Math.PI.toFloat()).toFloat() * yMagnitude
            }
        }
    )

    fun getAction(onj: OnjNamedObject): AdvancedTextPart.() -> Unit = actions[onj.name]?.invoke(onj)
        ?: throw RuntimeException("unknown dialog action: ${onj.name}")

}
