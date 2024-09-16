package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fortyfive.keyInput.KeyInputCondition
import com.fourinachamber.fortyfive.keyInput.KeyInputMapEntry
import com.fourinachamber.fortyfive.keyInput.KeyInputMapKeyEntry
import com.fourinachamber.fortyfive.keyInput.KeyPreset
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.screen.general.CustomHorizontalGroup
import com.fourinachamber.fortyfive.screen.general.CustomVerticalGroup
import com.fourinachamber.fortyfive.screen.general.customActor.Selector
import com.fourinachamber.fortyfive.screen.general.customActor.Slider
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Timeline
import java.security.Key
import kotlin.math.ceil

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
            forcedPrefWidth = worldWidth * 0.6f
            forcedPrefHeight = worldHeight
            syncDimensions()
            backgroundHandle = "settings_background"
            fixedZIndex = NavbarCreator.navbarZIndex + 1

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
                        screen.addToSelectionHierarchy(
                            FocusableParent(
                                listOf(
                                    SelectionTransition(
                                        groups = listOf(
                                            NavbarCreator.navbarFocusGroup,
                                            settingsFocusGroup + "_first",
                                        )
                                    ),
                                    SelectionTransition(
                                        groups = listOf(
                                            settingsFocusGroup, //this is needed, or it just jumps to the third element when coming from a side option
                                            settingsFocusGroup + "_first",
                                        )
                                    )
                                )
                            )
                        )
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
            group = if (isFirst) settingsFocusGroup + "_first" else settingsFocusGroup
            setFocusableTo(true, this)
            forcedPrefWidth = parentWidth
            syncDimensions()

            styles(
                normal = {
                    backgroundHandle = "single_setting_background"
                },
                focused = {
                    backgroundHandle = "single_setting_background_focused"
                }
            )

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
            group = if (isFirst) settingsFocusGroup + "_first" else settingsFocusGroup
            setFocusableTo(true, this)

            forcedPrefWidth = parentWidth
            syncDimensions()

            styles(
                normal = {
                    backgroundHandle = "single_setting_background"
                },
                focused = {
                    backgroundHandle = "single_setting_background_focused"
                }
            )

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

    val settingsKeyMap = listOf<KeyInputMapEntry>(
        KeyInputMapEntry(
            100,
            KeyInputCondition.And(
                KeyInputCondition.ScreenState(NavbarCreator.navbarOpenScreenState),
                KeyInputCondition.ScreenState(settingsOpenScreenState)
            ),
            singleKeys = KeyPreset.LEFT.keys + KeyPreset.RIGHT.keys,
            { screen, keycode ->
                val par = screen.focusedActor ?: return@KeyInputMapEntry false
                if (par !is Layout || par !is CustomHorizontalGroup) return@KeyInputMapEntry false
                val selectionActor = par.children.filterIsInstance<Selector>().firstOrNull()
                val isLeft=KeyPreset.fromKeyCode(keycode) == KeyPreset.LEFT
                if (selectionActor != null) {
                    if (isLeft) selectionActor.onClick(0F)
                    else selectionActor.onClick(selectionActor.width)
                    return@KeyInputMapEntry true
                }
                val sliderActor = par.children.filterIsInstance<Slider>().firstOrNull()
                if (sliderActor != null) {
                    var oldPos = sliderActor.cursorPos
                    if (isLeft) sliderActor.updatePos((oldPos - 0.1F) * sliderActor.width)
                    else sliderActor.updatePos((oldPos + 0.1f) * sliderActor.width)
                    return@KeyInputMapEntry true
                }
                return@KeyInputMapEntry false
            }
        ),
        KeyInputMapEntry(
            100,
            KeyInputCondition.And(
                KeyInputCondition.ScreenState(NavbarCreator.navbarOpenScreenState),
                KeyInputCondition.ScreenState(settingsOpenScreenState)
            ),
            singleKeys = KeyPreset.ACTION.keys,
            { screen, keycode ->
                val par = screen.focusedActor ?: return@KeyInputMapEntry false
                if (par !is Layout || par !is CustomHorizontalGroup) return@KeyInputMapEntry false
                val selectionActor = par.children.filterIsInstance<Selector>().firstOrNull()
                if (selectionActor != null) {
                    selectionActor.onClick(selectionActor.width)
                    return@KeyInputMapEntry true
                }
                val sliderActor = par.children.filterIsInstance<Slider>().firstOrNull()
                if (sliderActor != null) {
                    if (sliderActor.cursorPos == 1F) sliderActor.updatePos(0F)
                    else {
                        sliderActor.updatePos(ceil((sliderActor.cursorPos + 0.01f) * 4) / 4F * sliderActor.width)
                    }
                    return@KeyInputMapEntry true
                }
                return@KeyInputMapEntry false
            }
        ),
    )
}
