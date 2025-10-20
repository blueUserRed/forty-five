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

//    lateinit var encounter: Encounter
//
//    fun init() {
//        val enemiesOnj = ConfigFileManager.getConfigFile("enemies")
//
//        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
//        difficulty = SaveState.currentDifficulty
//        val encounter = encounters[controller.encounterContext.encounterIndex]
//        FortyFive.logger.debug(logTag, "chose encounter $encounter")
//        enemies = encounter
//            .enemies
//            .map { enemy -> enemyPrototypes.find { it.name == enemy } ?: throw RuntimeException("unknown enemy $enemy") }
//            .map { it.create(it.baseHealth) }
//        encounter
//            .encounterModifier
//            .forEach { controller.addEncounterModifier(it) }
//        this.encounter = encounter
//        controller.addTutorialText(encounter.tutorialTextParts)
//        controller.initEnemyArea(enemies)
//    }

    companion object {

        const val logTag = "director"

//        lateinit var encounters: List<Encounter>
//            private set

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

//        private fun chooseEncounter(map: DetailMap, progress: ClosedFloatingPointRange<Float>): Int {
//            val biome = map.biome
//            val encounters = encounters.filter { !it.special }
//            if (encounters.isEmpty()) throw RuntimeException("no encounters are defined")
//            val encountersInBiome = encounters.filter { biome in it.biomes }
//            if (encountersInBiome.isEmpty()) {
//                FortyFive.logger.warn(logTag, "No encounter found for biome $biome; choosing a random one")
//                return encounters.randomIndex()
//            }
//            val encountersInRoad = encountersInBiome.filter { progress intersection it.progress }
//            if (encountersInRoad.isEmpty()) {
//                FortyFive.logger.warn(logTag, "No encounter found for progress $progress; choosing a random one")
//                return encountersInBiome.randomIndex()
//            }
//            val chosen = encountersInRoad
//                .map { it.weight to it }
//                .weightedRandom()
//
//            return this.encounters.indexOf(chosen)
//        }

    }
}
