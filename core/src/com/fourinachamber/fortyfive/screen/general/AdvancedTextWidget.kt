package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.Affine2
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.styles.StyleCondition
import com.fourinachamber.fortyfive.screen.general.styles.StyleInstruction
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.utils.TemplateString
import io.github.orioncraftmc.meditate.YogaNode
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.math.sin

open class AdvancedTextWidget(
    advancedText: AdvancedText,
    private val screen: OnjScreen
) : CustomFlexBox(screen) {

    private var nodesOfCurrentText: List<YogaNode> = listOf()

    open var advancedText: AdvancedText = advancedText
        set(value) {
            field = value
            clearText()
            initNodes(value)
        }

    init {
        initNodes(advancedText)
    }

    private fun initNodes(value: AdvancedText) {
        nodesOfCurrentText = value.parts.map { part ->
            val node = add(part.actor)
            if (part is StyledActor && part is Actor) { // TODO: .actor is stupid now
                val oldManager = part.styleManager
                val newManager = StyleManager(part, node)
                part.styleManager = newManager
                part.initStyles(screen)
                newManager.addInstruction( // TODO: this is ugly
                    "marginLeft",
                    StyleInstruction(
                        YogaValue(0.1f, YogaUnit.POINT),
                        1,
                        StyleCondition.Always,
                        YogaValue::class
                    ),
                    YogaValue::class
                )
                if (oldManager == null) {
                    screen.addStyleManager(newManager)
                } else {
                    screen.swapStyleManager(oldManager, newManager)
                }
            }
            node
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        advancedText.update()
        super.draw(batch, parentAlpha)
    }

    fun clearText() {
        nodesOfCurrentText.forEach { remove(it) }
        nodesOfCurrentText = listOf()
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

    fun resetProgress(): Unit = parts.forEach { it.resetProgress() }

    companion object {

        val EMPTY = AdvancedText(listOf())

        fun readFromOnj(partsOnj: OnjArray, screen: OnjScreen, defaults: OnjObject): AdvancedText {
            val defaultFontName = defaults.get<String>("font")
            val defaultFont = ResourceManager.get<BitmapFont>(screen, defaultFontName)
            val defaultColor = defaults.get<Color>("color")
            val defaultFontScale = defaults.get<Double>("fontScale").toFloat()
            val parts = partsOnj.value.map { obj ->
                obj as OnjNamedObject
                val part = when (obj.name) {
                    "Text" -> TextAdvancedTextPart(
                        obj.get<String>("text"),
                        obj.getOr<String?>("font", null)?.let { ResourceManager.get(screen, it) }
                            ?: defaultFont,
                        obj.getOr<Color?>("color", null) ?: defaultColor,
                        obj.getOr<Double?>("fontScale", null)?.toFloat() ?: defaultFontScale,
                        screen
                    )
                    "Icon" -> IconAdvancedTextPart(
                        obj.get<String>("icon"),
                        obj.getOr<String?>("font", null)?.let { ResourceManager.get(screen, it) }
                            ?: defaultFont,
                        screen,
                        obj.getOr<Double?>("fontScale", null)?.toFloat() ?: defaultFontScale
                    )
                    else -> throw RuntimeException("unknown text part ${obj.name}")
                }
                obj.ifHas<OnjArray>("actions") { arr ->
                    arr
                        .value
                        .forEach {
                            it as OnjNamedObject
                            val action = AdvancedTextPartActionFactory.getAction(it)
                            part.addDialogAction(action)
                        }
                }
                part
            }
            return AdvancedText(parts)
        }
    }

}

interface AdvancedTextPart {

    val actor: Actor

    var xOffset: Float?
    var yOffset: Float?

    fun progress(): Boolean
    fun resetProgress()
    fun addDialogAction(action: AdvancedTextPart.() -> Unit)

    fun calcTransformationMatrixForOffsets(oldTransform: Matrix4): Matrix4 {
        val worldTransform = Affine2()
        worldTransform.set(oldTransform)
        worldTransform.translate(xOffset ?: 0f, yOffset ?: 0f)
        val computed = Matrix4()
        computed.set(worldTransform)
        return computed
    }

    fun update() { }

}

class TextAdvancedTextPart(
    rawText: String,
    font: BitmapFont,
    fontColor: Color,
    fontScale: Float,
    screen: OnjScreen
) : CustomLabel(screen, rawText, LabelStyle(font, fontColor)), AdvancedTextPart {

    override val actor: Actor = this

    private val templateString: TemplateString = TemplateString(rawText)

    var progress: Int = templateString.string.length
        private set

    override var xOffset: Float? = null
    override var yOffset: Float? = null

    private val actions: MutableList<AdvancedTextPart.() -> Unit> = mutableListOf()

    init {
        setFontScale(fontScale)
    }

    override fun addDialogAction(action: AdvancedTextPart.() -> Unit) {
        actions.add(action)
    }

    override fun progress(): Boolean {
        progress++
        val text = templateString.string
        if (progress > text.length) return true
        setText(text.substring(0, progress))
        if (progress >= text.length) return true
        return false
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

        val shouldTransform = xOffset != null || yOffset != null

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
    private val screen: OnjScreen,
    private val dialogFontScale: Float,
) : CustomImageActor(resourceHandle, screen, false), AdvancedTextPart {


    override val actor: Actor = this

    override var xOffset: Float? = null

    override var yOffset: Float? = null
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
        isVisible = false
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
                xOffset = sin(TimeUtils.millis().toDouble() * xSpeed).toFloat() * xMagnitude
                yOffset = sin(TimeUtils.millis().toDouble() * ySpeed + Math.PI.toFloat()).toFloat() * yMagnitude
            }
        }
    )

    fun getAction(onj: OnjNamedObject): AdvancedTextPart.() -> Unit = actions[onj.name]?.invoke(onj)
        ?: throw RuntimeException("unknown dialog action: ${onj.name}")

}
