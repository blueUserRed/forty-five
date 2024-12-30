package com.fourinachamber.fortyfive.screen.components

import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Timeline

object ToTitleScreenCreator {

    fun ScreenCreator.getSharedTitleScreen(
    ): NavbarCreator.NavBarObject {
        val openTimelineCreator: () -> Timeline = {
            Timeline.timeline {
                MapManager.changeToTitleScreen()
            }
        }
        val closeTimelineCreator: () -> Timeline = { Timeline.timeline { } }
        return NavbarCreator.NavBarObject(
            "Title screen",
            openTimelineCreator,
            closeTimelineCreator
        )
    }
}