package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.EnterMapMapEvent
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.general.onSelectChange
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.EventPipeline
import com.fourinachamber.fortyfive.utils.Timeline
import ktx.actors.onClick

object NavbarCreator {

    const val navbarFocusGroup = "navbar_selectors"
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
            onClick {
                if (!isVisible) return@onClick
                isVisible = false
                val box = screen.namedActorOrError("navbar_buttonParent") as CustomBox
                val c = box.children.filterIsInstance<FocusableActor>().firstOrNull { it.isSelected } ?: return@onClick
                screen.changeSelectionFor(c as Actor)
            }
        }

        val boxWithTimeline = boxWithTimeline(navBarTimeline, screen)

        actor(boxWithTimeline) {
            flexDirection = FlexDirection.COLUMN
            if (isLeft) {
                getSmallerLeftNavBar(this@getSharedNavBar, worldWidth, worldHeight, navBarEvents, objects, navBarTimeline)
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
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            verticalAlign = CustomAlign.CENTER
            paddingLeft = 50f
            paddingRight = 50f

            box {
                flexDirection = FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                syncDimensions()
                image {
                    name("player_health_icon")
                    marginRight = 10f
                    width = 30f
                    height = 30f
                    backgroundHandle = "statusbar_lives"
                }

                label("red_wing", "{stat.playerLives}/{stat.maxPlayerLives}", isTemplate = true) {
                    fontColor = ScreenCreator.fortyWhite
                }
            }

            box {
                flexDirection = FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                syncDimensions()
                image {
                    name("cash_symbol")
                    marginRight = 10f
                    backgroundHandle = "cash_symbol"
                    width = 30f
                    height = 30f
                }

                label("red_wing", "\${stat.playerMoney}", isTemplate = true) {
                    fontColor = ScreenCreator.fortyWhite
                }
            }
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
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            verticalAlign = CustomAlign.CENTER
            paddingLeft = 50f
            paddingRight = 50f

            box {
                flexDirection = FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                syncDimensions()
                image {
                    name("player_health_icon")
                    marginRight = 10f
                    width = 30f
                    height = 30f
                    backgroundHandle = "statusbar_lives"
                }

                label("red_wing", "{stat.playerLives}/{stat.maxPlayerLives}", isTemplate = true) {
                    fontColor = ScreenCreator.fortyWhite
                }
            }

            box {
                syncDimensions()
                flexDirection = FlexDirection.ROW
                locationIndicator(creator)
            }

            box {
                flexDirection = FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                syncDimensions()
                image {
                    name("cash_symbol")
                    marginRight = 10f
                    backgroundHandle = "cash_symbol"
                    width = 30f
                    height = 30f
                }

                label("red_wing", "\${stat.playerMoney}", isTemplate = true) {
                    fontColor = ScreenCreator.fortyWhite
                }
            }
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
            height = parent.parent.height * 0.7f * scale
            width = 250f * scale
            backgroundHandle = "statusbar_option"
            logicalOffsetY = 30f
            setFocusableTo(true, this)
            group = navbarFocusGroup
            isSelectable = true

            label("red_wing", obj.name) {
                centerX()
                y = 15f
                setAlignment(Align.center)
                positionType = PositionType.ABSOLUTE
                fontColor = ScreenCreator.fortyWhite
                setFontScale(0.7f * scale)
            }

            fun createAction(end: Float): PropertyAction = PropertyAction(
                this@box,
                this@box::logicalOffsetY,
                end,
                invalidateHierarchyOf = this
            ).also {
                it.duration = 0.12f
                it.interpolation = Interpolation.pow2In
            }

            styles(
                normal = {
                    addAction(createAction(30f))
                },
                focused = {
                    addAction(createAction(25f))
                },
                selected = {
                    addAction(createAction(21f))
                },
                selectedAndFocused = {
                    addAction(createAction(16f))
                }
            )

            var isOpen = false
            events.watchFor<CloseNavBarButtons> {
                if (!isOpen) return@watchFor
                isOpen = false
                timeline.appendAction(obj.closeTimelineCreator().asAction())
                screen.escapeSelectionHierarchy(deselectActors = false)
                timeline.appendAction(Timeline.timeline { screen.leaveState(navbarOpenScreenState) }.asAction())
            }

            onSelectChange { _, _ ->
                if (isSelected) {
                    events.fire(ChangeBlackBackground(true))
                    timeline.appendAction(obj.openTimelineCreator().asAction())
                    timeline.appendAction(Timeline.timeline { screen.enterState(navbarOpenScreenState) }.asAction())
                    isOpen = true
                } else {
                    events.fire(CloseNavBarButtons)
                    events.fire(ChangeBlackBackground(false))
                }
            }
        }
    }

    private fun CustomBox.locationIndicator(creator: ScreenCreator) = with(creator) {
        val map = MapManager.currentDetailMap
        minHorizontalDistBetweenElements = 10f
        if (map.isArea) {
            image {
                backgroundHandle = nameTextureForMap(map.name)
                setupDimensionsForAreaName(this@locationIndicator)
            }
        } else {
            val enterMap = (map.startNode.event as? EnterMapMapEvent)?.targetMap
            val exitMap = (map.endNode.event as? EnterMapMapEvent)?.targetMap
            if (enterMap == null || exitMap == null) {
                label("red_wing", "You are on a road", color = Color.WHITE) {
                    positionType = PositionType.ABSOLUTE
                }
            } else {
                label("red_wing", "Road between", color = Color.WHITE) {
                    setFontScale(0.8f)
                    syncWidth()
                }
                image {
                    backgroundHandle = nameTextureForMap(enterMap)
                    setupDimensionsForAreaName(this@locationIndicator)
                }
                label("red_wing", "and", color = Color.WHITE) { setFontScale(0.8f) }
                image {
                    backgroundHandle = nameTextureForMap(exitMap)
                    setupDimensionsForAreaName(this@locationIndicator)
                }
            }
        }
    }

    private fun nameTextureForMap(mapName: String) =
        MapManager.mapImages.find { it.name == mapName && it.type == "name" }?.resourceHandle


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
}
