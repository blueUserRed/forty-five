package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.controllers.Controller
import com.badlogic.gdx.controllers.Controllers
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.utils.Align
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.plugin.ManagedPlugin
import com.microwavestudios.fortyfive.screen.actors.*
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline

object SettingsCreator {

    private const val settingsOpenScreenState = "settingsAreOpen"

    fun ScreenCreator.getSharedSettingsMenu(
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline
    ): Pair<CustomGroup, NavbarCreator.NavBarObject> {

        val openTimelineCreator: () -> Timeline
        val closeTimelineCreator: () -> Timeline

        val modal = InputManager.Modal(listOf(settingsGroup, NavbarCreator.navbarButtonGroup), screen)
        val filter = InputManager.FocusFilter(listOf(settingsGroup), screen)
        filter.start()

        val group = newGroup {
            width = worldWidth * 0.6f
            height = worldHeight
            backgroundHandle = "settings_background"

            y = -height

            box(isScrollable = true) {
                this as CustomScrollableBox
                scrollDirectionStart = CustomDirection.TOP
                addScrollbarFromDefaults(
                    CustomDirection.RIGHT,
                    "backpack_scrollbar",
                    "backpack_scrollbar_background",
                    barWidth = 10f
                )
                wrap = CustomWrap.NONE

                width = (worldWidth * 0.6f) * 0.9f
                height = worldHeight - 30f
                centerX()
                y = 0f
                paddingTop = 30f
                flexDirection = FlexDirection.COLUMN
                horizontalAlign = CustomAlign.CENTER
                verticalAlign = CustomAlign.START
                settings(this@getSharedSettingsMenu, width * 0.9f, events)
                verticalSpacer(200f)
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

    fun CustomBox.settings(creator: ScreenCreator, parentWidth: Float, events: EventPipeline) = with(creator) {

        val settings = mutableListOf<BindTarget<*>>()

        fun singleSettingSelector(name: String, bindTarget: String) =
            singleSettingSelector(creator, parentWidth, settings, events, name, bindTarget)

        reloadBox(creator, events, parentWidth, settings)

        header(creator, "Controllers")
        controllers(creator, events, parentWidth)
        singleSettingSelector("Controller Vibration:", "controllerVibration")

        header(creator, "Audio")
        singleSettingSlider(creator, parentWidth, "Master Volume", "masterVolume", 0f, 1f)
        singleSettingSlider(creator, parentWidth, "Music", "musicVolume", 0f, 1f)
        singleSettingSlider(creator, parentWidth, "Sound Effects", "soundEffectsVolume", 0f, 1f)

        header(creator, "General")
        singleSettingSelector("Show Screenshake:", "enableScreenShake")
        singleSettingSelector("Skip intro Screen:", "skipIntroScreen")
        singleSettingSelector("Fullscreen:", "fullscreen")
        singleSettingSelector("Use borderless window when in fullscreen:", "useBorderlessWindowFullscreen")

        header(creator, "Plugins")
        plugins(creator, parentWidth, events, settings)
    }

    private fun CustomBox.controllers(
        creator: ScreenCreator,
        events: EventPipeline,
        parentWidth: Float
    ) = with(creator) {
        box(isScrollable = true) {
            this as CustomScrollableBox
            scrollDirectionStart = CustomDirection.LEFT
            addScrollbarFromDefaults(
                CustomDirection.BOTTOM,
                "backpack_scrollbar",
                "backpack_scrollbar_background",
                barWidth = 10f
            )
            wrap = CustomWrap.NONE
            onLayoutAndNow {
                touchable = if (canBeScrolled) Touchable.enabled else Touchable.disabled
            }
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            height = 230f
            width = parentWidth

            fun controllersChanged() {
                children.toList().forEach { if (it !is CustomImageActor) removeActor(it) } // avoid removing scrollbar
                val controllers = Controllers.getControllers()
                controllers.forEach { controller ->
                    controller(creator, events, 200f, controller)
                    horizontalSpacer(30f)
                }
                if (controllers.isEmpty) label("red wing", "No Controllers connected", Color.FortyWhite, 22) {
                    width = parentWidth - 30f
                    setAlignment(Align.center)
                    syncHeight()
                }
            }

            events.watchFor<InputManager.ControllersChangedEvent> {
                controllersChanged()
            }

            controllersChanged()
        }
    }

    private fun CustomBox.controller(
        creator: ScreenCreator,
        events: EventPipeline,
        size: Float,
        controller: Controller
    ) = with(creator) {
        var isSelected = FortyFive.globalSave.currentControllerUid == controller.uniqueId
        box(backgroundHints = arrayOf("dark_brown_grey_texture", "lighter_brown_grey_texture")) {

            backgroundHandle = if (isSelected) {
                "lighter_brown_grey_texture"
            } else {
                "dark_brown_grey_texture"
            }
            width = size
            height = size
            flexDirection = FlexDirection.COLUMN
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.CENTER

            touchable = Touchable.enabled
            keyboardFocusable = KeyboardFocusable.LEAF
            joinGroup(settingsGroup)

            onInput(GameInputs.interact) {
                if (isSelected) {
                    events.fire(InputManager.NewControllerSelectedEvent(null))
                } else {
                    events.fire(InputManager.NewControllerSelectedEvent(controller.uniqueId))
                }
            }

            events.watchFor<InputManager.NewControllerSelectedEvent> { (uid) ->
                isSelected = uid == controller.uniqueId
                backgroundHandle = if (isSelected) {
                    "lighter_brown_grey_texture"
                } else {
                    "dark_brown_grey_texture"
                }
            }

            image {
                backgroundHandle = "white_texture"
                width = 50f
                height = 50f
                badTexture("controller icon")
            }
            verticalSpacer(10f)

            label("red wing", controller.name, Color.FortyWhite, 22) {
                touchable = Touchable.disabled
                width = size * 0.9f
                wrap = true
                setAlignment(Align.center)
                syncHeight()
            }

        }
    }

    private fun CustomBox.reloadBox(
        creator: ScreenCreator,
        events: EventPipeline,
        parentWidth: Float,
        targets: List<BindTarget<*>>
    ) = with(creator) {
        box {
            width = parentWidth
            height = 100f
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.CENTER
            var showsNeedRestart = false
            events.watchFor<SettingChanged> {
                val needsRestart = targets.any { !it.inSync }
                if (showsNeedRestart == needsRestart) return@watchFor
                clearChildren()
                if (needsRestart) {
                    showsNeedRestart = true
                    label("red wing", "Restart required", Color.HemoglobinRed, 23) {
                        syncDimensions()
                        setAlignment(Align.center)
                    }
                } else {
                    showsNeedRestart = false
                }
            }
            events.fire(SettingChanged)
        }
    }

    private fun CustomBox.header(creator: ScreenCreator, title: String) = with(creator) {
        label("red wing", title, fontSize = (32 * 1.4).toInt()) {
            marginTop = 50f
            marginBottom = 10f
            height = 50f
            syncWidth()
            fontColor = ScreenCreator.fortyWhite
        }
    }

    private fun CustomBox.plugins(
        creator: ScreenCreator,
        parentWidth: Float,
        events: EventPipeline,
        settings: MutableList<BindTarget<*>>,
    ) = with(creator) {
        box {
            flexDirection = FlexDirection.COLUMN
            width = parentWidth
            syncHeight()
            events.watchFor<ReloadPluginSettings> {
                clearChildren()
                val plugins = FortyFive.pluginManager.allPlugins
                plugins.forEach { plugin ->
                    pluginSettings(creator, plugin, events, settings, parentWidth)
                }
                if (plugins.isEmpty()) {
                    verticalSpacer(10f)
                    label("red wing", "No plugins found", Color.FortyWhite, 25) {
                        syncHeight()
                        width = parentWidth
                        setAlignment(Align.center)
                    }
                }
            }
            events.fire(ReloadPluginSettings)
        }
    }

    private fun CustomBox.pluginSettings(
        creator: ScreenCreator,
        plugin: ManagedPlugin,
        events: EventPipeline,
        settings: MutableList<BindTarget<*>>,
        parentWidth: Float
    ) = with(creator) {
        box {
            flexDirection = FlexDirection.ROW
            horizontalAlign = CustomAlign.SPACE_AROUND
            verticalAlign = CustomAlign.CENTER
            width = parentWidth
            height = 270f
            marginTop = 10f

            joinGroup(settingsGroup)
            backgroundHandle = "single_setting_background"

            box {
                relativeWidth(60f)
                syncHeight()
                flexDirection = FlexDirection.COLUMN
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.START

                verticalSpacer(10f)
                label("red wing", plugin.title, color = ScreenCreator.fortyWhite, fontSize = 28) {
                    relativeWidth(95f)
                    syncHeight()
                    wrap = true
                    joinGroup(settingsGroup)
                    keyboardFocusable = KeyboardFocusable.LEAF
                    touchable = Touchable.enabled
                    setAlignment(Align.center)
                }
                label("roadgeek", plugin.description, color = ScreenCreator.fortyWhite, fontSize = 18) {
                    relativeWidth(95f)
                    syncHeight()
                    wrap = true
                }
                verticalSpacer(10f)
            }
            val pluginSaveData = FortyFive.globalSave.getPluginSaveData(plugin.name)
            val needsAgreement = plugin.isRisky && !pluginSaveData.agreedToRisk
            box {
                relativeWidth(30f)
                syncHeight()
                flexDirection = FlexDirection.COLUMN
                horizontalAlign = CustomAlign.SPACE_AROUND
                verticalAlign = CustomAlign.CENTER
                if (plugin.isRisky) {
                    label("red wing", "Contains executable\ncode!", color = Color.Red, fontSize = 24) {
                        relativeWidth(100f)
                        syncHeight()
                        setAlignment(Align.center)
                    }
                    verticalSpacer(10f)
                }
                if (needsAgreement) {
                    box(backgroundHints = buttonBackgroundHints()) {
                        defaultButtonConfig()
                        relativeWidth(80f)
                        height = 50f
                        horizontalAlign = CustomAlign.CENTER
                        verticalAlign = CustomAlign.CENTER
                        label("red wing", "View Risk", Color.FortyWhite, 22) {
                            touchable = Touchable.disabled
                            syncDimensions()
                        }
                        joinGroup(settingsGroup)
                        touchable = Touchable.enabled
                        keyboardFocusable = KeyboardFocusable.LEAF
                        onInput(GameInputs.interact) {
                            val popup = PopupCreator.ShowPopup(
                                "Agree to plugin risk",
                                "This plugin contains executable code. If you choose to activate it, the plugin gains " +
                                        "full access to your computer, files, etc. Only enable plugins that are from a " +
                                        "known origin that you can trust. After you agree to this, you still have " +
                                        "to enable the plugin separately.",
                                listOf(
                                    "Agree" to true,
                                    "Disagree" to false
                                )
                            ) { result ->
                                if (!result) return@ShowPopup
                                FortyFive.globalSave.setPluginAgreement(plugin.name, true)
                                events.fire(ReloadPluginSettings)
                            }
                            events.fire(popup)
                        }
                    }
                } else {
                    val bindTarget = FortyFive.pluginManager.activatedBindTargetForPlugin(plugin)
                    settings.add(bindTarget)
                    lateinit var restartLabel: NewLabel
                    val callback = {
                        events.fire(SettingChanged)
                        restartLabel.isVisible = !bindTarget.inSync
                    }
                    selector("redwing100", bindTarget, 0.32f * 0.8f, Color.FortyWhite, callback) {
                        height = 50f
                        width = 250f
                        joinGroup(settingsGroup)
                        touchable = Touchable.enabled
                        keyboardFocusable = KeyboardFocusable.LEAF
                        onInput(GameInputs.switchSelectorToLeft) { switch(-1) }
                        onInput(GameInputs.switchSelectorToRight) { switch(1) }
                    }
                    restartLabel = label("red wing", "Restart required", Color.HemoglobinRed, 22) {
                        isVisible = false
                        syncDimensions()
                    }
                }
            }
        }
    }

    private fun CustomBox.singleSettingSelector(
        creator: ScreenCreator,
        parentWidth: Float,
        settings: MutableList<BindTarget<*>>,
        events: EventPipeline,
        name: String,
        bindTarget: String
    ) = singleSettingSelector(creator, parentWidth, settings, events, name, BindTargetFactory.getAnyType(bindTarget))

    private fun CustomBox.singleSettingSelector(
        creator: ScreenCreator,
        parentWidth: Float,
        settings: MutableList<BindTarget<*>>,
        events: EventPipeline,
        name: String,
        bindTarget: BindTarget<*>
    ) = with(creator) {
        settings.add(bindTarget)
        box {
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

            box {
                flexDirection = FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                relativeHeight(100f)
                width = 210f
                horizontalSpacer(10f)
                label("red wing", name, color = ScreenCreator.fortyWhite, fontSize = (32 * 0.8).toInt()) {
                    syncHeight()
                    width = 200f
                }
            }

            val selector: Selector

            box {
                flexDirection = FlexDirection.ROW
                relativeHeight(100f)
                width = 250f
                height = parent.height
                val callback = { events.fire(SettingChanged) }
                selector = selector("redwing100", bindTarget, 0.32f * 0.8f, Color.FortyWhite, callback) {
                    height = parent.height
                    width = 240f
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
    ) = with(creator) {
        box {
            flexDirection = FlexDirection.ROW
            width = parentWidth
            height = 50f
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            marginTop = 10f

            keyboardFocusable = KeyboardFocusable.LEAF
            joinGroup(settingsGroup)
            backgroundHandle = "single_setting_background"

            box {
                flexDirection = FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                horizontalSpacer(10f)
                width = 210f
                relativeHeight(100f)
                label("red wing", name, ScreenCreator.fortyWhite, (32 * 0.8).toInt()) {
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
                    height = parent.height
                    width = 200f
                }
                horizontalSpacer(10f)
            }

            onInput(GameInputs.switchSelectorToLeft) { slider.move(-0.1f) } // TODO: veeeeryyy baad
            onInput(GameInputs.switchSelectorToRight) { slider.move(0.1f) }
        }
    }

    private data object ReloadPluginSettings
    private data object SettingChanged

    const val settingsGroup: String = "settings-element"
}
