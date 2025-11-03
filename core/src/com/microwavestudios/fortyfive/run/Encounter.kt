package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.canReturnToTwo
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.EncounterModifier
import com.microwavestudios.fortyfive.game.UserPrefs
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.game.enemy.EnemyPrototype
import com.microwavestudios.fortyfive.map.EncounterPlaceholderMapEvent
import com.microwavestudios.fortyfive.map.MapNodeBuilder
import com.microwavestudios.fortyfive.run.RunGenerator.Companion.logTag
import com.microwavestudios.fortyfive.utils.Utils
import com.microwavestudios.fortyfive.utils.unreachable
import com.microwavestudios.fortyfive.utils.zip
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.random.Random

data class Encounter(
    val enemies: List<String>,
    val encounterModifierNames: Set<String>,
    val forceCards: List<String>?,
    val shuffleCards: Boolean,
    val unadjustedMajorDifficulty: Int,
    val majorDifficulty: Int,
    val minorDifficulty: Float,
    val special: Boolean,
) {

    val encounterModifier: List<EncounterModifier> by lazy {
        encounterModifierNames
            .map { EncounterModifier.getFromName(it) }
            .filter { !UserPrefs.disableRtMechanics || !it.isRtBased }
    }

    fun createEnemies(): List<Enemy> {
        val enemiesOnj = ConfigFileManager.getConfigFile("enemies")
        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
        val healthMultiplier = 1f + (minorDifficulty * GameControllerImpl.Config.enemyHealthDifficultyAdjustment)
        return enemies
            .map { enemy -> enemyPrototypes.find { it.name == enemy } ?: throw RuntimeException("unknown enemy $enemy") }
            .map { it.create((it.baseHealth * healthMultiplier).toInt()) }
    }

    fun asOnj(): OnjObject = buildOnjObject {
        "enemies" with enemies
        "encounterModifier" with encounterModifierNames
        "forceCards" with forceCards
        "shuffleCards" with shuffleCards
        "unadjustedMajorDifficulty" with unadjustedMajorDifficulty
        "majorDifficulty" with majorDifficulty
        "minorDifficulty" with minorDifficulty
        "special" with special
    }

    companion object {

        fun fromOnj(onj: OnjObject): Encounter = Encounter(
            onj.get<OnjArray>("enemies").value.map { it.value as String },
            onj.get<OnjArray>("encounterModifier").value.map { it.value as String }.toSet(),
            onj.getOr<OnjArray?>("forceCards", null)?.value?.map { it.value as String },
            onj.getOr("shuffleCards", true),
            onj.get<Long>("unadjustedMajorDifficulty").toInt(),
            onj.get<Long>("majorDifficulty").toInt(),
            onj.get<Double>("minorDifficulty").toFloat(),
            onj.getOr("special", false),
        )
    }

}

object EncounterGenerator {

    fun generate(placeholder: EncounterPlaceholderMapEvent, node: MapNodeBuilder): Encounter {

        val random = Random(placeholder.seed)

        var majorDifficulty = placeholder.majorDifficulty
        var minorDifficulty = placeholder.minorDifficulty

        var baseModifiers = placeholder.runModifier.mapNotNull { it.addModifier() }
        if (!EncounterModifier.isValid(baseModifiers)) {
            FortyFive.logger.warn("EncounterGen", "Run modifiers generated invalid list of encounter modifiers: ${placeholder.runModifier}")
            baseModifiers = listOf()
        }

        val modifiers = generateEncounterModifier(random, baseModifiers, placeholder)

        val difficultyAdjustment = -modifiers
            .map { EncounterModifier.getFromName(it) }
            .sumOf { it.difficultyChange.toDouble() }

        majorDifficulty = (majorDifficulty + difficultyAdjustment.toInt()).coerceAtLeast(0)
        minorDifficulty = (minorDifficulty + (difficultyAdjustment % 1)).toFloat()

        val (enemies, enemyDifficulty) = generateEnemies(random, majorDifficulty, placeholder)
        val difficultyDiff = majorDifficulty - enemyDifficulty
        minorDifficulty += difficultyDiff

        return Encounter(
            enemies,
            modifiers.toSet(),
            null,
            true,
            placeholder.unadjustedMajorDifficulty,
            majorDifficulty,
            minorDifficulty,
            placeholder.genExtraction
        )
    }

