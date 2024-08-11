package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import onj.value.OnjNamedObject

class IntroScreenController(private val screen: OnjScreen, onj: OnjNamedObject) : ScreenController() {

    override fun init(context: Any?) {
        Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
        FortyFive.changeToScreen(ConfigFileManager.screenBuilderFor("titleScreen")) // screen change occurs after 5s because of the transitionAwayTime
        screen.afterMs(500) { // changeToInitialScreen causes a lagSpike, this prevents it from interrupting the sound
            SoundPlayer.playMusicOnce("microwave_theme", screen)
        }
    }
}
