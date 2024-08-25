package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.EnterMapMapEvent
import com.fourinachamber.fortyfive.screen.general.CustomHorizontalGroup
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator

object SharedComponents {

    val fortyWhite: Color = Color.valueOf("F0EADD")

    fun ScreenCreator.getSharedNavBar(worldWidth: Float) = newVerticalGroup {
        width = worldWidth * 0.7f
        height = 130f

        debug()

        horizontalGroup {
            debug()
            forcedPrefWidth = parent.width
            forcedPrefHeight = parent.height * 0.5f
            syncDimensions()
            backgroundHandle = "statusbar_background"

            horizontalSpacer(70f)

            image {
                name("player_health_icon")
                forcedPrefWidth = 30f
                forcedPrefHeight = 30f
                syncDimensions()
                backgroundHandle = "statusbar_lives"
            }

            horizontalSpacer(10f)

            label("red_wing", "{stat.playerLives}/{stat.maxPlayerLives}", isTemplate = true) {
                fontColor = fortyWhite
            }

            horizontalGrowingSpacer(0.5f)

            locationIndicator(this@getSharedNavBar)

            horizontalGrowingSpacer(0.5f)

            image {
                name("cash_symbol")
                backgroundHandle = "cash_symbol"
                forcedPrefWidth = 30f
                forcedPrefHeight = 30f
                syncDimensions()
            }

            horizontalSpacer(10f)

            label("red_wing", "\${stat.playerMoney}", isTemplate = true) {
                fontColor = fortyWhite
            }

            horizontalSpacer(70f)

        }

    }

    private fun CustomHorizontalGroup.locationIndicator(creator: ScreenCreator) = with(creator) {
        val map = MapManager.currentDetailMap
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
                label("red_wing", "Road between", color = Color.WHITE) { setFontScale(0.8f) }
                horizontalSpacer(10f)
                image {
                    backgroundHandle = nameTextureForMap(enterMap)
                    setupDimensionsForAreaName(this@locationIndicator)
                }
                horizontalSpacer(10f)
                label("red_wing", "and", color = Color.WHITE) { setFontScale(0.8f) }
                horizontalSpacer(10f)
                image {
                    backgroundHandle = nameTextureForMap(exitMap)
                    setupDimensionsForAreaName(this@locationIndicator)
                }
            }
        }
    }

    private fun nameTextureForMap(mapName: String) =
        MapManager.mapImages.find { it.name == mapName && it.type == "name" }?.resourceHandle


    private fun CustomImageActor.setupDimensionsForAreaName(parent: CustomHorizontalGroup) {

        fun setup(drawable: Drawable) {
            val height = 40f
            val aspectRatio = drawable.minWidth / drawable.minHeight
            this.forcedPrefHeight = height
            this.forcedPrefWidth = height * aspectRatio
            parent.invalidate()
            parent.invalidateChildren()
        }

        loadedDrawable?.let { setup(it) } ?: loadedDrawableResourceGetter.onResourceChange { drawable -> setup(drawable) }
    }

}
