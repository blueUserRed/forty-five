package com.microwavestudios.fortyfive.map.events.specialevent

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.onjNamespaces.OnjSpecialEventAction
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreen
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreenContext
import com.microwavestudios.fortyfive.screen.screens.LoseRunScreen
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.weightedRandom
import com.microwavestudios.fortyfive.utils.zipToFirst
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.random.Random

data class SpecialEvent(
    val name: String,
    val title: String,
    val description: String,
    val allowedInBiomes: List<String>,
    val weight: Int,
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
            require(profile.isRunActive)

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
            override val nbrOfCards: Int = 1
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

    fun healPlayer(amount: Int): SpecialEventAction = { _, _ ->
        Timeline.later { // TODO: animation
            val profile = FortyFive.profileManager.currentProfile
            requireNotNull(profile)
            require(profile.isRunActive)

            profile.healthInRun = profile.healthInRun!! + amount
        }
    }

    fun healOrDamagePlayer(amount: Int): SpecialEventAction = { screen, rewardScreens ->
        Timeline.timeline {
            val action = if (Random.nextBoolean()) {
                damagePlayer(amount)(screen, rewardScreens)
            } else {
                healPlayer(amount)(screen, rewardScreens)
            }
            include(action)
        }
    }

}

object SpecialEventFactory {

    private var specialEvents: MutableMap<String, SpecialEvent> = mutableMapOf()

    fun getRandomSpecialEvent(): SpecialEvent {
        val profile = FortyFive.profileManager.currentProfile
        requireNotNull(profile) { "cant call getRandomSpecialEvent() without active profile" }
        val biome = profile.currentMapSaver.currentMap.biome
        val allowed = specialEvents
            .values
            .filter { biome in it.allowedInBiomes }
        require(allowed.isNotEmpty()) { "No special event for biome: $biome" }
        val winner = allowed
            .zipToFirst { it.weight }
            .weightedRandom(Random)
        return winner
    }

    fun init() {
        val configFile = ConfigFileManager.getConfigFile("specialEvents")
        val events = configFile
            .get<OnjArray>("specialEvents")
            .value
            .map { event ->
                event as OnjObject
                val name = event.get<String>("name")
                val title = event.get<String>("title")
                val description = event.get<String>("description")
                val options = event
                    .get<OnjArray>("options")
                    .value
                    .map { option ->
                        option as OnjObject
                        val text = option.get<String>("text")
                        val options = option.get<OnjArray>("actions")
                            .value
                            .map { (it as OnjSpecialEventAction).value }
                        text to options
                    }
                val biomes = event.get<OnjArray>("allowedInBiomes").value.map { it.value as String }
                val weight = event.get<Long>("weight").toInt()
                SpecialEvent(name, title, description, biomes, weight, options)
            }
        specialEvents = events.associateByTo(mutableMapOf()) { it.name }
    }

}
