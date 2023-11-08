package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.game.enemy.EnemyPrototype
import com.fourinachamber.fortyfive.game.enemy.NextEnemyAction
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.math.log

class GameDirector(private val controller: GameController) {

    private var difficulty = 0.0

    private lateinit var enemies: List<Enemy>

    fun init() {
        val enemiesOnj = OnjParser.parseFile(Gdx.files.internal("config/enemies.onj").file()) // TODO: read in companion
        enemiesFileSchema.assertMatches(enemiesOnj)
        enemiesOnj as OnjObject

        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
        difficulty = SaveState.currentDifficulty
        val encounter = chooseEncounter()
        FortyFiveLogger.debug(logTag, "chose encounter $encounter")
        enemies = encounter
            .enemies
            .map { enemy -> enemyPrototypes.find { it.name == enemy } ?: throw RuntimeException("unknown enemy $enemy") }
            .map { it.create(it.baseHealth) }
        encounter
            .encounterModifier
            .forEach { controller.addEncounterModifier(EncounterModifier.getFromName(it)) }
        controller.initEnemyArea(enemies)
    }

    fun onNewTurn() {
        enemies.forEach { enemy ->
            val nextAction = enemy.brain.chooseNewAction(controller, enemy, difficulty)
            enemy.actor.setupForAction(NextEnemyAction.None) // make sure current action is cleared
            enemy.actor.setupForAction(nextAction)
        }
    }

    fun checkActions(): Timeline = Timeline.timeline {
        enemies.forEach { enemy ->
            action {
                enemy.actor.setupForAction(NextEnemyAction.None)
            }
            enemy.brain.resolveEnemyAction(controller, enemy, difficulty)?.getTimeline()?.let { include(it) }
        }
    }

    fun end() {
        val newDifficulty = adjustDifficulty()
        FortyFiveLogger.debug(logTag, "adjusted difficulty from $difficulty to $newDifficulty")
        SaveState.currentDifficulty = newDifficulty
    }

    private fun chooseEncounter(): Encounter {
        val road = MapManager.currentDetailMap
        val progress = road.progress
        val biome = road.biome
        if (encounters.isEmpty()) throw RuntimeException("no encounters are defined")
        val encountersInBiome = encounters.filter { biome in it.biomes }
        if (encountersInBiome.isEmpty()) {
            FortyFiveLogger.warn(logTag, "No encounter found for biome $biome; choosing a random one")
            return encounters.random()
        }
        val encountersInRoad = encountersInBiome.filter { progress intersects it.progress }
        if (encountersInRoad.isEmpty()) {
            FortyFiveLogger.warn(logTag, "No encounter found for progress $progress; choosing a random one")
            return encountersInBiome.random()
        }
        return encountersInRoad
            .map { it.weight to it }
            .weightedRandom()
    }

    private fun adjustDifficulty(): Double {
        return difficulty
    }

    private data class Encounter(
        val enemies: List<String>,
        val encounterModifier: Set<String>,
        val biomes: Set<String>,
        val progress: ClosedFloatingPointRange<Float>,
        val weight: Int
    )

    companion object {

        const val logTag = "director"

        private lateinit var encounters: List<Encounter>

        private val enemiesFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/enemies.onjschema").file())
        }

        private val encounterDefinitionsSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/encounter_definitions.onjschema").file())
        }

        fun init() {
            val onj = OnjParser.parseFile(Gdx.files.internal("config/encounter_definitions.onj").file())
            encounterDefinitionsSchema.assertMatches(onj)
            onj as OnjObject
            encounters = onj
                .get<OnjArray>("encounter")
                .value
                .map { it as OnjObject }
                .map { obj -> Encounter(
                    obj.get<OnjArray>("enemies").value.map { it.value as String },
                    obj.get<OnjArray>("encounterModifier").value.map { it.value as String }.toSet(),
                    obj.get<OnjArray>("biomes").value.map { it.value as String }.toSet(),
                    obj.get<OnjArray>("progress").toFloatRange(),
                    obj.get<Long>("weight").toInt()
                ) }
        }

    }
}
