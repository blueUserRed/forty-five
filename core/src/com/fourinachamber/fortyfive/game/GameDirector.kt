package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.game.enemy.NextEnemyAction
import com.fourinachamber.fortyfive.map.detailMap.DetailMap
import com.fourinachamber.fortyfive.map.detailMap.EncounterMapEvent
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class GameDirector(private val controller: GameController) {

    private var difficulty = 0.0

    private lateinit var enemies: List<Enemy>

    lateinit var encounter: Encounter

    fun init() {
        val enemiesOnj = OnjParser.parseFile(Gdx.files.internal("config/enemies.onj").file()) // TODO: read in companion
        enemiesFileSchema.assertMatches(enemiesOnj)
        enemiesOnj as OnjObject

        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
        difficulty = SaveState.currentDifficulty
        val encounter = encounters[controller.encounterContext.encounterIndex]
        FortyFiveLogger.debug(logTag, "chose encounter $encounter")
        enemies = encounter
            .enemies
            .map { enemy -> enemyPrototypes.find { it.name == enemy } ?: throw RuntimeException("unknown enemy $enemy") }
            .map { it.create(it.baseHealth) }
        encounter
            .encounterModifiers
            .forEach { controller.addEncounterModifier(EncounterModifier.getFromName(it)) }
        this.encounter = encounter
        controller.addTutorialText(encounter.tutorialTextParts)
        controller.initEnemyArea(enemies)
    }

    fun chooseEnemyActions() {
        controller.activeEnemies.forEach { enemy ->
            val nextAction = enemy.brain.chooseNewAction(controller, enemy, difficulty, listOf())
            enemy.actor.setupForAction(NextEnemyAction.None) // make sure current action is cleared
            enemy.actor.setupForAction(nextAction)
        }
    }

    fun checkActions(): Timeline = Timeline.timeline {
        controller.activeEnemies.forEach { enemy ->
            val action = enemy.brain.resolveEnemyAction(controller, enemy, difficulty)
            action?.let {
                include(enemy.actor.enemyActionAnimationTimeline(it, controller))
                include(it.getTimeline())
            }
            action {
                enemy.actor.setupForAction(NextEnemyAction.None)
            }
        }
    }

    fun end() {
        val newDifficulty = adjustDifficulty()
        FortyFiveLogger.debug(logTag, "adjusted difficulty from $difficulty to $newDifficulty")
        SaveState.currentDifficulty = newDifficulty
    }

    private fun adjustDifficulty(): Double {
        return difficulty
    }

    data class Encounter(
        val enemies: List<String>,
        val encounterModifiers: Set<String>,
        val biomes: Set<String>,
        val progress: ClosedFloatingPointRange<Float>,
        val weight: Int,
        val forceCards: List<String>?,
        val shuffleCards: Boolean,
        val special: Boolean,
        val tutorialTextParts: List<GameTutorialTextPart>
    )

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
            val startNode = map.startNode
            val endNode = map.endNode
            val allNodes = map.uniqueNodes
            val progress = map.progress
            val roadDirection = Vector2(endNode.x, endNode.y) - Vector2(startNode.x, startNode.y)
            val difficultyVariance = (progress.endInclusive - progress.start) * 0.1f
            allNodes.forEach { node ->
                if (node === startNode || node === endNode) return@forEach
                if (node.event !is EncounterMapEvent) return@forEach
                val nodeDirection = Vector2(node.x, node.y) - Vector2(startNode.x, startNode.y)
                val distance = (nodeDirection dot roadDirection) / roadDirection.len()
                val normalDistance = distance / roadDirection.len()
                val difficulty = progress.start + (progress.endInclusive - progress.start) * normalDistance
                val difficultyRange = (difficulty - difficultyVariance)..(difficulty + difficultyVariance)
                val encounterIndex = chooseEncounter(map, difficultyRange)
                node.event.encounterIndex = encounterIndex
            }
        }

        private fun chooseEncounter(map: DetailMap, progress: ClosedFloatingPointRange<Float>): Int {
            val biome = map.biome
            val encounters = encounters.filter { !it.special }
            if (encounters.isEmpty()) throw RuntimeException("no encounters are defined")
            val encountersInBiome = encounters.filter { biome in it.biomes }
            if (encountersInBiome.isEmpty()) {
                FortyFiveLogger.warn(logTag, "No encounter found for biome $biome; choosing a random one")
                return encounters.randomIndex()
            }
            val encountersInRoad = encountersInBiome.filter { progress intersects it.progress }
            if (encountersInRoad.isEmpty()) {
                FortyFiveLogger.warn(logTag, "No encounter found for progress $progress; choosing a random one")
                return encountersInBiome.randomIndex()
            }
            val chosen = encountersInRoad
                .map { it.weight to it }
                .weightedRandom()

            return this.encounters.indexOf(chosen)
        }

    }
}
