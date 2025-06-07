package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.keyInput.GameInputs
import com.fourinachamber.fortyfive.keyInput.KeyboardFocusable
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.ScreenManager
import com.fourinachamber.fortyfive.screen.components.NavbarCreator
import com.fourinachamber.fortyfive.screen.components.SettingsCreator.getSharedSettingsMenu
import com.fourinachamber.fortyfive.screen.gameWidgets.TitleScreenController
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.alpha
import kotlin.reflect.KClass

class TitleScreen : ScreenCreator() {

    override val name: String = "titleScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    val popupWidgetName = "popup_widget"

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 800 //800 fits good with the animation
    )

    override fun getScreenControllers(): List<ScreenController> = listOf(
        TitleScreenController(screen)
    )

    private val controller: TitleScreenController by lazy {
        screen.screenControllers.filterIsInstance<TitleScreenController>().first()
    }

    private var settingsOpen: Boolean = false

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        image {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            backgroundHandle = "title_screen_background"
        }

        val blackOverlay = image {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            backgroundHandle = "black_texture"
            alpha = 0.3f
            fixedZIndex = 100
            isVisible = false
            touchable = Touchable.disabled
        }

        for (i in 1..15) {
            addBullet("title_screen_bullet_$i")
        }
        val (settings, settingsObject) = getSharedSettingsMenu(worldWidth, worldHeight)

        blackOverlay.onInput(GameInputs.interact) {
            closeSettings(blackOverlay, settingsObject)
        }

        screen.inputManager.onInput(GameInputs.cancel) {
            closeSettings(blackOverlay, settingsObject)
        }

        box {
            x = 120F
            y = worldHeight * 0.65F
            addOption("Continue") { FortyFive.toMap() }
            addOption("Abandon Run") {}
            addOption("Reset Game") {}

            addOption("Settings") { openSettings(blackOverlay, settingsObject) }
            addOption("View Credits") { FortyFive.screenManager.transitionImmediate(CreditsScreen) }
            addOption("Quit") { Gdx.app.exit() } // TODO: fix popup
//            addOption("Quit") {handleQuit() }
        }

        actor(settings) {
            centerX()
            fixedZIndex = 10000
        }
    }

    private fun CustomBox.handleQuit() {
        showPopup(
            "Do you want to quit?",
            "Are you sure you want to leave this game. Without you, the wild west will be unsafe and dangerous for all inhabitants, so choose wisely.",
            mapOf(
                "Quit" to { Gdx.app.exit() },
                "Cancel" to null
            )
        )
    }

    private fun openSettings(blackOverlay: CustomImageActor, settingsObject: NavbarCreator.NavBarObject) {
        if (settingsOpen) return
        settingsOpen = true
        controller.timeline.appendAction(Timeline.timeline {
            include(settingsObject.openTimelineCreator())
            action {
                blackOverlay.isVisible = true
                blackOverlay.touchable = Touchable.enabled
            }
        }.asAction())
    }

    private fun closeSettings(blackOverlay: CustomImageActor, settingsObject: NavbarCreator.NavBarObject) {
        if (!settingsOpen) return
        settingsOpen = false
        controller.timeline.appendAction(Timeline.timeline {
            include(settingsObject.closeTimelineCreator())
            action {
                blackOverlay.isVisible = false
                blackOverlay.touchable = Touchable.disabled
            }
        }.asAction())
    }

    private fun Group.showPopup(title: String, description: String, actions: Map<String, (() -> Unit)?>) {
        var curParent = parent
        while (curParent.parent != null) curParent = curParent.parent
        curParent.box {
            screen.addNamedActor(popupWidgetName, this)
            name(popupWidgetName)
            positionType = PositionType.ABSOLUTE
            backgroundHandle = "detail_widget_background_big"
            width = worldWidth * 0.3f
            height = worldHeight * 0.3f
            x = (worldWidth - width) / 2
            y = (worldHeight - height) / 2
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.SPACE_BETWEEN
            paddingTop = 25f
            paddingBottom = -20f
            debug = true
            label("red_wing", title, color = Color.FortyWhite) {
                setFontScale(1.4f)
                syncWidth()
            }
            advancedText("red_wing", Color.FortyWhite, 0.8f) {
                fitContentHeight = true
                setRawText(description, null)
                relativeWidth(80f)
            }

            box {
                flexDirection = FlexDirection.ROW
                horizontalAlign = CustomAlign.SPACE_AROUND
                relativeWidth(100F)
                relativeHeight(20f)

                val labels = mutableListOf<CustomLabel>()
                actions.entries.forEach {
                    labels.add(label("red_wing", it.key) {
//                        onSelect {
//                            it.value?.invoke()
//                            if (it.value == null) removePopup()
//                        }
                        onLayoutAndNow {
                            height = prefHeight * 1.2f
                            setAlignment(Align.center)
                        }
//                        if (it.value == null) screen.focusSpecific(this)
                    })
                }

                val curMax = labels.maxOf { it.prefWidth } * 1.2f
                labels.forEach { it.width = curMax }
            }
        }

//        screen.addToSelectionHierarchy(
//            FocusableParent(listOf(SelectionTransition(groups = listOf(popupFocusGroup))),
//                onLeave = {
//                    removePopup()
//                })
//        )
    }

    fun removePopup() {
        screen.namedActorOrNull(popupWidgetName)?.remove()
    }

    private fun Group.addOption(displayText: String, action: () -> Unit) = label("red_wing_bmp", displayText) {
        setFontScale(0.4f)
        syncWidth()
        syncHeight()
        touchable = Touchable.enabled
        keyboardFocusable = KeyboardFocusable.LEAF

        onInput(GameInputs.interact) {
            action()
        }

        observeInputState(
            GameInputs.States.focused,
            { underline = true },
            { underline = false }
        )
    }

    fun Group.addBullet(name: String) = box {
        positionType = PositionType.ABSOLUTE
        width = worldWidth
        height = worldHeight
        name(name)
        backgroundHandle = name
    }

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = TitleScreen::class
    }
}
