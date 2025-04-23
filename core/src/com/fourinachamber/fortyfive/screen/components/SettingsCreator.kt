package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.fourinachamber.fortyfive.screen.general.CustomVerticalGroup
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Timeline

object SettingsCreator {
    private const val settingsFocusGroup = "settings_singleSettingOption"
    private const val settingsOpenScreenState = "settingsAreOpen"

    fun ScreenCreator.getSharedSettingsMenu(
        worldWidth: Float,
        worldHeight: Float
    ): Pair<CustomVerticalGroup, NavbarCreator.NavBarObject> {

        val openTimelineCreator: () -> Timeline
        val closeTimelineCreator: () -> Timeline

        val group = newVerticalGroup {
            debug()
            forcedPrefWidth = worldWidth * 0.6f
            forcedPrefHeight = worldHeight
            syncDimensions()
            backgroundHandle = "settings_background"

            onLayout { x = parent.width / 2 - width / 2 }
            y = -height

            verticalGroup {
                forcedPrefWidth = (worldWidth * 0.6f) * 0.9f
                forcedPrefHeight = worldHeight
                verticalSpacer(30f)
                settings(this@getSharedSettingsMenu, forcedPrefWidth!!)
            }

            fun getAction(to: Float) = MoveToAction().also {
                it.duration = 0.2f
                it.interpolation = Interpolation.pow2In
                it.y = to
                it.x = x
            }

            openTimelineCreator = {
                val action = getAction(-120f)
                Timeline.timeline {
                    action {
                        addAction(action)
                        screen.enterState(settingsOpenScreenState)
                    }
                    delayUntil { action.isComplete }
                }
            }

            closeTimelineCreator = {
                val action = getAction(-height)
                Timeline.timeline {
                    action {
                        screen.leaveState("settingsOpenScreenState")
                        addAction(action)
                    }
                    delayUntil { action.isComplete }
                }
            }

        }

        return group to NavbarCreator.NavBarObject(
            "Settings",
            openTimelineCreator,
            closeTimelineCreator
        )
    }

    fun CustomVerticalGroup.settings(creator: ScreenCreator, parentWidth: Float) = with(creator) {

        label("red_wing", "General") {
            setFontScale(1.4f)
            fontColor = ScreenCreator.fortyWhite
        }
        verticalSpacer(10f)

        singleSettingSelector(creator, parentWidth, "Show Screenshake", "enableScreenShake", true)
        singleSettingSelector(creator, parentWidth, "Start game on:", "startScreen")
        singleSettingSelector(creator, parentWidth, "Realtime based mechanics", "disableRt")
        singleSettingSelector(creator, parentWidth, "Window Mode:", "windowMode")


        label("red_wing", "Audio") {
            setFontScale(1.4f)
            fontColor = ScreenCreator.fortyWhite
        }
        verticalSpacer(10f)

        singleSettingSlider(creator, parentWidth, "Master Volume", "masterVolume", 0f, 1f)
        singleSettingSlider(creator, parentWidth, "Music", "musicVolume", 0f, 1f)
        singleSettingSlider(creator, parentWidth, "Sound Effects", "soundEffectsVolume", 0f, 1f)
    }

    private fun CustomVerticalGroup.singleSettingSelector(
        creator: ScreenCreator,
        parentWidth: Float,
        name: String,
        bindTarget: String,
        isFirst: Boolean = false,
    ) = with(creator) {
        horizontalGroup {
            forcedPrefWidth = parentWidth
            syncDimensions()

//            styles(
//                normal = {
//                    backgroundHandle = "single_setting_background"
//                },
//                focused = {
//                    backgroundHandle = "single_setting_background_focused"
//                }
//            )

            horizontalSpacer(20f)

            label("red_wing", name, color = ScreenCreator.fortyWhite)

            horizontalGrowingSpacer(1f)

            selector("red_wing", bindTarget) {
                onLayoutAndNow { forcedPrefHeight = parent.height }
                forcedPrefWidth = 200f
                syncWidth()
            }

            horizontalSpacer(20f)
        }
        verticalSpacer(25f)
    }

    private fun CustomVerticalGroup.singleSettingSlider(
        creator: ScreenCreator,
        parentWidth: Float,
        name: String,
        bindTarget: String,
        min: Float,
        max: Float,
        isFirst: Boolean = false,
    ) = with(creator) {
        horizontalGroup {
            forcedPrefWidth = parentWidth
            syncDimensions()

//            styles(
//                normal = {
//                    backgroundHandle = "single_setting_background"
//                },
//                focused = {
//                    backgroundHandle = "single_setting_background_focused"
//                }
//            )

            horizontalSpacer(20f)

            label("red_wing", name, color = ScreenCreator.fortyWhite)

            horizontalGrowingSpacer(1f)

            slider(min, max, bindTarget) {
                onLayoutAndNow { forcedPrefHeight = parent.height }
                forcedPrefWidth = 200f
                syncDimensions()
            }

            horizontalSpacer(20f)
        }
        verticalSpacer(25f)
    }
}
