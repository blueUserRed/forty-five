package com.microwavestudios.fortyfive.screen.commonComponents

import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color

object ProfileCardCreator {

    fun ScreenCreator.getSharedProfileCard(
        profile: Profile.Preview
    ): CustomGroup = newBox {

        width = 250f
        height = 400f

        flexDirection = FlexDirection.COLUMN
        backgroundHandle = "white_texture"

        if (profile.loadedSuccessfully) {
            normalCard(this@getSharedProfileCard, profile)
        } else {
            failedCard(this@getSharedProfileCard, profile)
        }
    }

    private fun CustomBox.failedCard(creator: ScreenCreator, profile: Profile.Preview) = with(creator) {
        label("red wing", "Profile ${profile.name}", fontSize = (32 * 1.3).toInt()) { syncHeight() }
        val message = when (profile.loadFailure) {
            null -> ""
            Profile.LoadFailure.MARKED_CORRUPTED, Profile.LoadFailure.CORRUPTED_FILES -> "Profile is corrupted!"
            Profile.LoadFailure.VERSION_TOO_NEW -> "Profile was created by a newer version of the game; try upgrading the game"
            Profile.LoadFailure.VERSION_TOO_OLD -> "Profile was created by an older version of the game; try downgrading the game"
        }
        label("red wing", message, Color.Red, (32 * 0.8).toInt()) {
            syncHeight()
            wrap = true
            relativeWidth(100f)
            syncHeight()
        }
    }

    private fun CustomBox.normalCard(creator: ScreenCreator, profile: Profile.Preview) = with(creator) {
        label("red wing", "Profile ${profile.name}", fontSize = (32 * 1.3).toInt()) { syncHeight() }
        if (profile.exists) {
            label("red wing", "current area: ${profile.currentArea}", fontSize = 32) { syncHeight() }
            label("red wing", "money: ${profile.money}", fontSize = 32) { syncHeight() }
            label("red wing", "cards in collection: ${profile.collection!!.size}", fontSize = 32) { syncHeight() }
            label("red wing", "run:", fontSize = 32) { syncHeight() }
            val runPreview = profile.runPreview
            if (runPreview != null) {
                label("red wing", "   health: ${runPreview.playerHealth}", fontSize = 32) { syncHeight() }
                label("red wing", "   type: ${runPreview.run.type.displayName}", fontSize = 32) { syncHeight() }
                label("red wing", "   difficulty: ${runPreview.run.difficulty}", fontSize = 32) { syncHeight() }
            } else {
                label("red wing", "   No run active", fontSize = 32) { syncHeight() }
            }
        } else {
            label("red wing", "no save yet", fontSize = 32) { syncHeight() }
        }
    }

}
