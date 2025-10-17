package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.config.MapImageData
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.screen.SquareDropShadow
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.actors.*
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline

object NavbarCreator {

    const val navbarOpenScreenState = "navbarIsOpen"

    fun ScreenCreator.getSharedNavBar(
        worldWidth: Float,
        worldHeight: Float,
        objects: List<NavBarObject>,
        screen: OnjScreen,
        isLeft: Boolean = false,
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
            if (isLeft) {
                getSmallerLeftNavBar(
                    this@getSharedNavBar,
                    worldWidth,
                    worldHeight,
                    navBarEvents,
                    objects,
                    navBarTimeline
                )
            } else {
                getNavBar(this@getSharedNavBar, worldWidth, worldHeight, navBarEvents, objects, navBarTimeline)
            }
        }
    }

    private fun boxWithTimeline(timeline: Timeline, screen: OnjScreen): CustomBox = object : CustomBox(screen) {

        override fun act(delta: Float) {
            timeline.updateTimeline()
            super.act(delta)
        }
    }

    private fun CustomGroup.healthLabel(creator: ScreenCreator) = with(creator) {
        val profile = FortyFive.profileManager.currentProfile
        box {
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            profile ?: return@box
            if (!profile.isRunActive) return@box
            syncDimensions()
            image {
                name("player_health_icon")
                marginRight = 10f
                width = 30f
                height = 30f
                backgroundHandle = "statusbar_lives"
            }

            val healthLabel = label("red wing", "${profile.healthInRun}", isTemplate = true, fontSize = 32) {
                fontColor = ScreenCreator.fortyWhite
                syncDimensions()
            }
            profile.events.watchFor<Profile.HealthChangedEvent> { event ->
                healthLabel.setText(event.newHealth.toString())
            }
        }
    }

    private fun CustomBox.cashLabel(creator: ScreenCreator) = with(creator) {
        val profile = FortyFive.profileManager.currentProfile
        box {
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            syncDimensions()
            profile ?: return@box
            image {
                name("cash_symbol")
                marginRight = 10f
                backgroundHandle = "cash_symbol"
                width = 30f
                height = 30f
            }

            val cashLabel = label("red wing", "\$${profile.playerMoney}", fontSize = 32) {
                fontColor = ScreenCreator.fortyWhite
                syncDimensions()
            }
            profile.events.watchFor<Profile.MoneyChangedEvent> { event ->
                cashLabel.setText("\$${event.newMoney}")
            }
        }
    }

    private fun CustomBox.getSmallerLeftNavBar(
        creator: ScreenCreator,
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline,
        objects: List<NavBarObject>,
        timeline: Timeline
    ) = with(creator) {
        x = 0f
        y = 0f
        width = worldWidth * 0.32f
        height = 130f
        onLayoutAndNow { y = worldHeight - height }

        box {
            flexDirection = FlexDirection.ROW
            relativeWidth(100f)
            relativeHeight(50f)
            backgroundHandle = "statusbar_background_left"
            badTexture("navbar small", lowRes = true)
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            verticalAlign = CustomAlign.CENTER
            paddingLeft = 50f
            paddingRight = 50f

            healthLabel(creator)
            cashLabel(creator)
        }

        box {
            name("navbar_buttonParent")
            fixedZIndex = -1
            relativeWidth(92f)
            relativeHeight(50f)
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.START
            horizontalAlign = CustomAlign.SPACE_AROUND
            objects.forEach {
                navBarButton(creator, events, it, timeline, scale = 0.9f)
            }
        }
    }

    private fun CustomBox.getNavBar(
        creator: ScreenCreator,
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline,
        objects: List<NavBarObject>,
        timeline: Timeline
    ) = with(creator) {
        x = 0f
        y = 0f
        width = worldWidth * 0.7f
        height = 130f
        centerX()
        onLayoutAndNow { y = worldHeight - height }

        box {
            flexDirection = FlexDirection.ROW
            relativeWidth(100f)
            relativeHeight(50f)
            backgroundHandle = "statusbar_background"
            badTexture("navbar", lowRes = true)
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            verticalAlign = CustomAlign.CENTER
            paddingLeft = 50f
            paddingRight = 50f

            healthLabel(creator)

            box {
                flexDirection = FlexDirection.ROW
                locationIndicator(creator)
                syncDimensions()
            }

            cashLabel(creator)
        }

        box {
            name("navbar_buttonParent")
            fixedZIndex = -1
            relativeWidth(100f)
            relativeHeight(50f)
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.START
            horizontalAlign = CustomAlign.SPACE_AROUND
            objects.forEach {
                navBarButton(creator, events, it, timeline)
            }
        }

    }

    private fun CustomBox.navBarButton(
        creator: ScreenCreator,
        events: EventPipeline,
        obj: NavBarObject,
        timeline: Timeline,
        scale: Float = 1f,
    ) = with(creator) {
        box {
            val baseLogicalOffsetY = 35F
            val openOffsetY = -9F
            val focusedOffsetY = -5F
            height = parent.parent.height * 0.7f * scale
            width = 250f * scale
            backgroundHandle = "statusbar_option"
            joinGroup(navbarButtonGroup)
            touchable = Touchable.enabled

            label("red wing", obj.name, fontSize = (32 * 0.7 * scale).toInt()) {
                centerX()
                y = 20f
                setAlignment(Align.center)
                positionType = PositionType.ABSOLUTE
                fontColor = ScreenCreator.fortyWhite
                syncHeight()
            }

            fun createAction(end: Float): PropertyAction<Float> = PropertyAction(
                this@box,
                this@box::logicalOffsetY,
                baseLogicalOffsetY + end,
                invalidateHierarchyOf = this
            ).also {
                it.duration = 0.12f
                it.interpolation = Interpolation.pow2In
            }

            var isOpen = false

            keyboardFocusable = KeyboardFocusable.LEAF
            logicalOffsetY = baseLogicalOffsetY

            val dropShadow = SquareDropShadow(
                Color.BLACK, 2f, -2f, 1.1f, blurFactor = 0.5f, showDropShadow = false
            )
            this.dropShadow = dropShadow

            observeInputState(
                GameInputs.States.focused,
                {
                    if (isOpen) addAction(createAction(openOffsetY + focusedOffsetY))
                        else addAction(createAction(focusedOffsetY))

                    dropShadow.showDropShadow = true
                },
                {
                    if (isOpen) addAction(createAction(openOffsetY))
                        else addAction(createAction(0f))
                    dropShadow.showDropShadow = false

                }
            )
            this.isInInputState(GameInputs.States.focused)

            events.watchFor<CloseNavBarButtons> {
                if (!isOpen) return@watchFor
                isOpen = false
                timeline.appendAction(obj.closeTimelineCreator().asAction())
                timeline.appendAction(Timeline.timeline {
                    action { screen.leaveState(navbarOpenScreenState) }
                }.asAction())
                val isFocused = this.observedStates.contains(GameInputs.States.focused) //TODO fix this, its always true
                addAction(createAction(if (isFocused) focusedOffsetY else 0f))
            }

            onInput(GameInputs.interact) {
                if (isOpen) {
                    events.fire(CloseNavBarButtons)
                    events.fire(ChangeBlackBackground(false))
                } else {
                    events.fire(CloseNavBarButtons)
                    events.fire(ChangeBlackBackground(true))
                    timeline.appendAction(Timeline.timeline {
                        include(obj.openTimelineCreator())
                        action { screen.enterState(navbarOpenScreenState) }
                    }.asAction())
                    isOpen = true
                    addAction(createAction(openOffsetY + focusedOffsetY))
                }
            }
        }
    }

    private fun CustomBox.locationIndicator(creator: ScreenCreator) = with(creator) {
        val profile = FortyFive.profileManager.currentProfile ?: return@with
        val map = profile.currentMapSaver.currentMap
        minHorizontalDistBetweenElements = 10f
        if (map.isArea) {
            image {
                backgroundHandle = nameTextureForMap(map.name)
                setupDimensionsForAreaName(this@locationIndicator)
            }
        } else {
            label("red wing", "You are on a road", color = Color.WHITE, fontSize = 32) {
                syncDimensions()
            }
        }
    }

    private fun nameTextureForMap(mapName: String) =
        ConfigFileManager
            .mapConfig
            .images
            .find { it.name == mapName && it.type == MapImageData.Type.NAME }
            ?.resourceHandle


    private fun CustomImageActor.setupDimensionsForAreaName(parent: CustomBox) {

        fun setup(drawable: Drawable) {
            val height = 40f
            val aspectRatio = drawable.minWidth / drawable.minHeight
            this.height = height
            this.width = height * aspectRatio
            parent.invalidate()
            parent.invalidateChildren()
        }

        loadedDrawable?.let { setup(it) }
            ?: loadedDrawableResourceGetter.onResourceChange { drawable -> setup(drawable) }
    }

    private data object CloseNavBarButtons
    private data class ChangeBlackBackground(val show: Boolean)

    data class NavBarObject(
        val name: String,
        val openTimelineCreator: () -> Timeline,
        val closeTimelineCreator: () -> Timeline,
    )

    const val navbarButtonGroup: String = "navbar-button"
}
