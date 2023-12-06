package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.EnterMapMapEvent
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.CustomMoveByAction
import com.fourinachamber.fortyfive.screen.general.customActor.InOutAnimationActor
import com.fourinachamber.fortyfive.screen.general.onButtonClick
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.toOnjYoga
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.value.OnjObject
import onj.value.OnjString

class StatusbarWidget(
    private val mapIndicatorWidgetName: String?,
    private val optionsWidgetName: String,
    private val options: List<OnjObject>,
    screen: OnjScreen
) : CustomFlexBox(screen) {

    private val timeline: Timeline = Timeline(mutableListOf())

    /**
     * which option from [optionWidgets] is being "displayed" at the moment (the index of it)
     */
    private var displayedOptionIndex: Int = -1

    /**
     * all possible options to open from the statusbar
     */
    private val optionWidgets: MutableList<Pair<CustomFlexBox, String>> = mutableListOf()
    override fun draw(batch: Batch?, parentAlpha: Float) {
        timeline.updateTimeline()
        super.draw(batch, parentAlpha)
    }

    private var isInitialised = false

    override fun layout() {
        if (!isInitialised) {
            initSpecialChildren()
            isInitialised = true
        }
        super.layout()
    }

    private fun initSpecialChildren() {
        initMapIndicator()
        initOptions()
        resortZIndices()
        timeline.startTimeline()
    }

    private fun initOptions() {
        val optionParent = screen.namedActorOrError(optionsWidgetName) as CustomFlexBox
        for (i in options) {
            val curBox = screen.screenBuilder.generateFromTemplate(
                "statusbar_option",
                mapOf(
                    "text" to i.get<OnjString>("displayName"),
                    "width" to ((100F - (3 * 2) - ((options.size - 1) * 2)) / options.size).toOnjYoga(YogaUnit.PERCENT)
                ),
                optionParent,
                screen
            ) as CustomFlexBox
            val actorName = i.get<String>("actorName")
            optionWidgets.add(curBox to actorName)
            curBox.onButtonClick { buttonClicked(curBox) }
        }
    }

    private fun buttonClicked(clickedBox: CustomFlexBox) {
        if (timeline.isFinished) {
            val option = optionWidgets.find { it.first == clickedBox }!!
            when (displayedOptionIndex) {
                -1 -> {
                    screen.enterState(StatusbarWidget.OVERLAY_NAME)
                    display(option)
                }
                optionWidgets.indexOf(option) -> {
                    hide(option)
                    screen.leaveState(StatusbarWidget.OVERLAY_NAME)
                }
                else -> {
                    hide(optionWidgets[displayedOptionIndex])
                    display(option)
                }
            }
        }
    }

    private fun initMapIndicator() {
        if (mapIndicatorWidgetName == null) return
        val mapIndicator = screen.namedActorOrError(mapIndicatorWidgetName) as CustomFlexBox
        val curImage = getImageData(MapManager.currentDetailMap.name)
        if (curImage == null || !MapManager.currentDetailMap.isArea) {
            screen.screenBuilder.generateFromTemplate(
                "statusbar_text",
                mapOf("text" to OnjString("Road between ")),
                mapIndicator,
                screen
            )
            screen.screenBuilder.generateFromTemplate(
                "statusbar_sign",
                mapOf(
                    "textureName" to
                            OnjString(
                                getImageData(
                                    (MapManager.currentDetailMap.startNode.event as EnterMapMapEvent).targetMap
                                )!!.resourceHandle
                            )
                ),
                mapIndicator,
                screen
            )
            screen.screenBuilder.generateFromTemplate(
                "statusbar_text",
                mapOf("text" to OnjString(" and ")),
                mapIndicator,
                screen
            )
            screen.screenBuilder.generateFromTemplate(
                "statusbar_sign",
                mapOf(
                    "textureName" to
                            OnjString(
                                getImageData(
                                    (MapManager.currentDetailMap.endNode.event as EnterMapMapEvent).targetMap
                                )!!.resourceHandle
                            )
                ),
                mapIndicator,
                screen
            )
        } else {
            screen.screenBuilder.generateFromTemplate(
                "statusbar_sign",
                mapOf("textureName" to OnjString(curImage.resourceHandle)),
                mapIndicator,
                screen
            )
        }
    }

    private fun getImageData(name: String) = MapManager.mapImages.find { it.name == name && it.type == "name" }

    private fun getOptionTimeline(target: CustomFlexBox, goUp: Boolean) =
        Timeline.timeline {
            val action = CustomMoveByAction(
                target,
                Interpolation.exp10Out,
                relY = 20F * (if (goUp) 1 else -1),
                duration = 200F
            )
            action { target.addAction(action) }
            delayUntil { action.isComplete }
        }

    private fun hide(option: Pair<CustomFlexBox, String>) {
        val boxAction = getOptionTimeline(option.first, true).asAction()
        val widgetAction = getStatusbarOption(option).hide().asAction()
        timeline.appendAction(Timeline.timeline { parallelActions(widgetAction, boxAction) }.asAction())
        displayedOptionIndex = -1
    }

    private fun display(option: Pair<CustomFlexBox, String>) {
        val boxAction = getOptionTimeline(option.first, false).asAction()
        val widgetAction = getStatusbarOption(option).display().asAction()
        timeline.appendAction(Timeline.timeline { parallelActions(widgetAction, boxAction) }.asAction())
        displayedOptionIndex = optionWidgets.indexOf(option)
    }

    private fun getStatusbarOption(option: Pair<CustomFlexBox, String>) =
        screen.namedActorOrError(option.second) as InOutAnimationActor


    companion object{
        const val OVERLAY_NAME = "inStatusbarOverlay"
    }
}