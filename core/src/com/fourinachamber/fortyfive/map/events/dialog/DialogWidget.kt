package com.fourinachamber.fortyfive.map.events.dialog

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.events.chooseCard.ChooseCardScreenContext
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import io.github.orioncraftmc.meditate.YogaNode
import ktx.actors.onClick
import onj.value.OnjObject
import onj.value.OnjString

class DialogWidget(
    private val progressTime: Int,
    private val advanceArrowDrawableHandle: ResourceHandle,
    private val advanceArrowOffset: Float,
    private val optionsBoxName: String,
    private val speakingPersonLabel: String,
    defaults: OnjObject,
    screen: OnjScreen
) : AdvancedTextWidget(defaults, screen, true), ResourceBorrower {

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

    private val advanceArrowDrawable: Promise<Drawable> = ResourceManager.request(this, screen, advanceArrowDrawableHandle)

    private var initialisedOptionsBox: Boolean = false
    private var initialisedNameSize: Boolean = false
    private lateinit var optionsBox: CustomFlexBox

    private val optionBoxNodes: MutableList<YogaNode> = mutableListOf()

    private var chosenOption: String? = null
    private var currentOptions: Map<String, Int>? = null

    private val onFinishedCallbacks: MutableList<() -> Unit> = mutableListOf()

    private lateinit var dialog: Dialog

    fun start(dialog: Dialog) {
        this.dialog = dialog
        currentPart = dialog.parts.getOrNull(0) ?: return
        advancedText.resetProgress()
        parent.onButtonClick {
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
            includeLater({ finished() }, { true })
        }

        is NextDialogPartSelector.Fixed -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action { currentPart = getPart(part.next) }
            delayUntil { isAnimFinished }
            includeLater({ finished() }, { true })
        }

        is NextDialogPartSelector.End -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action {
                end()
                FortyFive.changeToScreen(ConfigFileManager.screenBuilderFor(part.nextScreen))
            }
        }

        is NextDialogPartSelector.GiftCardEnd -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action {
                val context = object : ChooseCardScreenContext {
                    override val forwardToScreen: String = part.nextScreen
                    override var seed: Long = -1
                    override val nbrOfCards: Int = 1
                    override val types: List<String> = listOf()
                    override val enableRerolls: Boolean = false
                    override var amountOfRerolls: Int = 0
                    override val rerollPriceIncrease: Int = 0
                    override val rerollBasePrice: Int = 0
                    override val forceCards: List<String> = listOf(part.card)
                    override fun completed() {}
                }
                end()
                MapManager.changeToChooseCardScreen(context)
            }
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
            includeLater({ finished() }, { true })
        }

        is NextDialogPartSelector.ToCreditScreenEnd -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action {
                end()
                MapManager.changeToCreditsScreen()
            }
        }

    }

    private fun end() {
        onFinishedCallbacks.forEach { it() }
    }

    fun onFinish(callback: () -> Unit) {
        onFinishedCallbacks.add(callback)
    }

    private fun getPart(next: Int): DialogPart? = dialog.parts.getOrNull(next) ?: run {
        FortyFiveLogger.warn(logTag, "dialog links to part $next which doesn't exist")
        null
    }

    private fun setupOptionsBox() {
        optionBoxNodes.clear()
        screen.afterMs(20){
            screen.enterState(showOptionsBoxScreenState)
        }
        currentOptions!!.forEach { (option, _) ->
            val actor = screen.screenBuilder.generateFromTemplate(
                "optionsItem",
                mutableMapOf("text" to option),
                optionsBox,
                screen
            ) as CustomLabel
            actor.onButtonClick { chosenOption = option }
            actor.styleManager?.let { optionBoxNodes.add(it.node) }
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
        if (!initialisedNameSize) {
            val tempParent = screen.namedActorOrError(speakingPersonLabel).parent as CustomFlexBox
            if (tempParent.width < 30) { //TODO ugly with fixed size parameter
                tempParent.invalidate()
            }else{ //Needs to be done more than once till the text is correctly drawn
                initialisedNameSize = true
            }
        }
        super.draw(batch, parentAlpha)
        timeline.updateTimeline()
        currentPart?.text?.update()
        val advanceArrowDrawable = advanceArrowDrawable.getOrNull()
        if (batch != null && advanceArrowDrawable != null && readyToAdvance) {
            val aspect = advanceArrowDrawable.minHeight / advanceArrowDrawable.minWidth
            val arrowWidth = width * (1f / 18f)
            val arrowHeight = arrowWidth / aspect
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
