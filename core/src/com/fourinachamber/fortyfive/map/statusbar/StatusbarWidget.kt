package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.graphics.g2d.Batch
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.EnterMapMapEvent
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.StatusbarOption
import com.fourinachamber.fortyfive.screen.general.onButtonClick
import com.fourinachamber.fortyfive.utils.Timeline
import onj.value.OnjObject
import onj.value.OnjString

class StatusbarWidget(
    private val map_indicator_name: String,
    private val options_name: String,
    private val inCenter: Boolean,
    private val options: List<OnjObject>,
    private val screen: OnjScreen
) : CustomFlexBox(screen) {

    private val timeline: Timeline = Timeline(mutableListOf())

    private var displayedOptionIndex: Int = -1

    private val optionWidgets: MutableList<Pair<CustomFlexBox, String>> = mutableListOf()
    override fun draw(batch: Batch?, parentAlpha: Float) {
        timeline.updateTimeline()
        super.draw(batch, parentAlpha)
    }

    private var isInited = false

    override fun layout() {
        if (!isInited) {
            initSpecialChildren()
            isInited = true
        }
        super.layout()
    }

    fun initSpecialChildren() {
        val mapIndicator = screen.namedActorOrError(map_indicator_name) as CustomFlexBox
        val curImage = MapManager.mapImages.find { it.name == MapManager.currentDetailMap.name + "_name" }
        if (curImage == null || !MapManager.currentDetailMap.isArea) {
            screen.screenBuilder.generateFromTemplate(
                "statusbar_text",
                mapOf("text" to OnjString("Road from ")),
                mapIndicator,
                screen
            )
            screen.screenBuilder.generateFromTemplate(
                "statusbar_sign",
                mapOf(
                    "textureName" to
                            OnjString(MapManager.mapImages.find { it.name == (MapManager.currentDetailMap.startNode.event as EnterMapMapEvent).targetMap + "_name" }!!.resourceHandle)
                ),
                mapIndicator,
                screen
            )
            screen.screenBuilder.generateFromTemplate(
                "statusbar_text",
                mapOf("text" to OnjString(" to ")),
                mapIndicator,
                screen
            );
            screen.screenBuilder.generateFromTemplate(
                "statusbar_sign",
                mapOf(
                    "textureName" to
                            OnjString(MapManager.mapImages.find { it.name == (MapManager.currentDetailMap.endNode.event as EnterMapMapEvent).targetMap + "_name" }!!.resourceHandle)
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

        val optionParent = screen.namedActorOrError(options_name) as CustomFlexBox
        for (i in options) {
            val curBox = screen.screenBuilder.generateFromTemplate(
                "statusbar_option",
                mapOf("text" to i.get<OnjString>("displayName")),
                optionParent,
                screen
            ) as CustomFlexBox
            val actorName = i.get<String>("actorName")
            optionWidgets.add(curBox to actorName)
            curBox.onButtonClick {
                if (timeline.isFinished) {
                    val option = optionWidgets.find { it.first == curBox }!!
                    when (displayedOptionIndex) {
                        -1 -> display(option)
                        optionWidgets.indexOf(option) -> hide(option)
                        else -> {
                            hide(optionWidgets[displayedOptionIndex])
                            display(option)
                        }
                    }
                }
            }
        }
        resortZIndices()
        timeline.startTimeline()
    }

    private fun getOptionTimeline(target: CustomFlexBox, goUp: Boolean) = Timeline.timeline {
        val dist=0.02F
        repeat(100) {
            action {
                target.moveBy(0F, if (goUp)dist else -dist)
            }
            delay(10)
        }
    }

    private fun hide(option: Pair<CustomFlexBox, String>) {
        val boxAction = getOptionTimeline(option.first, true).asAction()
        val widgetAction = (screen.namedActorOrError(option.second) as StatusbarOption).hide().asAction()
        timeline.appendAction(Timeline.timeline { parallelActions(widgetAction, boxAction) }.asAction())
        displayedOptionIndex = -1
    }

    private fun display(option: Pair<CustomFlexBox, String>) {
        val boxAction = getOptionTimeline(option.first, false).asAction()
        val widgetAction = (screen.namedActorOrError(option.second) as StatusbarOption).display().asAction()
        timeline.appendAction(Timeline.timeline { parallelActions(widgetAction, boxAction) }.asAction())
        displayedOptionIndex = optionWidgets.indexOf(option)
    }
}