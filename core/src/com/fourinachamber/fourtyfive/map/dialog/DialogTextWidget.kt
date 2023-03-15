package com.fourinachamber.fourtyfive.map.dialog

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Affine2
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.general.CustomFlexBox
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.styleTest.StyledActor
import com.fourinachamber.fourtyfive.utils.TemplateString
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import onj.value.OnjValue
import kotlin.math.sin

class DialogTextWidget(
    private val dialog: Dialog,
    private val fontScale: Float,
    private val progressTime: Int,
) : CustomFlexBox(), StyledActor {

    private var isDialogFinished: Boolean = false

    private var lastProgressTime: Long = Long.MAX_VALUE

    init {
        dialog.parts.forEach {
            it.setFontScale(fontScale)
            add(it)
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
                DialogPart.readFromOnj(it, font, screen)
            }
            return Dialog(parts)
        }
    }

}

class DialogPart(
    rawText: String,
    font: BitmapFont,
    fontColor: Color,
    private val actions: List<DialogPart.() -> Unit>,
    screen: OnjScreen,
) : CustomLabel(screen, "", LabelStyle(font, fontColor)) {

    var progress: Int = 0
        private set

    private val templateString: TemplateString = TemplateString(rawText)

    private var xOffset: Float? = null
    private var yOffset: Float? = null

    fun progress(): Boolean {
        progress++
        val text = templateString.string
        if (progress > text.length) return true
        setText(text.substring(0, progress))
        if (progress >= text.length) return true
        return false
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        actions.forEach { it(this) }

        if (batch == null) {
            super.draw(null, parentAlpha)
            return
        }

        val shouldTransform = xOffset != null || yOffset != null

        val oldTransform = batch.transformMatrix.cpy()
        if (shouldTransform) {
            val worldTransform = Affine2()
            worldTransform.set(oldTransform)
            worldTransform.translate(xOffset ?: 0f, yOffset ?: 0f)
            val computed = Matrix4()
            computed.set(worldTransform)
            batch.transformMatrix = computed
        }

        super.draw(batch, parentAlpha)

        if (shouldTransform) {
            batch.transformMatrix = oldTransform
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

        fun getAction(onj: OnjNamedObject): DialogPart.() -> Unit = actions[onj.name]?.invoke(onj)
            ?: throw RuntimeException("unknown dialog action: ${onj.name}")

    }

    companion object {

        fun readFromOnj(onj: OnjObject, font: BitmapFont, screen: OnjScreen): DialogPart {
            return DialogPart(
                onj.get<String>("text"),
                font,
                onj.get<Color>("color"),
                onj.getOr<List<OnjValue>>("actions", listOf()).map {
                    it as OnjNamedObject
                    DialogPartActionFactory.getAction(it)
                },
                screen
            )
        }

    }
}
