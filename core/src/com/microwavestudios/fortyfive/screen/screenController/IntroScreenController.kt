package com.microwavestudios.fortyfive.screen.screenController

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.ScreenController

class IntroScreenController(private val screen: OnjScreen) : ScreenController() {

    override fun init(context: Any?) {
        OnjScreen.toggleFullScreen(true)
        FortyFive.screenManager.screenFinished() // screen change occurs after 5s because of the transitionAwayTime
        screen.afterMs(500) { // changeToInitialScreen causes a lagSpike, this prevents it from interrupting the sound
            FortyFive.soundPlayer.playMusicOnce("microwave_theme", screen)
        }
    }
}
