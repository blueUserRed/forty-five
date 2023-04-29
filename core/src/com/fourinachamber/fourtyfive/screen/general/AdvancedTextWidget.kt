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
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import onj.value.OnjValue
import kotlin.math.sin

class AdvancedTextWidget(
    private val dialog: Dialog,
    private val fontScale: Float,
    private val progressTime: Int,
    screen: OnjScreen
) : CustomFlexBox(screen), StyledActor {

    private var isDialogFinished: Boolean = false

    private var lastProgressTime: Long = Long.MAX_VALUE

    init {
        dialog.parts.forEach {
            it.dialogFontScale = fontScale
            add(it.actor)
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (isDialogFinished) return
        val curTime = TimeUtils.millis()
        if (curTime < lastProgressTime + progressTime) return
        isDialogFinished = dialog.progress()
        lastProgressTime = curTime
    }

}

data class Dialog(
    val parts: List<DialogPart>
) {

    private var currentPartIndex: Int = 0

    fun progress(): Boolean {
        if (currentPartIndex >= parts.size) return true
        val finished = parts[currentPartIndex].progress()
        if (!finished) return false
        currentPartIndex++
        return currentPartIndex >= parts.size
    }

    companion object {
        fun readFromOnj(arr: OnjArray, font: BitmapFont, screen: OnjScreen): Dialog {
            val parts = arr.value.map {
                it as OnjObject
                TextDialogPart.readFromOnj(it, font, screen)
            }
            return Dialog(parts)
        }
    }

}

interface DialogPart {

    var dialogFontScale: Float
    val actor: Actor

    var xOffset: Float?
    var yOffset: Float?

    fun progress(): Boolean
    fun prepareForAnimation()
    fun addDialogAction(action: DialogPart.() -> Unit)

    fun calcTransformationMatrixForOffsets(oldTransform: Matrix4): Matrix4 {
        val worldTransform = Affine2()
        worldTransform.set(oldTransform)
        worldTransform.translate(xOffset ?: 0f, yOffset ?: 0f)
        val computed = Matrix4()
        computed.set(worldTransform)
       return computed
    }

}

class TextDialogPart(
    rawText: String,
    font: BitmapFont,
    fontColor: Color,
    screen: OnjScreen
) : CustomLabel(screen, rawText, LabelStyle(font, fontColor)), DialogPart {

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

    private val actions: MutableList<DialogPart.() -> Unit> = mutableListOf()

    override fun addDialogAction(action: DialogPart.() -> Unit) {
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

    override fun prepareForAnimation() {
        progress = 0
        setText("")
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        setText(templateString.string)
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

        fun readFromOnj(onj: OnjObject, font: BitmapFont, screen: OnjScreen): TextDialogPart {
            return TextDialogPart(
                onj.get<String>("text"),
                font,
                onj.get<Color>("color"),
                screen
            )
        }

    }
}

object DialogPartActionFactory {

    private val actions: Map<String, (onj: OnjObject) -> DialogPart.() -> Unit> = mapOf(
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

    fun getAction(onj: OnjNamedObject): TextDialogPart.() -> Unit = actions[onj.name]?.invoke(onj)
        ?: throw RuntimeException("unknown dialog action: ${onj.name}")

}
