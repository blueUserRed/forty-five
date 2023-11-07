package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.game.enemy.EnemyPrototype
import com.fourinachamber.fortyfive.game.enemy.NextEnemyAction
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

class GameDirector(private val controller: GameController) {

    private var difficulty = 0.0

    private lateinit var enemies: List<Enemy>

    fun init() {
        val enemiesOnj = OnjParser.parseFile(Gdx.files.internal("config/enemies.onj").file())
        enemiesFileSchema.assertMatches(enemiesOnj)
        enemiesOnj as OnjObject

        difficulty = SaveState.currentDifficulty
        FortyFiveLogger.debug(logTag, "difficulty = $difficulty")
        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
        val chosenProtos = chooseEnemies(enemyPrototypes)
        enemies = chosenProtos.map { it.create(it.baseHealth) }
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

    private fun adjustDifficulty(): Double {
        return difficulty
    }

    private data class Encounter(
        val enemies: List<String>,
        val encounterModifier: Set<String>,
        val biomes: Set<String>,
        val progress: ClosedFloatingPointRange<Float>,
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
                    obj.get<OnjArray>("progress").toFloatRange()
                ) }
        }

    }
}
