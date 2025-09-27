package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.*
import com.microwavestudios.fortyfive.screen.commonComponents.NavbarCreator
import com.microwavestudios.fortyfive.screen.commonComponents.PopupCreator
import com.microwavestudios.fortyfive.screen.commonComponents.PopupCreator.getSharedPopup
import com.microwavestudios.fortyfive.screen.commonComponents.ProfileCardCreator.getSharedProfileCard
import com.microwavestudios.fortyfive.screen.commonComponents.SettingsCreator.getSharedSettingsMenu
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.screen.screenController.TimelineController
import com.microwavestudios.fortyfive.utils.Color
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

    private var settingsOpen: Boolean = false

    private val events: EventPipeline = EventPipeline()

    private var currentlySelectedProfile: Profile.Preview? = null

    private val timelines: TimelineController = TimelineController()

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
            addOption("Start", true) { handleContinue() }
            addOption("Settings") { openSettings(blackOverlay, settingsObject) }
            addOption("View Credits") { FortyFive.screenManager.appendScreen(CreditsScreen) }
            addOption("Quit") { handleQuit() }
        }

        box {
            x = 400f
            y = worldHeight * 0.65f
            width = worldWidth * 0.7f
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.SPACE_AROUND

            profileSelector()
        }

        events.watchFor<SelectedProfileChanged> { event ->
            currentlySelectedProfile = event.newProfile
        }

        label("red_wing", "rework stage 1", Color.Black) {
            onLayoutAndNow {
                x = worldWidth - width - 10
                y = worldHeight - height - 10
            }
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
            hasNavbar = false,
            hasTutorial = false,
        )

        screen.afterMs(0) { // run when screen is shown
            val profileManager = FortyFive.profileManager
            val current = profileManager.currentProfile?.name
            profileManager.deselectProfile()
            val preview = if (current != null) {
                profileManager.availableProfiles.find { it.name == current }!!
            } else {
                profileManager.availableProfiles.first()
            }
            events.fire(SelectedProfileChanged(preview))
        }
    }

    private fun CustomBox.profileSelector() {
        val previews = FortyFive.profileManager.availableProfiles
        previews.forEach { preview ->

            actor(getSharedProfileCard(preview)) {
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF

                onInput(GameInputs.interact) {
                    if (!preview.loadedSuccessfully) return@onInput
                    events.fire(SelectedProfileChanged(preview))
                }

                events.watchFor<SelectedProfileChanged> { event ->
                    backgroundHandle = if (event.newProfile === preview) "grey_texture" else "white_texture"
                }
            }
        }
    }

    private fun handleContinue() {
        val selected = currentlySelectedProfile!!
        if (!selected.loadedSuccessfully) {
            FortyFive.soundPlayer.situation("not_allowed", screen)
            return
        }
        val success = FortyFive.profileManager.selectProfile(selected)
        if (success) {
            FortyFive.toMap()
        } else {
            // a bit ugly, but shouldn't really happen
            FortyFive.profileManager.reloadPreviews()
            FortyFive.screenManager.ensureNextScreen(TitleScreen)
            FortyFive.screenManager.screenFinished()
        }
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

    private fun openSettings(blackOverlay: CustomImageActor, settingsObject: NavbarCreator.NavBarObject) {
        if (settingsOpen) return
        settingsOpen = true
        timelines.appendMainTimeline(Timeline.timeline {
            include(settingsObject.openTimelineCreator())
            action {
                blackOverlay.isVisible = true
                blackOverlay.touchable = Touchable.enabled
            }
        })
    }

    private fun closeSettings(blackOverlay: CustomImageActor, settingsObject: NavbarCreator.NavBarObject) {
        if (!settingsOpen) return
        settingsOpen = false
        timelines.appendMainTimeline(Timeline.timeline {
            include(settingsObject.closeTimelineCreator())
            action {
                blackOverlay.isVisible = false
                blackOverlay.touchable = Touchable.disabled
            }
        })
    }

    private fun Group.addOption(
        displayText: String,
        onlyAvailableWhenProfileIsSelected: Boolean = false,
        action: () -> Unit
    ) = label("red_wing_bmp", displayText) {
        setFontScale(0.4f)
        syncWidth()
        syncHeight()
        touchable = Touchable.enabled
        keyboardFocusable = KeyboardFocusable.LEAF

        if (onlyAvailableWhenProfileIsSelected) events.watchFor<SelectedProfileChanged> { event ->
            fontColor = if (event.newProfile == null) Color.Grey else Color.Black
        }

        onInput(GameInputs.interact) {
            if (onlyAvailableWhenProfileIsSelected && currentlySelectedProfile == null) return@onInput
            action()
        }

        observeInputState(
            GameInputs.States.focused,
            { underline = true },
            { underline = false }
        )
    }

    private fun Group.addBullet(name: String) = box {
        positionType = PositionType.ABSOLUTE
        width = worldWidth
        height = worldHeight
        name(name)
        backgroundHandle = name
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        timelines
    )

    private class SelectedProfileChanged(val newProfile: Profile.Preview?)

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = TitleScreen::class
    }
}
