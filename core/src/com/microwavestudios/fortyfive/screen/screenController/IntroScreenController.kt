package com.microwavestudios.fortyfive.screen.screenController

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.ScreenController

class IntroScreenController(private val screen: RenderableScreen) : ScreenController() {

    override fun init(context: Any?) {
        FortyFive.screenManager.screenFinished() // screen change occurs after 5s because of the transitionAwayTime
        screen.afterMs(500) {
            FortyFive.soundPlayer.playMusicOnce("microwave_theme", screen)
        }
    }
}
