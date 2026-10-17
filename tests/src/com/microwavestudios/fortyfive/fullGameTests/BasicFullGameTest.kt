package com.microwavestudios.fortyfive.fullGameTests

import com.badlogic.gdx.Input
import com.microwavestudios.fortyfive.profile.IProfileManager
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.testing.FullGameTest
import com.microwavestudios.fortyfive.testing.mockservices.MockProfile
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager
import com.microwavestudios.fortyfive.utils.Timeline

class BasicFullGameTest : FullGameTest() {

    override val name: String = "BasicFullGameTest"


    override fun testTimeline(): Timeline = Timeline.timeline {
        waitUntilScreenIsLoaded()
//        brieflyPressKey(Input.Keys.TAB)
//        brieflyPressKey(Input.Keys.ENTER)
//        waitUntilScreenIsLoaded()
    }

}
