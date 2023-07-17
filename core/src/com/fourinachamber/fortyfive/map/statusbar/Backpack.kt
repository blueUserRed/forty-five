package com.fourinachamber.fortyfive.map.statusbar

import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.InOutAnimationActor
import com.fourinachamber.fortyfive.utils.Timeline

class Backpack(screen: OnjScreen) : CustomFlexBox(screen), InOutAnimationActor {
    override fun display(): Timeline {
        return Timeline.timeline {
            action {
                println("now opening")
            }
            delay(200)
            action {
                println("now open")
            }
        }
    }

    override fun hide(): Timeline {
        return Timeline.timeline {
            action {
                println("now closing")
            }
            delay(200)
            action {
                println("now closed")
            }
        }
    }
}