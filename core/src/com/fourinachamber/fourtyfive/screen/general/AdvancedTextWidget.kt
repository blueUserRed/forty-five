package com.fourinachamber.fourtyfive.screen.general

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Affine2
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.general.styles.StyledActor
import com.fourinachamber.fourtyfive.utils.TemplateString
import io.github.orioncraftmc.meditate.YogaNode
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.math.sin

open class AdvancedTextWidget(
    advancedText: AdvancedText,
    private val fontScale: Float,
    screen: OnjScreen
) : CustomFlexBox(screen) {

    private var nodesOfCurrentText: List<YogaNode> = listOf()

    open var advancedText: AdvancedText = advancedText
        set(value) {
            field = value
            clearText()
            nodesOfCurrentText = value.parts.map {
                it.dialogFontScale = fontScale
                add(it.actor)
            }
        }

    init {
        nodesOfCurrentText = advancedText.parts.map {
            it.dialogFontScale = fontScale
            add(it.actor)
        }
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

    fun resetProgress(): Unit = parts.forEach { it.resetProgress() }

    companion object {

        fun readFromOnj(arr: OnjArray, font: BitmapFont, screen: OnjScreen): AdvancedText {
            val parts = arr.value.map {
                it as OnjObject
                TextAdvancedTextPart.readFromOnj(it, font, screen)
            }
            return AdvancedText(parts)
        }
    }

}

interface AdvancedTextPart {

    var dialogFontScale: Float
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

}

class TextAdvancedTextPart(
    rawText: String,
    font: BitmapFont,
    fontColor: Color,
    screen: OnjScreen
) : CustomLabel(screen, rawText, LabelStyle(font, fontColor)), AdvancedTextPart {

    override var dialogFontScale: Float
        get() = this.fontScaleX
        set(value) {
            setFontScale(value)
        }

    override val actor: Actor = this

    private val templateString: TemplateString = TemplateString(rawText)

    var progress: Int = templateString.string.length
        private set

    override var xOffset: Float? = null
    override var yOffset: Float? = null

    private val actions: MutableList<AdvancedTextPart.() -> Unit> = mutableListOf()

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

    companion object {

        fun readFromOnj(onj: OnjObject, font: BitmapFont, screen: OnjScreen): TextAdvancedTextPart {
            return TextAdvancedTextPart(
                onj.get<String>("text"),
                font,
                onj.get<Color>("color"),
                screen
            )
        }

    }
}

object AdvancedTextPartActionFactory {

    private val actions: Map<String, (onj: OnjObject) -> AdvancedTextPart.() -> Unit> = mapOf(
        "ShakeDialogAction" to { onj ->
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

    fun getAction(onj: OnjNamedObject): TextAdvancedTextPart.() -> Unit = actions[onj.name]?.invoke(onj)
        ?: throw RuntimeException("unknown dialog action: ${onj.name}")

}
