package com.microwavestudios.fortyfive.screen.screenController

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.utils.Timeline
import onj.value.OnjObject

class FadeToBlackScreenController(private val screen: OnjScreen, onj: OnjObject) : ScreenController() {

    private val duration: Int = onj.get<Long>("duration").toInt()

    private val timeline: Timeline = Timeline().apply { startTimeline() }

    override fun init(context: Any?) {
    }

    override fun update() {
        timeline.updateTimeline()
    }

    override fun onTransitionAway() {
        FortyFive.currentRenderPipeline?.getFadeToBlackTimeline(duration, true)?.let {
            timeline.appendAction(it.asAction())
        }
    }
}
