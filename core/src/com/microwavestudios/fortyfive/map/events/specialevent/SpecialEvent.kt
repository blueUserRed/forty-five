package com.microwavestudios.fortyfive.map.events.specialevent

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.game.Talisman
import com.microwavestudios.fortyfive.game.TalismanFactory
import com.microwavestudios.fortyfive.game.controller.EncounterContext
import com.microwavestudios.fortyfive.map.MapNode
import com.microwavestudios.fortyfive.onjNamespaces.OnjSpecialEventAction
import com.microwavestudios.fortyfive.run.Encounter
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreen
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreenContext
import com.microwavestudios.fortyfive.screen.screens.EncounterScreen
import com.microwavestudios.fortyfive.screen.screens.GetTalismanScreen
import com.microwavestudios.fortyfive.screen.screens.GetTalismanScreenContext
import com.microwavestudios.fortyfive.screen.screens.LoseRunScreen
import com.microwavestudios.fortyfive.screen.screens.MapScreenContext
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
    val conditions: List<SpecialEventCondition>,
    val weight: Int,
    val options: List<Pair<String, List<SpecialEventAction>>>
)


typealias SpecialEventAction = (SpecialEventActionData) -> Timeline

data class SpecialEventActionData(
    val screen: RenderableScreen,
    val nextScreens: ScreenManager.ScreenChain,
    val mapScreenContextTransformers: MutableList<(MapScreenContext) -> MapScreenContext>
)


object SpecialEventActions {

    fun damagePlayer(damage: Int): SpecialEventAction = { (screen, _) ->
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

    fun useSteps(amount: Int): SpecialEventAction = {
        Timeline.later {
            val profile = FortyFive.profileManager.currentProfile
            requireNotNull(profile)
            require(profile.isRunActive)
            repeat(amount) { profile.stepTaken() }
        }
    }

    fun getRandomCard(): SpecialEventAction = { (_, nextScreens) ->
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
                nextScreens.append(ChooseCardScreen, context)
            }
        }
    }

    fun healPlayer(amount: Int): SpecialEventAction = {
        Timeline.later { // TODO: animation
            val profile = FortyFive.profileManager.currentProfile
            requireNotNull(profile)
            require(profile.isRunActive)

            profile.healthInRun = profile.healthInRun!! + amount
        }
    }

    fun healOrDamagePlayer(amount: Int): SpecialEventAction = { data ->
        Timeline.timeline {
            val action = if (Random.nextBoolean()) {
                damagePlayer(amount)(data)
            } else {
                healPlayer(amount)(data)
            }
            include(action)
        }
    }

    fun boostTalismanRewardChance(increase: Double): SpecialEventAction = { _ ->
        require(increase > 0.0) { "increase must be positive" }
        Timeline.later {
            val profile = FortyFive.profileManager.currentProfile!!
            profile.boostTalismanRewardChance(increase)
        }
    }

    fun getTalisman(talisman: String): SpecialEventAction = { data ->
        val context = object : GetTalismanScreenContext {

            override val talisman: Talisman = TalismanFactory.getTalisman(talisman)

            override fun completed() {}
        }

        Timeline.later {
            data.nextScreens.append(GetTalismanScreen, context)
        }
    }

    fun fightForTalisman(talisman: String, enemyGroups: List<String>): SpecialEventAction = { data ->
        val profile = FortyFive.profileManager.currentProfile
        requireNotNull(profile)
        val majorDiff = profile.currentMapSaver.currentMap.majorDifficulty
        val encounter = Encounter(
            enemiesGroups = enemyGroups,
            encounterModifierNames = setOf(),
            forceCards = null,
            forceConcreteEnemies = null,
            shuffleCards = true,
            unadjustedMajorDifficulty = majorDiff,
            majorDifficulty = majorDiff,
            minorDifficulty = 1f,
            isHard = false,
            difficultyScalingInfo = 0f,
            special = true,
        )
        val encounterContext = object : EncounterContext {
            override val encounter: Encounter = encounter
            override val isExtraction: Boolean = false
            override val forceTalisman: Talisman = TalismanFactory.getTalisman(talisman)
            override fun completed() {}
        }
        Timeline.later {
            data.nextScreens.append(EncounterScreen, encounterContext)
        }
    }

    fun putPlayerOnNode(node: MapNode): SpecialEventAction = { (_, _, contextTransformers) ->
        Timeline.later {
            contextTransformers.add { _ -> object : MapScreenContext {

                override val teleportPlayerTo: MapNode = node
            } }
        }
    }

    fun putPlayerOnRandomNode(): SpecialEventAction = { data ->
        Timeline.later {
            val profile = FortyFive.profileManager.currentProfile
            requireNotNull(profile)
            require(profile.isRunActive)
            val map = profile.currentMapSaver.currentMap
            val node = map
                .uniqueNodes
                .filter { profile.currentMapSaver.currentNode != it }
                .random()
            include(putPlayerOnNode(node)(data))
        }
    }

    fun putPlayerOnNodeWithDistance(targetDistance: Int): SpecialEventAction = { data ->
        Timeline.later {
            val profile = FortyFive.profileManager.currentProfile
            requireNotNull(profile)
            require(profile.isRunActive)
            val currentNode = profile.currentMapSaver.currentNode
            val node = nodeWithDistance(currentNode, targetDistance, Random)
            include(putPlayerOnNode(node)(data))
        }
    }

    private fun nodeWithDistance(currentNode: MapNode, targetDistance: Int, random: Random): MapNode {
        var distance = targetDistance
        var nextNodes = listOf(currentNode)
        val visitedNodes = mutableSetOf<Int>()
        visitedNodes.add(currentNode.index)
        while (distance > 0) {
            distance--
            val newNodes = nextNodes
                .flatMap { it.edgesTo }
                .filter { it.index !in visitedNodes }
            if (newNodes.isEmpty()) {
                FortyFive.logger.warn("SpecialEventAction", "couldn't find node $targetDistance nodes away")
                return nextNodes.random(random)
            }
            newNodes.forEach { visitedNodes.add(it.index) }
            nextNodes = newNodes
        }
        return nextNodes.random(random)
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
            .filter { event -> event.conditions.all { it() } }
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
                val weight = event.get<Long>("weight").toInt()
                val conditions = event.get<OnjArray>("conditions").value.map {
                    @Suppress("UNCHECKED_CAST")
                    it.value as SpecialEventCondition
                }
                SpecialEvent(name, title, description, conditions, weight, options)
            }
        specialEvents = events.associateByTo(mutableMapOf()) { it.name }
    }

}

typealias SpecialEventCondition = () -> Boolean

object SpecialEventConditions {

    fun inBiome(biome: String): SpecialEventCondition = {
        val profile = FortyFive.profileManager.currentProfile!!
        profile.currentMapSaver.currentMap.biome == biome
    }

    fun playerHasTalisman(talisman: String): SpecialEventCondition = {
        val profile = FortyFive.profileManager.currentProfile!!
        talisman in profile.talismans.map { it.name }
    }

    fun not(specialEventCondition: SpecialEventCondition): SpecialEventCondition = {
        !specialEventCondition()
    }

}