    private fun generateEnemies(
        random: Random,
        majorDifficulty: Int, // adjusted
        placeholder: EncounterPlaceholderMapEvent
    ): Pair<List<String>, Int> {
        val enemyConfig = RunGeneratorConfig.enemyConfig
        lateinit var config: EnemyConfig
        var difficultyToCheck = placeholder.unadjustedMajorDifficulty
        while (true) {
            if (difficultyToCheck < 0) {
                throw RuntimeException("no enemy config for difficulty: ${placeholder.unadjustedMajorDifficulty}")
            }
            val toCheck = enemyConfig.find { it.majorDifficulty == difficultyToCheck }
            if (toCheck != null) {
                config = toCheck
                break
            }
            difficultyToCheck--
        }

        val availableEnemies = mutableListOf<Triple<Int, String, String>>() // maj. Diff., name, group name
        config.allowedEnemies.forEach { group ->
            val enemies = RunGeneratorConfig.enemyGroups[group]
            requireNotNull(enemies) { "unknown enemy group: $group" }
            enemies.forEach { (difficulty, name) -> availableEnemies.add(Triple(difficulty, name, group)) }
        }
        availableEnemies.sortBy { it.first }

        var candidates = when (placeholder.amountEnemies) {
            1 -> generateEnemySingle(majorDifficulty, availableEnemies)
            2 -> generateEnemyPairs(majorDifficulty, availableEnemies)
            3 -> generateEnemyTriples(majorDifficulty, availableEnemies)
            else -> unreachable()
        }
        if (candidates.isEmpty()) {
            FortyFive.logger.warn("EncounterGenerator", "Cant generate encounter with preferred number of enemies " +
                    "number enemies: ${placeholder.amountEnemies}, " +
                    "available enemies: $availableEnemies")
        }
        if (candidates.isEmpty()) candidates = generateEnemySingle(majorDifficulty, availableEnemies)
        if (candidates.isEmpty()) candidates = generateEnemyPairs(majorDifficulty, availableEnemies)
        if (candidates.isEmpty()) candidates = generateEnemyTriples(majorDifficulty, availableEnemies)

        if (candidates.isEmpty()) {
            val maxDifficulty = availableEnemies.maxOf { it.first }
            if (majorDifficulty > maxDifficulty * 3) {
                // difficulty is too high
                FortyFive.logger.debug("EncounterGenerator", "Difficulty $majorDifficulty has no hard enough enemies")
                val difficultEnemies = availableEnemies.filter { it.first >= maxDifficulty }
                val enemies = listOf(
                    difficultEnemies.random(random),
                    difficultEnemies.random(random),
                    difficultEnemies.random(random),
                )
                return enemies.map { it.second } to enemies.sumOf { it.first }
            }

            FortyFive.logger.warn(
                "EncounterGenerator",
                "Couldn't generate enemies for encounter " +
                        "difficulty: $majorDifficulty, " +
                        "available enemies: $availableEnemies, " +
                        "amountEnemies: ${placeholder.amountEnemies}"
            )

            // shouldn't really happen, always return expected difficulty to prevent weird scaling
            return listOf(availableEnemies.first().second) to majorDifficulty
        }

        val winner = candidates
            .zip { scoreEnemyConfiguration(random, it, placeholder) }
            .maxBy { it.second }
            .first

        return winner.map { it.second } to winner.sumOf { it.first }
    }

    private fun scoreEnemyConfiguration(
        random: Random,
        enemies: Array<Triple<Int, String, String>>,
        placeholder: EncounterPlaceholderMapEvent
    ): Int {
        val distinctEnemies = enemies.map { it.third }.distinct().size

        val distinctEnemiesPoints = when (enemies.size) {
            1 -> 0
            2 -> if (distinctEnemies == 2) 100 else 0
            3 -> when (distinctEnemies) {
                3 -> 100
                2 -> 50
                else -> 0
            }
            else -> unreachable()
        }

        val distinctDifficulties = enemies.map { it.first }.distinct().size

        val distinctDifficultiesPoints = when (enemies.size) {
            1 -> 0
            2 -> if (distinctDifficulties == 2) 40 else 0
            3 -> when (distinctDifficulties) {
                3 -> 40
                2 -> 20
                else -> 0
            }
            else -> unreachable()
        }

        val probabilityIncreases = RunGeneratorConfig.enemyProbabilityIncrease
        val enemyGroups = enemies.map { it.third }
        var bonusPoints = 0
        probabilityIncreases
            .forEach {
                if (it.biome != placeholder.biome || it.enemy !in enemyGroups) return@forEach
                bonusPoints += it.bonusPoints
            }

        val randomPoints = (0..RunGeneratorConfig.enemyProbabilityRandomPoints).random(random)

        return distinctEnemiesPoints + distinctDifficultiesPoints + bonusPoints + randomPoints
    }

