package com.microwavestudios.fortyfive.screen.commonComponents

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Timeline

object ToTitleScreenCreator {

    fun ScreenCreator.getSharedTitleScreen(): NavbarCreator.NavBarObject {
        val openTimelineCreator: () -> Timeline = {
            Timeline.timeline {
                FortyFive.toTitleScreen()
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