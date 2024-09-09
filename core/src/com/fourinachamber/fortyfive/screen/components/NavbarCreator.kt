package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.EnterMapMapEvent
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.general.customActor.PropertyAction
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.EventPipeline
import com.fourinachamber.fortyfive.utils.Timeline
import ktx.actors.onClick

object NavbarCreator {

    fun ScreenCreator.getSharedNavBar(worldWidth: Float, worldHeight: Float, objects: List<NavBarObject>, screen: OnjScreen) = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

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
            onClick {
                if (!isVisible) return@onClick
                navBarEvents.fire(CloseNavBarButtons)
                isVisible = false
            }
        }

        val boxWithTimeline = boxWithTimeline(navBarTimeline, screen)

        actor(boxWithTimeline) {
            flexDirection = FlexDirection.COLUMN
            getNavBar(this@getSharedNavBar, worldWidth, worldHeight, navBarEvents, objects, navBarTimeline)
        }
    }

    private fun boxWithTimeline(timeline: Timeline, screen: OnjScreen): CustomBox = object : CustomBox(screen) {

        override fun act(delta: Float) {
            timeline.updateTimeline()
            super.act(delta)
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
    ) = with(creator) {
        var isOpened = false
        group {
            height = parent.parent.height * 0.7f
            width = 250f
            backgroundHandle = "statusbar_option"
            logicalOffsetY = 30f
            touchable = Touchable.enabled

            label("red_wing", obj.name) {
                centerX()
                y = 15f
                setAlignment(Align.center)
                fontColor = ScreenCreator.fortyWhite
                setFontScale(0.7f)
            }

            fun createAction(end: Float): PropertyAction = PropertyAction(
                this@group,
                this@group::logicalOffsetY,
                end,
                invalidateHierarchyOf = this
            ).also {
                it.duration = 0.12f
                it.interpolation = Interpolation.pow2In
            }

            events.watchFor<CloseNavBarButtons> {
                if (!isOpened) return@watchFor
                this@group.addAction(createAction(30f))
                isOpened = false
                timeline.appendAction(obj.closeTimelineCreator().asAction())
            }

            onClick {
                if (isOpened) {
                    this@group.addAction(createAction(30f))
                    isOpened = false
                    events.fire(ChangeBlackBackground(false))
                    timeline.appendAction(obj.closeTimelineCreator().asAction())
                } else {
                    events.fire(CloseNavBarButtons)
                    val action = createAction(16f)
                    this@group.addAction(action)
                    isOpened = true
                    events.fire(ChangeBlackBackground(true))
                    timeline.appendAction(obj.openTimelineCreator().asAction())
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
            val exitMap  = (map.endNode.event as? EnterMapMapEvent)?.targetMap
            if (enterMap == null || exitMap == null) {
                label("red_wing", "You are on a road", color = Color.WHITE)
            } else {
                label("red_wing", "Road between", color = Color.WHITE) {
                    setFontScale(0.8f)
                    syncWidth()
                }
                image {
                    backgroundHandle = nameTextureForMap(enterMap)
                    setupDimensionsForAreaName(this@locationIndicator)
                }
                label("red_wing", "and", color = Color.WHITE) { setFontScale(0.8f)}
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

        loadedDrawable?.let { setup(it) } ?: loadedDrawableResourceGetter.onResourceChange { drawable -> setup(drawable) }
    }

    private data object CloseNavBarButtons
    private data class ChangeBlackBackground(val show: Boolean)

    data class NavBarObject(
        val name: String,
        val openTimelineCreator: () -> Timeline,
        val closeTimelineCreator: () -> Timeline,
    )

}
