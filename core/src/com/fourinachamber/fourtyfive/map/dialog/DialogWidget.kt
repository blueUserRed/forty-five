package com.fourinachamber.fourtyfive.map.dialog

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.ResourceHandle
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.*
import com.fourinachamber.fourtyfive.utils.Timeline
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class DialogWidget(
    private val dialog: Dialog,
    private val progressTime: Int,
    private val advanceArrowDrawableHandle: ResourceHandle,
    private val advanceArrowOffset: Float,
    screen: OnjScreen,
) : AdvancedTextWidget(dialog.firstPart.text, screen) {

    private var isAnimFinished: Boolean = false

    private var lastProgressTime: Long = Long.MAX_VALUE

    private val timeline: Timeline = Timeline(mutableListOf())

    private var currentPart: DialogPart = dialog.firstPart
        set(value) {
            field = value
            advancedText = value.text
        }

    override var advancedText: AdvancedText
        get() = super.advancedText
        set(value) {
            super.advancedText = value
            value.resetProgress()
            isAnimFinished = false
        }

    private var readyToAdvance: Boolean = false

    private val advanceArrowDrawable: Drawable by lazy {
        ResourceManager.get(screen, advanceArrowDrawableHandle)
    }

    init {
        advancedText.resetProgress()
        onButtonClick {
            if (readyToAdvance) readyToAdvance = false
        }
        val line = Timeline.timeline {
            delayUntil { isAnimFinished }
            includeLater( { finished() }, { true } )
        }
        timeline.appendAction(line.asAction())
        timeline.start()
    }

    private fun finished(): Timeline = when (val part = currentPart.nextDialogPartSelector) {

        is NextDialogPartSelector.Fixed -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action { currentPart = part.next }
            delayUntil { isAnimFinished }
            includeLater( { finished() }, { true } )
        }

        is NextDialogPartSelector.End -> Timeline.timeline {
        }

        is NextDialogPartSelector.Choice -> Timeline.timeline {
        }

    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        timeline.update()
        currentPart.text.update()
        if (batch != null && readyToAdvance) {
            val aspect = advanceArrowDrawable.minHeight / advanceArrowDrawable.minWidth
            val arrowWidth = width * (1f / 18f)
            val arrowHeight = arrowWidth / aspect
//            val arrowHeight = height * (3f / 4f)
//            val arrowWidth = arrowHeight * aspect
            advanceArrowDrawable.draw(
                batch,
                x + width - advanceArrowOffset - arrowWidth,
                y + height / 2 - arrowHeight / 2,
                arrowWidth,
                arrowHeight
            )
        }
        if (isAnimFinished) return
        val curTime = TimeUtils.millis()
        if (curTime < lastProgressTime + progressTime) return
        isAnimFinished = advancedText.progress()
        lastProgressTime = curTime
    }

}

data class Dialog(
    val firstPart: DialogPart
) {

    companion object {

        fun readFromOnj(onj: OnjObject, screen: OnjScreen): Dialog {
            val defaults = onj.get<OnjObject>("defaults")
            val dialog = readDialogPart(onj.get<OnjObject>("parts"), defaults, screen)
            return Dialog(dialog)
        }

        private fun readDialogPart(onj: OnjObject, defaults: OnjObject, screen: OnjScreen): DialogPart {
            val text = AdvancedText.readFromOnj(onj.get<OnjArray>("text"), screen, defaults)
            val nextSelector = onj.get<OnjNamedObject>("next")
            val next = when (nextSelector.name) {
                "EndOfDialog" -> NextDialogPartSelector.End
                "FixedNextPart" -> NextDialogPartSelector.Fixed(
                    readDialogPart(nextSelector.get<OnjObject>("next"), defaults, screen)
                )
                "ChooseNextPart" -> NextDialogPartSelector.Choice(
                    nextSelector
                        .get<OnjArray>("choices")
                        .value
                        .map { it as OnjObject }
                        .associate {
                            it.get<String>("name") to readDialogPart(it.get<OnjObject>("next"), defaults, screen)
                        }
                )
                else -> throw RuntimeException()
            }
            return DialogPart(text, next)
        }

    }

}

data class DialogPart(
    val text: AdvancedText,
    val nextDialogPartSelector: NextDialogPartSelector
)

sealed class NextDialogPartSelector {

    class Fixed(val next: DialogPart) : NextDialogPartSelector()

    class Choice(
        val choices: Map<String, DialogPart>
    ) : NextDialogPartSelector()

    object End : NextDialogPartSelector()

}
