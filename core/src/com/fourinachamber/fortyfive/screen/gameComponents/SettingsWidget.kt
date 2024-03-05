package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.InOutAnimationActor
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.utils.Timeline

/**
 * this widget doesn't actually contain the settings, these are implemented in share_widgets.onj.
 * This widget is only necessary because the statusBar can't deal with popups that don't extend InOutAnimationActor
 */
class SettingsWidget(private val screen: OnjScreen) : Widget(), StyledActor, InOutAnimationActor {

    override var styleManager: StyleManager? = null

    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    override fun display(): Timeline = Timeline.timeline {
        action {
            screen.enterState("show_settings")
        }
    }

    override fun hide(): Timeline = Timeline.timeline {
        action {
            screen.leaveState("show_settings")
        }
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

}
