package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.customActor.OffSettable
import kotlin.math.sin

class TitleScreenController : ScreenController() {

    private lateinit var screen: OnjScreen

    private var transitionAwayVelocity: Float = -1f

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
    }

    private fun doTransitionAwayAnim() {
        transitionAwayVelocity += Gdx.graphics.deltaTime * 80f
        repeat(15) { i ->
            val actor = screen.namedActorOrError("title_screen_bullet_${i + 1}")
            actor as OffSettable
            actor.offsetY -= transitionAwayVelocity
        }
    }

    override fun update() {
        if (transitionAwayVelocity != -1f) {
            doTransitionAwayAnim()
            return
        }
        if (OnjScreen.transitionAwayScreenState in screen.screenState) {
            transitionAwayVelocity = 0f
            repeat(15) { i ->
                screen.afterMs(i * 10) {
                    SoundPlayer.situation("title_screen_card_drop", screen)
                }
            }
            return
        }
        repeat(15) { i ->
            val actor = screen.namedActorOrError("title_screen_bullet_${i + 1}")
            actor as OffSettable
            actor.offsetY = sin(TimeUtils.millis() * 0.001 + i * i * 100).toFloat() * 6f
        }
    }
}
