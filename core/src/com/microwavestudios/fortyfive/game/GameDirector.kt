package com.microwavestudios.fortyfive.game

import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.map.EncounterMapEvent
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class GameDirector(private val controller: GameController) {

    private var difficulty = 0.0

    private lateinit var enemies: List<Enemy>

    lateinit var encounter: Encounter

    fun init() {
        val enemiesOnj = ConfigFileManager.getConfigFile("enemies")

        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
        difficulty = SaveState.currentDifficulty
        val encounter = encounters[controller.encounterContext.encounterIndex]
        FortyFive.logger.debug(logTag, "chose encounter $encounter")
        enemies = encounter
            .enemies
            .map { enemy -> enemyPrototypes.find { it.name == enemy } ?: throw RuntimeException("unknown enemy $enemy") }
            .map { it.create(it.baseHealth) }
        encounter
            .encounterModifier
            .forEach { controller.addEncounterModifier(it) }
        this.encounter = encounter
        controller.addTutorialText(encounter.tutorialTextParts)
        controller.initEnemyArea(enemies)
    }

    data class Encounter(
        val enemies: List<String>,
        val encounterModifierNames: Set<String>,
        val biomes: Set<String>,
        val progress: ClosedFloatingPointRange<Float>,
        val weight: Int,
        val forceCards: List<String>?,
        val shuffleCards: Boolean,
        val special: Boolean,
        val tutorialTextParts: List<GameTutorialTextPart>
    ) {

        val encounterModifier: List<EncounterModifier>
            get() = encounterModifierNames
                .map { EncounterModifier.getFromName(it) }
                .filter { !UserPrefs.disableRtMechanics || !it.isRtBased }

        val createdEnemies: List<Enemy> by lazy {
            val enemiesOnj = ConfigFileManager.getConfigFile("enemies")
            val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
            enemies
                .map { enemy -> enemyPrototypes.find { it.name == enemy } ?: throw RuntimeException("unknown enemy $enemy") }
                .map { it.create(it.baseHealth) }
        }
    }

    data class GameTutorialTextPart(
        val text: String,
        val confirmationText: String,
        val focusActorName: String?,
        val predicate: GamePredicate?
    ) {

        companion object {

            fun fromOnj(onj: OnjObject): GameTutorialTextPart = GameTutorialTextPart(
                onj.get<String>("text"),
                onj.get<String>("confirmationText"),
                onj.getOr<String?>("focusActor", null),
                onj.getOr<OnjNamedObject?>("predicate", null)?.let { GamePredicate.fromOnj(it) }
            )
        }
    }

    companion object {

        const val logTag = "director"

        lateinit var encounters: List<Encounter>
            private set

        fun init() {
            val onj = ConfigFileManager.getConfigFile("encounterDefinitions")
            encounters = onj
                .get<OnjArray>("encounter")
                .value
                .map { it as OnjObject }
                .map { obj -> Encounter(
                    obj.get<OnjArray>("enemies").value.map { it.value as String },
                    obj.get<OnjArray>("encounterModifier").value.map { it.value as String }.toSet(),
                    obj.get<OnjArray>("biomes").value.map { it.value as String }.toSet(),
                    obj.get<OnjArray>("progress").toFloatRange(),
                    obj.get<Long>("weight").toInt(),
                    obj.getOr<OnjArray?>("forceCards", null)?.value?.map { it.value as String },
                    obj.getOr("shuffleCards", true),
                    obj.getOr("special", false),
                    obj.getOr<OnjArray?>("tutorialText", null)
                        ?.value
                        ?.map { GameTutorialTextPart.fromOnj(it as OnjObject) }
                        ?: listOf()
                ) }
        }

        fun assignEncounters(map: DetailMap) {
//            val startNode = map.startNode
//            val endNode = map.endNode
//            val allNodes = map.uniqueNodes
////            val progress = map.progress
//            val roadDirection = Vector2(endNode.x, endNode.y) - Vector2(startNode.x, startNode.y)
//            val difficultyVariance = 0f
//            allNodes.forEach { node ->
//                if (node === startNode || node === endNode) return@forEach
//                if (node.event !is EncounterMapEvent) return@forEach
//                val nodeDirection = Vector2(node.x, node.y) - Vector2(startNode.x, startNode.y)
//                val distance = (nodeDirection dot roadDirection) / roadDirection.len()
//                val normalDistance = distance / roadDirection.len()
//                val difficulty = progress.start + (progress.endInclusive - progress.start) * normalDistance
//                val difficultyRange = (difficulty - difficultyVariance)..(difficulty + difficultyVariance)
//                val encounterIndex = chooseEncounter(map, difficultyRange)
//                node.event.encounterIndex = encounterIndex
//            }
        }

        private fun chooseEncounter(map: DetailMap, progress: ClosedFloatingPointRange<Float>): Int {
            val biome = map.biome
            val encounters = encounters.filter { !it.special }
            if (encounters.isEmpty()) throw RuntimeException("no encounters are defined")
            val encountersInBiome = encounters.filter { biome in it.biomes }
            if (encountersInBiome.isEmpty()) {
                FortyFive.logger.warn(logTag, "No encounter found for biome $biome; choosing a random one")
                return encounters.randomIndex()
            }
            val encountersInRoad = encountersInBiome.filter { progress intersection it.progress }
            if (encountersInRoad.isEmpty()) {
                FortyFive.logger.warn(logTag, "No encounter found for progress $progress; choosing a random one")
                return encountersInBiome.randomIndex()
            }
            val chosen = encountersInRoad
                .map { it.weight to it }
                .weightedRandom()

            return this.encounters.indexOf(chosen)
        }

    }
}
