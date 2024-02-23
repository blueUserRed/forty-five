package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.EnterMapMapEvent
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.CustomMoveByAction
import com.fourinachamber.fortyfive.screen.general.customActor.InOutAnimationActor
import com.fourinachamber.fortyfive.screen.general.onButtonClick
import com.fourinachamber.fortyfive.utils.*
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
    private val optionWidgets: MutableList<Pair<CustomFlexBox, Either<String, () -> Unit>>> = mutableListOf()

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
            val action = if (i.hasKey<String>("actorName")) {
                i.get<String>("actorName").eitherLeft()
            } else {
                if (!i.hasKey<String>("actionName")) {
                    throw RuntimeException("statusBar Option that has no actor must define an action")
                }
                when (val name = i.get<String>("actionName")) {
                    "toTitleScreen" -> {
                        {
                            MapManager.changeToTitleScreen()
                        }
                    }
                    else -> throw RuntimeException("unknown statusBar action $name")
                }.eitherRight()
            }
            optionWidgets.add(curBox to action)
            curBox.onButtonClick {
                buttonClicked(curBox)
                SoundPlayer.situation("statusbar_button_clicked", screen)
            }
        }
    }

    private fun buttonClicked(clickedBox: CustomFlexBox) {
        if (timeline.isFinished) {
            val option = optionWidgets.find { it.first == clickedBox }!!
            when (displayedOptionIndex) {
                -1 -> {
                    screen.enterState(OVERLAY_NAME)
                    execute(option)
                }

                optionWidgets.indexOf(option) -> {
                    hide(option.first to (option.second as Either.Left).value)
                    screen.leaveState(OVERLAY_NAME)
                }

                else -> {
                    val optionToHide = optionWidgets[displayedOptionIndex]
                    hide(optionToHide.first to (optionToHide.second as Either.Left).value)
                    execute(option)
                }
            }
        }
    }

    private fun initMapIndicator() {
        if (mapIndicatorWidgetName == null) return
        val mapIndicator = screen.namedActorOrError(mapIndicatorWidgetName) as CustomFlexBox
        val currentMap = MapManager.currentDetailMap
        val curImage = if (currentMap.isArea) {
            getImageData(currentMap.name)
        } else {
            null
        }
        if (curImage != null) {
            screen.screenBuilder.generateFromTemplate(
                "statusbar_sign",
                mapOf("textureName" to OnjString(curImage.resourceHandle)),
                mapIndicator,
                screen
            )
        } else if (currentMap.startNode.event is EnterMapMapEvent && currentMap.endNode.event is EnterMapMapEvent) {
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
                                    (currentMap.startNode.event as EnterMapMapEvent).targetMap
                                ).resourceHandle
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
                                    (currentMap.endNode.event as EnterMapMapEvent).targetMap
                                ).resourceHandle
                            )
                ),
                mapIndicator,
                screen
            )
        } else {
            screen.screenBuilder.generateFromTemplate(
                "statusbar_text",
                mapOf("text" to OnjString("You are on a Road")), //this text is not by phiLLiPP, just a temp solution
                mapIndicator,
                screen
            )
        }
    }

    private fun getImageData(name: String) = MapManager.mapImages.find { it.name == name && it.type == "name" }
        ?: throw RuntimeException("no image for map $name")

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

    private fun execute(option: Pair<CustomFlexBox, Either<String, () -> Unit>>) {
        val boxAction = getOptionTimeline(option.first, false).asAction()
        when (val action = option.second) {

            is Either.Left -> {
                val widgetAction = getStatusbarOption(option.first to action.value).display().asAction()
                timeline.appendAction(Timeline.timeline { parallelActions(widgetAction, boxAction) }.asAction())
                displayedOptionIndex = optionWidgets.indexOf(option)
            }

            is Either.Right -> {
                action.value()
            }
        }
    }

    private fun getStatusbarOption(option: Pair<CustomFlexBox, String>) =
        screen.namedActorOrError(option.second) as InOutAnimationActor


    companion object {
        const val OVERLAY_NAME = "inStatusbarOverlay"
    }
}