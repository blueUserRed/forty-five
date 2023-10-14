package com.fourinachamber.fortyfive.map.events.dialog

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.Timeline
import io.github.orioncraftmc.meditate.YogaNode
import ktx.actors.onClick
import onj.value.OnjObject

class DialogWidget(
    private val progressTime: Int,
    private val advanceArrowDrawableHandle: ResourceHandle,
    private val advanceArrowOffset: Float,
    private val optionsBoxName: String,
    private val optionsFont: BitmapFont,
    private val optionsFontColor: Color,
    private val optionsFontScale: Float,
    defaults: OnjObject,
    screen: OnjScreen
) : AdvancedTextWidget(defaults, screen) {

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

    private val optionBoxNodes: MutableList<YogaNode> = mutableListOf()

    private var chosenOption: String? = null
    private var currentOptions: Map<String, Int>? = null

    private lateinit var dialog: Dialog

    fun start(dialog: Dialog) {
        this.dialog = dialog
        currentPart = dialog.parts.getOrNull(0) ?: return
        advancedText.resetProgress()
        onButtonClick {
            if (readyToAdvance) readyToAdvance = false
        }
        val line = Timeline.timeline {
            delayUntil { isAnimFinished }
            includeLater({ finished() }, { true })
        }
        timeline.appendAction(line.asAction())
        timeline.startTimeline()
    }

    private fun finished(): Timeline = when (val part = currentPart!!.nextDialogPartSelector) {

        is NextDialogPartSelector.Continue -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action { currentPart = getPart(dialog.parts.indexOf(currentPart!!) + 1) }
            delayUntil { isAnimFinished }
            includeLater( { finished() }, { true } )
        }

        is NextDialogPartSelector.Fixed -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action { currentPart = getPart(part.next) }
            delayUntil { isAnimFinished }
            includeLater( { finished() }, { true } )
        }

        is NextDialogPartSelector.End -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action { FortyFive.changeToScreen(part.nextScreen) }
        }

        is NextDialogPartSelector.Choice -> Timeline.timeline {
            action {
                currentOptions = part.choices
                setupOptionsBox()
            }
            delayUntil { chosenOption != null }
            action {
                val next = currentOptions!![chosenOption!!]!!
                currentPart = getPart(next)
                clearOptionsBox()
            }
            delayUntil { isAnimFinished }
            includeLater( { finished() }, { true } )
        }

    }

    private fun getPart(next: Int): DialogPart? = dialog.parts.getOrNull(next) ?: run {
        FortyFiveLogger.warn(logTag, "dialog links to part $next which doesn't exist")
        null
    }

    private fun setupOptionsBox() {
        optionBoxNodes.clear()
        screen.enterState(showOptionsBoxScreenState)
        currentOptions!!.forEach { (option, _) ->
            val actor = CustomLabel(screen, option, Label.LabelStyle(optionsFont, optionsFontColor))
            actor.setFontScale(optionsFontScale)
            actor.onClick { chosenOption = option } // TODO: find some way to use onButtonClick there
            val node = optionsBox.add(actor)
            optionBoxNodes.add(node)
        }
    }

    private fun clearOptionsBox() {
        screen.leaveState(showOptionsBoxScreenState)
        optionBoxNodes.forEach { optionsBox.remove(it) }
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
        timeline.updateTimeline()
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
        const val logTag: String = "Dialog"
    }

}
