package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.fourinachamber.fortyfive.keyInput.GameInputs
import com.fourinachamber.fortyfive.keyInput.InputManager
import com.fourinachamber.fortyfive.keyInput.KeyboardFocusable
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.general.customActor.Selector
import com.fourinachamber.fortyfive.screen.general.customActor.Slider
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Timeline

object SettingsCreator {

    private const val settingsOpenScreenState = "settingsAreOpen"

    fun ScreenCreator.getSharedSettingsMenu(
        worldWidth: Float,
        worldHeight: Float
    ): Pair<CustomGroup, NavbarCreator.NavBarObject> {

        val openTimelineCreator: () -> Timeline
        val closeTimelineCreator: () -> Timeline

        val modal = InputManager.Modal(listOf(settingsGroup, NavbarCreator.navbarButtonGroup), screen)
        val filter = InputManager.FocusFilter(listOf(settingsGroup), screen)
        filter.start()

        val group = newGroup {
            debug()
            width = worldWidth * 0.6f
            height = worldHeight
            backgroundHandle = "settings_background"

            y = -height

            box {
                width = (worldWidth * 0.6f) * 0.9f
                height = worldHeight - 30f
                centerX()
                paddingTop = 30f
                flexDirection = FlexDirection.COLUMN
                horizontalAlign = CustomAlign.CENTER
                verticalAlign = CustomAlign.START
                settings(this@getSharedSettingsMenu, width)
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
                        modal.push()
                        filter.end()
                    }
                    delayUntil { action.isComplete }
                }
            }

            closeTimelineCreator = {
                val action = getAction(-height)
                Timeline.timeline {
                    action {
                        screen.leaveState(settingsOpenScreenState)
                        addAction(action)
                        modal.finished()
                        filter.start()
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

    fun CustomBox.settings(creator: ScreenCreator, parentWidth: Float) = with(creator) {

        label("red_wing", "General") {
            marginBottom = 10f
            setFontScale(1.4f)
            fontColor = ScreenCreator.fortyWhite
        }

        singleSettingSelector(creator, parentWidth, "Show Screenshake", "enableScreenShake", true)
        singleSettingSelector(creator, parentWidth, "Start game on:", "startScreen")
        singleSettingSelector(creator, parentWidth, "Realtime based mechanics", "disableRt")
        singleSettingSelector(creator, parentWidth, "Window Mode:", "windowMode")


        label("red_wing", "Audio") {
            marginTop = 20f
            marginBottom = 10f
            setFontScale(1.4f)
            fontColor = ScreenCreator.fortyWhite
        }

        singleSettingSlider(creator, parentWidth, "Master Volume", "masterVolume", 0f, 1f)
        singleSettingSlider(creator, parentWidth, "Music", "musicVolume", 0f, 1f)
        singleSettingSlider(creator, parentWidth, "Sound Effects", "soundEffectsVolume", 0f, 1f)
    }

    private fun CustomBox.singleSettingSelector(
        creator: ScreenCreator,
        parentWidth: Float,
        name: String,
        bindTarget: String,
        isFirst: Boolean = false,
    ) = with(creator) {
        box(backgroundHints = arrayOf("single_setting_background", "single_setting_background_focused")) {
            flexDirection = FlexDirection.ROW
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            verticalAlign = CustomAlign.CENTER
            width = parentWidth
            height = 50f
            marginTop = 10f

            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.enabled
            joinGroup(settingsGroup)
            backgroundHandle = "single_setting_background"
            observeInputState(
                GameInputs.States.focused,
                { backgroundHandle = "single_setting_background_focused" },
                { backgroundHandle = "single_setting_background" }
            )

            box {
                flexDirection = FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                relativeHeight(100f)
                width = 210f
                horizontalSpacer(10f)
                label("red_wing", name, color = ScreenCreator.fortyWhite) {
                    setFontScale(0.8f)
                    syncHeight()
                    width = 200f
                }
            }

            val selector: Selector

            box {
                flexDirection = FlexDirection.ROW
                relativeHeight(100f)
                width = 210f
                selector = selector("red_wing", bindTarget, fontScale = 0.8f) {
                    onLayoutAndNow { height = parent.height }
                    width = 200f
                }
                horizontalSpacer(10f)
            }

            onInput(GameInputs.switchSelectorToLeft) { selector.switch(-1) }
            onInput(GameInputs.switchSelectorToRight) { selector.switch(1) }
        }
    }

    private fun CustomBox.singleSettingSlider(
        creator: ScreenCreator,
        parentWidth: Float,
        name: String,
        bindTarget: String,
        min: Float,
        max: Float,
        isFirst: Boolean = false,
    ) = with(creator) {
        box(backgroundHints = arrayOf("single_setting_background", "single_setting_background_focused")) {
            flexDirection = FlexDirection.ROW
            width = parentWidth
            height = 50f
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            marginTop = 10f

            keyboardFocusable = KeyboardFocusable.LEAF
            joinGroup(settingsGroup)
            backgroundHandle = "single_setting_background"
            observeInputState(
                GameInputs.States.focused,
                { backgroundHandle = "single_setting_background_focused" },
                { backgroundHandle = "single_setting_background" }
            )

            box {
                flexDirection = FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                horizontalSpacer(10f)
                width = 210f
                relativeHeight(100f)
                label("red_wing", name, color = ScreenCreator.fortyWhite) {
                    setFontScale(0.8f)
                    width = 200f
                    syncHeight()
                    marginLeft = 10f
                }
            }

            val slider: Slider

            box {
                flexDirection = FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                relativeHeight(100f)
                width = 210f
                slider = slider(min, max, bindTarget) {
                    onLayoutAndNow { height = parent.height }
                    width = 200f
                    marginRight = 10f
                }
                horizontalSpacer(10f)
            }

            onInput(GameInputs.switchSelectorToLeft) { slider.move(-0.1f) } // TODO: veeeeryyy baad
            onInput(GameInputs.switchSelectorToRight) { slider.move(0.1f) }
        }
    }

    const val settingsGroup: String = "settings-element"
}