    private fun generateEnemySingle(
        majorDifficulty: Int,
        availableEnemies: List<Triple<Int, String, String>>
    ): List<Array<Triple<Int, String, String>>> {
        return availableEnemies.filter { it.first == majorDifficulty }.map { arrayOf(it) }
    }

    private fun generateEnemyPairs(
        majorDifficulty: Int,
        availableEnemies: List<Triple<Int, String, String>>
    ): List<Array<Triple<Int, String, String>>> {
        val candidates = mutableListOf<Array<Triple<Int, String, String>>>()
        var i = 0
        while (i < availableEnemies.size) {
            val enemy = availableEnemies[i]
            val difficulty = enemy.first
            if (difficulty > majorDifficulty) break
            var j = i
            while (j < availableEnemies.size) {
                val enemy2 = availableEnemies[j]
                if (difficulty + enemy2.first == majorDifficulty) {
                    candidates.add(arrayOf(enemy, enemy2))
                }
                j++
            }
            i++
        }
        return candidates
    }

    private fun generateEnemyTriples(
        majorDifficulty: Int,
        availableEnemies: List<Triple<Int, String, String>>
    ): List<Array<Triple<Int, String, String>>> {
        val candidates = mutableListOf<Array<Triple<Int, String, String>>>()
        var i = 0
        while (i < availableEnemies.size) {
            val enemy = availableEnemies[i]
            val difficulty = enemy.first
            if (difficulty > majorDifficulty) break
            var j = i
            while (j < availableEnemies.size) {
                val enemy2 = availableEnemies[j]
                val difficulty2 = enemy2.first
                if (difficulty + difficulty2 > majorDifficulty) break
                var k = j
                while (k < availableEnemies.size) {
                    val enemy3 = availableEnemies[k]
                    if (difficulty + difficulty2 + enemy3.first == majorDifficulty) {
                        candidates.add(arrayOf(enemy, enemy2, enemy3))
                    }
                    k++
                }
                j++
            }
            i++
        }
        return candidates
    }

    private fun generateEncounterModifier(
        random: Random,
        baseModifiers: List<String>,
        placeholder: EncounterPlaceholderMapEvent
    ): List<String> {
        val pools = RunGeneratorConfig.encounterModifierPools
        var checkDifficulty = placeholder.unadjustedMajorDifficulty
        lateinit var pool: EncounterModifierPool
        while (true) {
            if (checkDifficulty < 0) {
                throw RuntimeException("no encounter modifier pool for difficulty: ${placeholder.unadjustedMajorDifficulty}")
            }
            val toCheck = pools.find { it.majorDifficulty == checkDifficulty }
            if (toCheck != null) {
                pool = toCheck
                break
            }
            checkDifficulty--
        }

        val selectedModifiers = baseModifiers.toMutableList()
        repeat((pool.maxModifiers - baseModifiers.size).coerceAtLeast(0)) {
            if (!Utils.coinFlip(pool.modifierProbability, random)) return@repeat

            val modifiers = pool.modifiers
            val start = modifiers.indices.random(random)
            var current = start

            while (true) {
                val modifier = modifiers[current]

                if (modifier !in selectedModifiers && selectedModifiers.none { EncounterModifier.isBlacklisted(modifier, it) }) {
                    selectedModifiers.add(modifier)
                    break
                }

                current++
                current %= modifiers.size
                if (current == start) {
                    FortyFive.logger.warn(
                        logTag,
                        "cant find non-blacklisted encounter modifier option.\n" +
                                "selectedModifiers = $selectedModifiers, modifiers = $modifiers"
                    )
                    break
                }
            }
        }
        return selectedModifiers
    }

}
