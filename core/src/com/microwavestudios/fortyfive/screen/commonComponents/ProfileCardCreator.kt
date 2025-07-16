package com.microwavestudios.fortyfive.screen.commonComponents

import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator

object ProfileCardCreator {

    fun ScreenCreator.getSharedProfileCard(
        profile: Profile.Preview
    ): CustomGroup = newBox {

        width = 250f
        height = 400f

        flexDirection = FlexDirection.COLUMN
        backgroundHandle = "white_texture"

        label("red_wing", "Profile ${profile.name}") {
            setFontScale(1.3f)
        }
        if (profile.exists) {
            label("red_wing", "current area: ${profile.currentArea}")
            label("red_wing", "money: ${profile.money}")
            label("red_wing", "cards in collection: ${profile.collection!!.size}")
            label("red_wing", "run:")
            val runPreview = profile.runPreview
            if (runPreview != null) {
                label("red_wing", "   health: ${runPreview.playerHealth}")
                label("red_wing", "   type: ${runPreview.run.type.displayName}")
                label("red_wing", "   difficulty: ${runPreview.run.difficulty}")
            } else {
                label("red_wing", "   No run active")
            }
        } else {
            label("red_wing", "no save yet")
        }
    }


}
