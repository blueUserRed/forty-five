package com.fourinachamber.fortyfive.screen.gameWidgets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.PermaSaveState
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.OffSettable
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import kotlin.math.sin

class TitleScreenController(private val screen: OnjScreen) : ScreenController() {

    private var transitionAwayVelocity: Float = -1f
    val timeline: Timeline = Timeline()

    private var isConfirmed: Boolean = false

    override fun init(context: Any?) {
        timeline.startTimeline()
        TemplateString.updateGlobalParam(
            "title_screen.startButtonText",
            if (SaveState.playerCompletedFirstTutorialEncounter) "Continue" else "Start your Journey"
        )
    }

    override fun onShow() {
        FortyFive.soundPlayer.changeMusicTo(SoundPlayer.Theme.TITLE)
    }

    private fun doTransitionAwayAnim() {
        transitionAwayVelocity += Gdx.graphics.deltaTime * 80f
        repeat(15) { i ->
            val actor = screen.namedActorOrError("title_screen_bullet_${i + 1}")
            actor as OffSettable
            actor.drawOffsetY -= transitionAwayVelocity
        }
    }

    override fun update() {
        timeline.updateTimeline()
        if (transitionAwayVelocity != -1f) {
            doTransitionAwayAnim()
            return
        }
        if (OnjScreen.transitionAwayScreenState in screen.screenState) {
            transitionAwayVelocity = 0f
            repeat(1) { i ->
                screen.afterMs(i * 30) {
                    FortyFive.soundPlayer.situation("title_screen_card_drop", screen)
                }
            }
            return
        }
        repeat(15) { i ->
            val actor = screen.namedActorOrError("title_screen_bullet_${i + 1}")
            actor as OffSettable
            actor.drawOffsetY = sin(TimeUtils.millis() * 0.001 + i * i * 100).toFloat() * 6f
        }
    }

    companion object {
        const val showConfirmationPopupScreenState = "show_confirmation_popup"
        const val showInDevelopmentReminder = "show_in_development_reminder"
    }

}
