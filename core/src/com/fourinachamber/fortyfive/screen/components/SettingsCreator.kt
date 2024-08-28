package com.fourinachamber.fortyfive.screen.components

import com.fourinachamber.fortyfive.screen.general.CustomVerticalGroup
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator

object SettingsCreator {

    fun ScreenCreator.getSharedSettingsMenu(worldWidth: Float, worldHeight: Float) = newVerticalGroup {
        forcedPrefWidth = worldWidth * 0.6f
        forcedPrefHeight = worldHeight
        syncDimensions()
        backgroundHandle = "settings_background"

        centerX()
        y = -40f

        verticalGroup {
            forcedPrefWidth = (worldWidth * 0.6f) * 0.9f
            forcedPrefHeight = worldHeight
            verticalSpacer(30f)
            settings(this@getSharedSettingsMenu, forcedPrefWidth!!)
        }

    }

    fun CustomVerticalGroup.settings(creator: ScreenCreator, parentWidth: Float) = with(creator) {

        label("red_wing", "General") {
            setFontScale(1.4f)
            fontColor = ScreenCreator.fortyWhite
        }
        verticalSpacer(10f)

        singleSettingSelector(creator, parentWidth, "Show Screenshake", "enableScreenShake")
        singleSettingSelector(creator, parentWidth, "Start game on:", "startScreen")
        singleSettingSelector(creator, parentWidth, "Realtime based mechanics", "disableRt")
        singleSettingSelector(creator, parentWidth, "Fullscreen type (press 'f' to toggle)", "fullScreenMode")


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
    ) = with(creator) {
        horizontalGroup {
            forcedPrefWidth = parentWidth
            syncDimensions()
            backgroundHandle = "single_setting_background"

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
        max: Float
    ) = with(creator) {
        horizontalGroup {
            forcedPrefWidth = parentWidth
            syncDimensions()
            backgroundHandle = "single_setting_background"

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
