package com.microwavestudios.fortyfive.map.events.specialevent

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl.Companion.logTag
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreen
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreenContext
import com.microwavestudios.fortyfive.screen.screens.LoseRunScreen
import com.microwavestudios.fortyfive.utils.Timeline
import kotlin.random.Random

data class SpecialEvent(
    val name: String,
    val title: String,
    val description: String,
    val options: List<Pair<String, List<SpecialEventAction>>>
)


typealias SpecialEventAction = (screen: RenderableScreen, rewardScreens: ScreenManager.ScreenChain) -> Timeline


object SpecialEventActions {

    fun damagePlayer(damage: Int): SpecialEventAction = { screen, _ ->
        Timeline.later {
            val pipeline = FortyFive.currentRenderPipeline
            requireNotNull(pipeline)

            val profile = FortyFive.profileManager.currentProfile
            requireNotNull(profile)

            val damageOverlay = GraphicsConfig.damageOverlay(screen)
            val damageOverlayTimeline = Timeline.timeline {
                action { damageOverlay.start() }
                delayUntil { damageOverlay.update(); damageOverlay.isFinished() }
                action { damageOverlay.end() }
            }

            parallelActions(
                pipeline.getScreenShakeTimeline().asAction(),
                damageOverlayTimeline.asAction()
            )
            val newHealth = profile.healthInRun!! - damage
            if (newHealth > 0) {
                profile.healthInRun = newHealth
            } else {
                profile.healthInRun = 0
                FortyFive.logger.debug("SpecialEvent", "player lost")
                include(FortyFive.currentRenderPipeline!!.getFadeToBlackTimeline(2000, stayBlack = true))
                delay(500)
                action {
                    profile.loseRun()
                    FortyFive.screenManager.ensureNextScreen(LoseRunScreen)
                    FortyFive.screenManager.overrideNextTransition(ScreenManager.ScreenTransition(null, null))
                    FortyFive.screenManager.screenFinished()
                }
            }
        }
    }

    fun getRandomCard(): SpecialEventAction = { _, rewardScreens ->
        val context = object : ChooseCardScreenContext {
            override var seed: Long = Random.nextLong() // TODO: get seed from somewhere
            override val nbrOfCards: Int = 3
            override val types: List<String> = listOf()
            override val enableRerolls: Boolean = false // TODO: enable rerolls?
            override var amountOfRerolls: Int = 0
            override val rerollPriceIncrease: Int = 0
            override val rerollBasePrice: Int = 0

            override fun completed() {
            }
        }

        Timeline.later {
            action {
                rewardScreens.append(ChooseCardScreen, context)
            }
        }
    }

}
