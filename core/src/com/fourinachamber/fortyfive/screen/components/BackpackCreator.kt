package com.fourinachamber.fortyfive.screen.components

import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Timeline

object BackpackCreator {

    fun ScreenCreator.getSharedBackpack(
        worldWidth: Float,
        worldHeight: Float,
    ): Pair<CustomGroup, NavbarCreator.NavBarObject> {

        val backpack = newGroup {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight

            box(isScrollable = true) {
                backgroundHandle = "backpack_deck_background"
                width = worldWidth * 0.4f
                height = worldHeight * 0.95f
                x = 0f
                y = 0f
            }

        }

        val navbarObject = NavbarCreator.NavBarObject(
            "Backpack",
            { Timeline.timeline {

            } },
            { Timeline.timeline {

            } },
        )

        return backpack to navbarObject
    }



}