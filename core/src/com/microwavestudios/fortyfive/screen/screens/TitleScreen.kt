package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.particle.ParticleSystem
import com.microwavestudios.fortyfive.particle.TextureParticleRenderer
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
import com.microwavestudios.fortyfive.screen.screenController.ParticleSystemScreenController
import com.microwavestudios.fortyfive.screen.screenController.TimelineController
import com.microwavestudios.fortyfive.utils.Colors
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.Utils
import com.microwavestudios.fortyfive.utils.alpha
import com.microwavestudios.fortyfive.utils.between
import com.microwavestudios.fortyfive.utils.minus
import kotlin.reflect.KClass

class TitleScreen : ScreenCreator() {

    override val name: String = "titleScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        name to noTransition(),
        "*" to geometricFadeTransition()
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

        lateinit var blackOverlay: CustomImageActor
        val (settings, settingsObject) = getSharedSettingsMenu(worldWidth, worldHeight, events)
        box {
            x = 70F
            y = worldHeight * 0.6f
            addOption("Start", false) { FortyFive.toMap() }
//            addOption("Start", true) { handleContinue() }
            addOption("Settings") { openSettings(blackOverlay, settingsObject) }
            addOption("View Credits") {
                FortyFive.screenManager.appendScreen(CreditsScreen)
                FortyFive.screenManager.screenFinished()
            }
            addOption("Quit") { handleQuit() }
        }

//        box {
//            x = 400f
//            y = worldHeight * 0.65f
//            width = worldWidth * 0.7f
//            flexDirection = FlexDirection.ROW
//            verticalAlign = CustomAlign.CENTER
//            horizontalAlign = CustomAlign.SPACE_AROUND
//
//            profileSelector()
//        }

        events.watchFor<SelectedProfileChanged> { event ->
            currentlySelectedProfile = event.newProfile
            FortyFive.globalSave.lastUsedProfile = event.newProfile?.name
        }

        label("red wing", "demo", Colors.Black, 32) {
            onLayoutAndNow {
                x = worldWidth - width - 10
                y = worldHeight - height - 10
            }
            syncDimensions()
        }

        blackOverlay = image {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            backgroundHandle = "black_texture"
            alpha = 0.3f
            isVisible = false
            touchable = Touchable.disabled
        }

        blackOverlay.onInput(GameInputs.interact) {
            closeSettings(blackOverlay, settingsObject)
        }

        screen.inputManager.onInput(GameInputs.cancel) {
            closeSettings(blackOverlay, settingsObject)
        }
        actor(settings) {
            centerX()
        }

        addDefaultOverlays(
            worldWidth,
            worldHeight,
            events,
            hasBackpack = false,
            canHaveRunBoard = false,
            hasSettings = false, // added manually
            hasNavbar = false,
            hasTutorial = false,
        )

        screen.afterMs(0) {
            val success = FortyFive.profileManager.selectProfile(FortyFive.profileManager.availableProfiles.first())
        }
//        screen.afterMs(0) { // run when screen is shown
//            val profileManager = FortyFive.profileManager
//            val current = profileManager.currentProfile?.name
//            profileManager.deselectProfile()
//            val preview = if (current != null) {
//                profileManager.availableProfiles.find { it.name == current }
//            } else {
//                val lastUsed = FortyFive.globalSave.lastUsedProfile
//                profileManager.availableProfiles.find { it.name == lastUsed }
//            }
//            events.fire(SelectedProfileChanged(preview ?: profileManager.availableProfiles.first()))
//        }
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
    ) = label("red wing", displayText, fontSize = 50) {
        syncWidth()
        syncHeight()
        touchable = Touchable.enabled
        keyboardFocusable = KeyboardFocusable.LEAF

        if (onlyAvailableWhenProfileIsSelected) events.watchFor<SelectedProfileChanged> { event ->
            fontColor = if (event.newProfile == null) Colors.Grey else Colors.Black
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

    override fun getScreenControllers(): List<ScreenController> = listOf(
        timelines, //  ParticleSystemScreenController(testParticleSystem, screen)
    )

    private class SelectedProfileChanged(val newProfile: Profile.Preview?)

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = TitleScreen::class
    }
}
