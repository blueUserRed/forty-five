package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.Timeline

class CreditScreenController : ScreenController() {

    private lateinit var creditsScroller: CustomFlexBox
    private lateinit var screen: OnjScreen
    private var scrollSpeed: Int = 0

    private val timeline: Timeline = Timeline()

    override fun init(onjScreen: OnjScreen, context: Any?) {
        SoundPlayer.skipInMusic(18.0f)
        screen = onjScreen
        creditsScroller = onjScreen.namedActorOrError("credits_scroller") as? CustomFlexBox
            ?: throw RuntimeException("actor named credits_scroller must be a CustomFlexBox")
        timeline.startTimeline()
        timeline.appendAction(Timeline.timeline {
            delay(2_400)
            action {
                scrollSpeed = 120
            }
            delayUntil { scrollSpeed == 0 }
            action {
                screen.enterState("finished")
            }
            includeAction(screen.confirmationClickTimelineAction())
            action {
                MapManager.changeToTitleScreen()
            }
            include(FortyFive.currentRenderPipeline!!.getFadeToBlackTimeline(1100))
        }.asAction())
    }

    override fun update() {
        timeline.updateTimeline()
        if (creditsScroller.offsetY >= creditsScroller.height - screen.viewport.worldHeight) {
            scrollSpeed = 0
            return
        }
        creditsScroller.offsetY += scrollSpeed * Gdx.graphics.deltaTime
    }
}