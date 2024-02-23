package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.OffSettable
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import kotlin.math.sin

class TitleScreenController : ScreenController() {

    private lateinit var screen: OnjScreen

    private var transitionAwayVelocity: Float = -1f
    private val timeline: Timeline = Timeline()

    private var isConfirmed: Boolean = false

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        timeline.startTimeline()
        TemplateString.updateGlobalParam(
            "title_screen.startButtonText",
            if (SaveState.playerCompletedFirstTutorialEncounter) "Continue" else "Start you journey"
        )
    }

    override fun onUnhandledEvent(event: Event) = when (event) {

        is QuitGameEvent -> timeline.appendAction(Timeline.timeline {
            action {
                screen.enterState(showConfirmationPopupScreenState)
                TemplateString.updateGlobalParam("title_screen.popupText", "Are you sure you want to quit?")
            }
            delayUntil { isConfirmed || showConfirmationPopupScreenState !in screen.screenState }
            action {
                if (isConfirmed) Gdx.app.exit()
            }

        }.asAction())

        is AbandonRunEvent -> timeline.appendAction(Timeline.timeline {
            action {
                screen.enterState(showConfirmationPopupScreenState)
                TemplateString.updateGlobalParam("title_screen.popupText", "Are you sure you want to abandon" +
                        " your run? You will loose all the progress you made and all bullets that haven't been saved yet.")
            }
            delayUntil { isConfirmed || showConfirmationPopupScreenState !in screen.screenState }
            action {
                if (isConfirmed) FortyFive.newRun(false)
                isConfirmed = false
                screen.leaveState(showConfirmationPopupScreenState)
            }

        }.asAction())

        is ResetGameEvent -> timeline.appendAction(Timeline.timeline {
            action {
                screen.enterState(showConfirmationPopupScreenState)
                TemplateString.updateGlobalParam("title_screen.popupText", "Are you sure you want to reset" +
                        " the game? The game will behave as if it were freshly installed.")
            }
            delayUntil { isConfirmed || showConfirmationPopupScreenState !in screen.screenState }
            action {
                if (isConfirmed) FortyFive.resetAll()
                TemplateString.updateGlobalParam(
                    "title_screen.startButtonText",
                    if (SaveState.playerCompletedFirstTutorialEncounter) "Continue" else "Start you journey"
                )
                isConfirmed = false
                screen.leaveState(showConfirmationPopupScreenState)
            }

        }.asAction())

        is PopupConfirmationEvent -> {
            isConfirmed = true
        }

        else -> {}

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
        timeline.updateTimeline()
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

    companion object {
        const val showConfirmationPopupScreenState = "show_confirmation_popup"
    }

}
