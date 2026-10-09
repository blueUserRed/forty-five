package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.utils.Align
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Colors
import com.microwavestudios.fortyfive.utils.Timeline

object ToTitleScreenCreator {

    fun getSharedTitleScreen(creator: ScreenCreator): NavbarCreator.NavBarObject = with(creator) {
        val openTimelineCreator: () -> Timeline = {
            Timeline.timeline {
                FortyFive.toTitleScreen()
            }
        }
        val closeTimelineCreator: () -> Timeline = { Timeline.timeline { } }
        return NavbarCreator.NavBarObject(
            "Title screen",
            { box, _, _ -> with(box) {
                label("red wing", "T", Colors.Black, 30, backgroundHints = arrayOf("white_texture", "grey_texture")) {
                    squareDim(40f)
                    setAlignment(Align.center)
                    focusBackgrounds("white_texture", "grey_texture")
                }
            } },
            openTimelineCreator,
            closeTimelineCreator
        )
    }
}
