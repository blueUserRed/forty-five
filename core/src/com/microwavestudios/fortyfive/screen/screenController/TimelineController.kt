package com.microwavestudios.fortyfive.screen.screenController

import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.utils.Timeline

class TimelineController : ScreenController() {

    private val mainTimeline: Timeline = Timeline()
    private val sideTimelines: MutableList<Timeline> = mutableListOf()

    init {
        mainTimeline.startTimeline()
    }

    fun appendMainTimeline(timeline: Timeline) {
        mainTimeline.appendAction(timeline.asAction())
    }

    fun dispatchTimeline(timeline: Timeline) {
        timeline.startTimeline()
        sideTimelines.add(timeline)
    }

    override fun update() {
        mainTimeline.updateTimeline()
        sideTimelines.removeIf {
            it.updateTimeline()
            it.isFinished
        }
    }
}
