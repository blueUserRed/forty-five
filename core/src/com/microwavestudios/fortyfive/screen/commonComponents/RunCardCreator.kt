package com.microwavestudios.fortyfive.screen.commonComponents

import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator

object RunCardCreator {

    fun ScreenCreator.getSharedRunCard(
        run: Run
    ): CustomGroup = newBox {

        width = 250f
        height = 370f

        flexDirection = FlexDirection.COLUMN
        backgroundHandle = "white_texture"

        label("red wing", "difficulty: ${run.difficulty}", fontSize = 32)
        label("red wing", "biome: ${run.biome}", fontSize = 32)
        label("red wing", "type: ${run.type.displayName}", fontSize = 32)
        label("red wing", "length: ${run.length.displayName}", fontSize = 32)
        label("red wing", "rewards:", fontSize = 32)
        run.rewards.forEach { reward ->
            label("red wing", "    -${reward.displayText()}", fontSize = 32)
        }
    }

}
