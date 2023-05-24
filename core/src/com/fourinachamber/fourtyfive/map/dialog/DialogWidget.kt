package com.fourinachamber.fourtyfive.map.dialog

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.screen.ResourceHandle
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.*
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.Timeline
import ktx.actors.onClick
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class DialogWidget(
    private val progressTime: Int,
    private val advanceArrowDrawableHandle: ResourceHandle,
    private val advanceArrowOffset: Float,
    private val optionsBoxName: String,
    private val optionsFont: BitmapFont,
    private val optionsFontColor: Color,
    private val optionsFontScale: Float,
    private val screen: OnjScreen
) : AdvancedTextWidget(AdvancedText.EMPTY, screen) {

    private var isAnimFinished: Boolean = false

    private var lastProgressTime: Long = Long.MAX_VALUE

    private val timeline: Timeline = Timeline(mutableListOf())

    private var currentPart: DialogPart? = null
        set(value) {
            field = value
            advancedText = value?.text ?: AdvancedText.EMPTY
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

    private var initialisedOptionsBox: Boolean = false
    private lateinit var optionsBox: CustomFlexBox

    private var chosenOption: String? = null
    private var currentOptions: Map<String, DialogPart>? = null

    fun start(dialog: Dialog) {
        currentPart = dialog.firstPart
        advancedText.resetProgress()
        onButtonClick {
            if (readyToAdvance) readyToAdvance = false
        }
        val line = Timeline.timeline {
            delayUntil { isAnimFinished }
            includeLater({ finished() }, { true })
        }
        timeline.appendAction(line.asAction())
        timeline.start()
    }

    private fun finished(): Timeline = when (val part = currentPart!!.nextDialogPartSelector) {

        is NextDialogPartSelector.Fixed -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action { currentPart = part.next }
            delayUntil { isAnimFinished }
            includeLater( { finished() }, { true } )
        }

        is NextDialogPartSelector.End -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action { FourtyFive.changeToScreen(part.nextScreen) }
        }

        is NextDialogPartSelector.Choice -> Timeline.timeline {
            action {
                currentOptions = part.choices
                setupOptionsBox()
            }
            delayUntil { chosenOption != null }
            action {
                val next = currentOptions!![chosenOption!!]!!
                currentPart = next
                clearOptionsBox()
            }
            delayUntil { isAnimFinished }
            includeLater( { finished() }, { true } )
        }

    }

    private fun setupOptionsBox() {
        screen.enterState(showOptionsBoxScreenState)
        currentOptions!!.forEach { (option, _) ->
            val actor = CustomLabel(screen, option, Label.LabelStyle(optionsFont, optionsFontColor))
            actor.setFontScale(optionsFontScale)
            actor.onClick { chosenOption = option } // TODO: find some way to use onButtonClick there
            optionsBox.add(actor)
        }
    }

    private fun clearOptionsBox() {
        screen.leaveState(showOptionsBoxScreenState)
        optionsBox.clear()
        chosenOption = null
        currentOptions = null
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (!initialisedOptionsBox) {
            val optionsBox = screen.namedActorOrError(optionsBoxName)
            if (optionsBox !is CustomFlexBox) {
                throw RuntimeException("actor with name $optionsBoxName must be of type CustomFlexBox")
            }
            this.optionsBox = optionsBox
            initialisedOptionsBox = true
        }
        super.draw(batch, parentAlpha)
        timeline.update()
        currentPart?.text?.update()
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

    companion object {
        const val showOptionsBoxScreenState: String = "displayOptionsBox"
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
                "EndOfDialog" -> NextDialogPartSelector.End(nextSelector.get<String>("changeToScreen"))
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

    class End(val nextScreen: String) : NextDialogPartSelector()

}
