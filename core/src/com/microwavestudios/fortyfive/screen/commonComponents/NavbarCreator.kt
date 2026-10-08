package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.config.MapImageData
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.SquareDropShadow
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.actors.*
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Colors
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline
import kotlin.math.max

object NavbarCreator {

    fun ScreenCreator.getSharedNavBar(
        worldWidth: Float,
        worldHeight: Float,
        objects: List<NavBarObject>,
        screen: RenderableScreen,
    ) = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight
        touchable = Touchable.childrenOnly

        val navBarEvents = EventPipeline()
        val navBarTimeline = Timeline()
        navBarTimeline.startTimeline()

        box {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            backgroundHandle = "transparent_black_texture"
            navBarEvents.watchFor<ChangeBlackBackground> { (show) ->
                isVisible = show
            }
            isVisible = false
            touchable = Touchable.enabled
            onInput(GameInputs.interact) {
                if (!isVisible) return@onInput
                isVisible = false
                navBarEvents.fire(CloseNavBarButtons)
            }
            onInput(GameInputs.cancel) { // Global input, works even when this actor isn't focused
                if (!isVisible) return@onInput
                isVisible = false
                navBarEvents.fire(CloseNavBarButtons)
            }
        }

        val boxWithTimeline = boxWithTimeline(navBarTimeline, screen)
        actor(boxWithTimeline) {
            flexDirection = FlexDirection.COLUMN
            constructNavbar(this@getSharedNavBar, objects, worldWidth, worldHeight, navBarEvents, navBarTimeline)
        }
    }

    private fun CustomBox.constructNavbar(
        creator: ScreenCreator,
        objects: List<NavBarObject>,
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline,
        timeline: Timeline
    ) = with(creator) {
        onLayoutAndNow { width = max(prefWidth, worldWidth * 0.2f) }
        height = 80f
        onLayoutAndNow { x = worldWidth - width }
        y = worldHeight - height
        backgroundHandle = "navbar_bg"
        flexDirection = FlexDirection.ROW
        verticalAlign = CustomAlign.CENTER

        val profile = FortyFive.profileManager.currentProfile ?: return@with

        horizontalSpacer(20f)

        image {
            squareDim(30f)
            backgroundHandle = "health_icon"
        }

        horizontalSpacer(5f)

        val healthLabel = label("red wing", "${profile.healthInRun}", Colors.Black, 32) {
            syncDimensions()
        }
        screen.events.watchFor<Profile.HealthChangedEvent> { event ->
            healthLabel.setText(event.newHealth.toString())
        }

        horizontalSpacer(20f)

        val cashLabel = label("red wing", "\$${profile.playerMoney}", Color.BLACK, 32) {
            name("navbar_cash")
            syncDimensions()
        }
        screen.events.watchFor<Profile.MoneyChangedEvent> { event ->
            cashLabel.setText("\$${event.newMoney}")
        }

        objects.forEach { obj ->
            horizontalSpacer(10f)
            setupNavbarButton(obj, screen, events, timeline)
        }

        horizontalSpacer(20f)
    }

    private fun CustomBox.setupNavbarButton(
        obj: NavBarObject,
        screen: RenderableScreen,
        events: EventPipeline,
        timeline: Timeline
    ) {
        var isOpen = false

        val openListeners = mutableListOf<() -> Unit>()
        val closeListeners = mutableListOf<() -> Unit>()

        val actor = obj.representation(
            this@setupNavbarButton,
            { openListeners.add(it) },
            { closeListeners.add(it) },
        )
        actor.joinGroup(navbarButtonGroup)
        actor.actor.touchable = Touchable.enabled
        actor.keyboardFocusable = KeyboardFocusable.LEAF

        events.watchFor<CloseNavBarButtons> {
            if (!isOpen) return@watchFor
            isOpen = false
            closeListeners.forEach { it() }
            timeline.appendAction(obj.closeTimelineCreator().asAction())
        }

        actor.onInput(GameInputs.interact) {
            FortyFive.soundPlayer.situation("navbar_button_clicked", screen)
            if (isOpen) {
                events.fire(CloseNavBarButtons)
                events.fire(ChangeBlackBackground(false))
            } else {
                events.fire(CloseNavBarButtons)
                events.fire(ChangeBlackBackground(true))
                timeline.appendAction(Timeline.timeline {
                    include(obj.openTimelineCreator())
                }.asAction())
                isOpen = true
                openListeners.forEach { it() }
            }
        }
    }

    private fun boxWithTimeline(timeline: Timeline, screen: RenderableScreen): CustomBox = object : CustomBox(screen) {

        override fun act(delta: Float) {
            timeline.updateTimeline()
            super.act(delta)
        }
    }

    private data object CloseNavBarButtons
    private data class ChangeBlackBackground(val show: Boolean)

    data class NavBarObject(
        val name: String,
        val representation: (
            box: CustomBox,
            addOpenListener: (() -> Unit) -> Unit,
            addCloseListener: (() -> Unit) -> Unit,
        ) -> InputActor,
        val openTimelineCreator: () -> Timeline,
        val closeTimelineCreator: () -> Timeline,
    )

    const val navbarButtonGroup: String = "navbar-button"
}
