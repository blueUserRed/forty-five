package com.fourinachamber.fortyfive.screen.gameWidgets

import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.Timeline
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
