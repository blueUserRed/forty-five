package com.fourinachamber.fortyfive.map.events.dialog

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
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
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.Timeline

class DialogWidget(
    screen: OnjScreen,
    private val optionsBoxName: String,
    private val getOption: (String) -> Actor
) : AdvancedTextWidget(Triple("red_wing", Color.FortyWhite, 50f), screen, true), ResourceBorrower {

    private val progressTimeMs: Int = 100
    private val advanceArrowDrawableHandle: ResourceHandle = "common_symbol_arrow_right"

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

//    private var initialisedOptionsBox: Boolean = false
//    private var initialisedNameSize: Boolean = false
//    private lateinit var optionsBox: CustomFlexBox

//    private val optionBoxNodes: MutableList<YogaNode> = mutableListOf()

    private var chosenOption: String? = null
    private var currentOptions: Map<String, Int>? = null

    private val onFinishedCallbacks: MutableList<() -> Unit> = mutableListOf()

    private lateinit var dialog: Dialog

    fun start(dialog: Dialog) {
        this.dialog = dialog
        currentPart = dialog.parts.getOrNull(0) ?: return
        advancedText.resetProgress()
        //TODO set people talking correctly
        parent.onSelect {
            screen.deselectAllExcept()
            if (readyToAdvance) readyToAdvance = false
        }
        val line = Timeline.timeline {
            delayUntil { isAnimFinished }
            includeLater({ finishedPart() }, { true })
        }
        timeline.appendAction(line.asAction())
        timeline.startTimeline()
    }

    private fun finishedPart(): Timeline = when (val part = currentPart!!.nextDialogPartSelector) {

        is NextDialogPartSelector.Continue -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action { currentPart = getPart(dialog.parts.indexOf(currentPart!!) + 1) }
            delayUntil { isAnimFinished }
            includeLater({ finishedPart() }, { true })
        }

        is NextDialogPartSelector.Fixed -> Timeline.timeline {
            action { readyToAdvance = true }
            delayUntil { !readyToAdvance }
            action { currentPart = getPart(part.next) }
            delayUntil { isAnimFinished }
            includeLater({ finishedPart() }, { true })
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
//                setupOptionsBox()
            }
            delayUntil { chosenOption != null }
            action {
                val next = currentOptions!![chosenOption!!]!!
                currentPart = getPart(next)
//                clearOptionsBox()
            }
            delayUntil { isAnimFinished }
            includeLater({ finishedPart() }, { true })
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

//    private fun setupOptionsBox() {
//        optionBoxNodes.clear()
//        screen.afterMs(20){
//            screen.enterState(showOptionsBoxScreenState)
//        }
//        currentOptions!!.forEach { (option, _) ->
//            val actor = screen.screenBuilder.generateFromTemplate(
//                "optionsItem",
//                mutableMapOf("text" to option),
//                optionsBox,
//                screen
//            ) as CustomLabel
//            actor.onButtonClick { chosenOption = option }
//            actor.styleManager?.let { optionBoxNodes.add(it.node) }
//        }
//    }
//
//    private fun clearOptionsBox() {
//        screen.leaveState(showOptionsBoxScreenState)
//        optionBoxNodes.forEach { optionsBox.remove(it) }
//        chosenOption = null
//        currentOptions = null
//    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
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
                x + width - 60f - arrowWidth,
                y + height / 2 - arrowHeight / 2,
                arrowWidth,
                arrowHeight
            )
        }
        if (isAnimFinished) return
        val curTime = TimeUtils.millis()
        if (curTime < lastProgressTime + progressTimeMs) return
        isAnimFinished = advancedText.progress()
        lastProgressTime = curTime
    }

    companion object {
        const val showOptionsBoxScreenState: String = "displayOptionsBox"
        const val logTag: String = "Dialog"
    }

}
