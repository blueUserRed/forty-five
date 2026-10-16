package com.microwavestudios.fortyfive.fullGameTests

import com.microwavestudios.fortyfive.testing.FullGameTest
import com.microwavestudios.fortyfive.utils.Timeline

class BasicFullGameTest : FullGameTest() {

    override val name: String = "BasicFullGameTest"

    override fun testTimeline(): Timeline = Timeline.timeline {

    }

}
