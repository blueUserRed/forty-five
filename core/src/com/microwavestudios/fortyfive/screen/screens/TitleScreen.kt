package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.CustomImageActor
import com.microwavestudios.fortyfive.screen.commonComponents.NavbarCreator
import com.microwavestudios.fortyfive.screen.commonComponents.PopupCreator
import com.microwavestudios.fortyfive.screen.commonComponents.PopupCreator.getSharedPopup
import com.microwavestudios.fortyfive.screen.commonComponents.SettingsCreator.getSharedSettingsMenu
import com.microwavestudios.fortyfive.screen.screenController.TitleScreenController
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.alpha
import kotlin.reflect.KClass

class TitleScreen : ScreenCreator() {

    override val name: String = "titleScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

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

    private val events: EventPipeline = EventPipeline()

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
            addOption("Abandon Run") { handleAbandonRun() }
            addOption("Reset Game") { handleResetGame() }

            addOption("Settings") { openSettings(blackOverlay, settingsObject) }
            addOption("View Credits") { FortyFive.screenManager.appendScreen(CreditsScreen) }
            addOption("Quit") { handleQuit() }
        }

        actor(settings) {
            centerX()
            fixedZIndex = 10000
        }

        val popup = getSharedPopup(worldWidth, worldHeight, events)
        actor(popup)

        addDefaultOverlays(
            worldWidth,
            worldHeight,
            events,
            hasSettings = false, // added manually
            hasBackpack = false,
            hasNavbar = false,
            hasWarnings = false,
            hasTutorial = false,
        )
    }

    private fun handleQuit() {

        val popup = PopupCreator.ShowPopup(
            "Do you want to quit?",
            "Are you sure you want to leave this game. Without you, the wild west will be unsafe and dangerous" +
                    "for all inhabitants, so choose wisely.",
            listOf(
                "Quit" to true,
                "Cancel" to false
            )
        ) { result ->
            if (result) Gdx.app.exit()
        }
        events.fire(popup)
    }

    private fun handleAbandonRun() {

        val popup = PopupCreator.ShowPopup(
            "Do you want to abandon you run?",
            "All the progress you made will be lost",
            listOf(
                "Ok" to true,
                "Cancel" to false
            )
        ) { result ->
            if (result) FortyFive.newRun(false)
        }
        events.fire(popup)
    }

    private fun handleResetGame() {

        val popup = PopupCreator.ShowPopup(
            "Are you sure you want to reset the game?",
            "All progress you made will be lost forever",
            listOf(
                "Ok" to true,
                "Cancel" to false
            )
        ) { result ->
            if (result) FortyFive.resetAll()
        }
        events.fire(popup)
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

    private fun Group.addBullet(name: String) = box {
        positionType = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.PositionType.ABSOLUTE
        width = worldWidth
        height = worldHeight
        name(name)
        backgroundHandle = name
    }

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = TitleScreen::class
    }
}
