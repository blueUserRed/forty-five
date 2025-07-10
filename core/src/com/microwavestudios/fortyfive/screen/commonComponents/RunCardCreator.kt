package com.microwavestudios.fortyfive.screen.commonComponents

import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.EventPipeline

object RunCardCreator {

    fun ScreenCreator.getSharedRunCard(
        run: Run
    ): CustomGroup = newBox {

        width = 250f
        height = 400f

        flexDirection = FlexDirection.COLUMN
        backgroundHandle = "white_texture"

        label("red_wing", "difficulty: ${run.difficulty}")
        label("red_wing", "biome: ${run.biome}")
        label("red_wing", "type: ${run.type.displayName}")
        label("red_wing", "length: ${run.length.displayName}")
        label("red_wing", "rewards:")
        run.rewards.forEach { reward ->
            label("red_wing", "    -${reward.displayText()}")
        }
    }

}
