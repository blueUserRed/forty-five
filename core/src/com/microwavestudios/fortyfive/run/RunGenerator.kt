package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.map.ChooseCardMapEvent
import com.microwavestudios.fortyfive.map.EmptyMapEvent
import com.microwavestudios.fortyfive.map.EncounterPlaceholderMapEvent
import com.microwavestudios.fortyfive.map.ShopMapEvent
import com.microwavestudios.fortyfive.map.generation.BaseMapGenerator
import com.microwavestudios.fortyfive.map.generation.ThreeLineMapGenerator
import com.microwavestudios.fortyfive.utils.Utils
import com.microwavestudios.fortyfive.utils.between
import com.microwavestudios.fortyfive.utils.random
import com.microwavestudios.fortyfive.utils.requireNot
import com.microwavestudios.fortyfive.utils.toIntRange
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.math.absoluteValue
import kotlin.math.log
import kotlin.random.Random

class RunGenerator {

    private val random: Random = Random

    fun generateRun(forDifficulty: Int, forBiome: String, forArea: String, type: RunType): Run {

        val modifiers = generateRunModifiers(forBiome, forDifficulty)

        val difficultyAdjustment = -modifiers.sumOf { it.difficultyAdjustment.toDouble() }
        val majorDifficulty = (forDifficulty + difficultyAdjustment.toInt()).coerceAtLeast(0)
        val minorDifficulty = 1f + (difficultyAdjustment % 1).toFloat()

        val rewards = generateRunRewards(forDifficulty)

        val mapGenerator = threeLineMapGen(
            majorDifficulty,
            forDifficulty,
            minorDifficulty,
            enemyAmountRange(forDifficulty),
            modifiers,
            forBiome
        )

        return Run(
            "-generated-",
            RunLength.MEDIUM,
            type,
            forDifficulty,
            modifiers,
            rewards,
            forBiome,
            forArea,
            100,
            100,
            mapGenerator
        )
    }

    private fun threeLineMapGen(
        majorDifficulty: Int,
        unadjustedMajorDifficulty: Int,
        minorDifficulty: Float,
        enemyAmountRange: IntRange,
        runModifier: List<RunModifier>,
        biome: String
    ): BaseMapGenerator = ThreeLineMapGenerator.ThreeLineMapGeneratorData(
        majorDifficulty = 1,
        biome = "wasteland",
        nodeProtectedArea = 20f,
        altLinesOffset = (40f..60f).random(random),
        mainLineNodes = 8,
        altLinesPadding = 0..2,
        varianceX = 15f,
        varianceY = 15f,
        roadLength = 270f,
        horizontalExtension = 80f,
        verticalExtension = 50f,
        locationSignProtectedAreaWidth = 25f,
        locationSignProtectedAreaHeight = 30f,
        firstNodeTexture = "map_node_default",
        firstNodeEvent = { EmptyMapEvent() },
        lastNodeTexture = "map_node_fight",
        lastNodeEvent = {
            EncounterPlaceholderMapEvent(
                true,
                majorDifficulty,
                unadjustedMajorDifficulty,
                minorDifficulty,
                runModifier,
                enemyAmountRange.random(random),
                biome,
                random.nextLong()
            )
        },
        mainEvent = ThreeLineMapGenerator.ThreeLineMapGeneratorEventSpawner(
            {
                EncounterPlaceholderMapEvent(
                    false,
                    majorDifficulty,
                    unadjustedMajorDifficulty,
                    minorDifficulty,
                    runModifier,
                    enemyAmountRange.random(random),
                    biome,
                    random.nextLong()
                )
            },
            offset = 0..1,
            nodeTexture = "map_node_fight",
            line = -1,
        ),
        events = listOf(
            ThreeLineMapGenerator.ThreeLineMapGeneratorEventSpawner(
                {
                    ChooseCardMapEvent(
                        listOf(),
                        true,
                        0,
                        20,
                        10,
                        random.nextLong(),
                        3
                    )
                },
                offset = 2..2,
                line = 2,
                nodeTexture = "map_node_choose_card"
            ),
            ThreeLineMapGenerator.ThreeLineMapGeneratorEventSpawner(
                {
                    ShopMapEvent(
                        setOf(),
                        "npc.traveling_merchant",
                        mutableSetOf(),
                        3..5,
                        mutableListOf(),
                        0,
                        20,
                        20,
                    )
                },
                offset = 2..2,
                line = 2,
                nodeTexture = "map_node_shop"
            ),
        ),
        decorations = run {
            val moreSkulls = Utils.coinFlip(0.1f, random)
            val moreCacti = !moreSkulls && Utils.coinFlip(0.2f, random)
            wastelandDecorations(moreSkulls, moreCacti)
        }
    ).let { ThreeLineMapGenerator(it) }

    private fun wastelandDecorations(moreSkulls: Boolean, moreCacti: Boolean): List<BaseMapGenerator.MapGeneratorDecoration> = listOf(
        BaseMapGenerator.MapGeneratorDecoration(
            distribution = BaseMapGenerator.DecorationDistribution.RandomDistribution,
            decoration = "map_decoration_wasteland_cactus_1",
            baseWidth = 2f,
            baseHeight = 4f,
            density = if (moreCacti) 0.0048f else 0.0024f,
            checkNodeCollisions = true,
            checkLineCollisions = false,
            checkDecorationCollisions = true,
            generateDecorationCollisions = true,
            onlyCheckCollisionsAtSpawnPoints = true,
            scale = 2.75f..3.25f,
            sortByY = true,
            shrinkBoundsWidth = 0f,
            shrinkBoundsHeight = 0f,
            animated = false,
        ),
        BaseMapGenerator.MapGeneratorDecoration(
            distribution = BaseMapGenerator.DecorationDistribution.RandomDistribution,
            decoration = "map_decoration_wasteland_cactus_2",
            baseWidth = 2f,
            baseHeight = 4f,
            density = if (moreCacti) 0.0016f else 0.0008f,
            checkNodeCollisions = true,
            checkLineCollisions = false,
            checkDecorationCollisions = true,
            generateDecorationCollisions = true,
            onlyCheckCollisionsAtSpawnPoints = false,
            scale = 2.75f..3.25f,
            sortByY = false,
            shrinkBoundsWidth = 0f,
            shrinkBoundsHeight = 0f,
            animated = false,
        ),
        BaseMapGenerator.MapGeneratorDecoration(
            distribution = BaseMapGenerator.DecorationDistribution.RandomDistribution,
            decoration = "map_decoration_wasteland_skull_1",
            baseWidth = 3f,
            baseHeight = 3f,
            density = if (moreSkulls) 0.0008f else 0.0004f,
            checkNodeCollisions = true,
            checkLineCollisions = false,
            checkDecorationCollisions = true,
            generateDecorationCollisions = true,
            onlyCheckCollisionsAtSpawnPoints = false,
            scale = 1.1f..1.7f,
            sortByY = false,
            shrinkBoundsWidth = 0f,
            shrinkBoundsHeight = 0f,
            animated = false,
        ),
        BaseMapGenerator.MapGeneratorDecoration(
            distribution = BaseMapGenerator.DecorationDistribution.RandomDistribution,
            decoration = "map_decoration_wasteland_skull_2",
            baseWidth = 3f,
            baseHeight = 3f,
            density = if (moreSkulls) 0.0008f else 0.0004f,
            checkNodeCollisions = true,
            checkLineCollisions = false,
            checkDecorationCollisions = true,
            generateDecorationCollisions = true,
            onlyCheckCollisionsAtSpawnPoints = false,
            scale = 1.1f..1.7f,
            sortByY = false,
            shrinkBoundsWidth = 0f,
            shrinkBoundsHeight = 0f,
            animated = false,
        )
    )

    private fun enemyAmountRange(majorDifficulty: Int): IntRange {
        var checkDifficulty = majorDifficulty
        lateinit var range: IntRange
        while (true) {
            if (checkDifficulty < 0) {
                throw RuntimeException("not enemy config for major difficulty: $majorDifficulty")
            }
            val config = RunGeneratorConfig.enemyConfig.find { it.majorDifficulty == checkDifficulty }
            if (config != null) {
                range = config.amount
                break
            }
            checkDifficulty--
        }
        return range
    }

    private fun generateRunRewards(majorDifficulty: Int): List<RunReward> {
        var checkDifficulty = majorDifficulty
        lateinit var pool: RunRewardPool
        while (true) {
            if (checkDifficulty < 0) {
                throw RuntimeException("no run rewards for difficulty $majorDifficulty")
            }
            val checkPool = RunGeneratorConfig.runRewardPools.find { it.majorDifficulty == checkDifficulty }
            checkDifficulty--
            checkPool ?: continue
            pool = checkPool
            break
        }
        val rewards = mutableListOf<RunReward>()
        val collections = pool.rewards.toMutableList()
        require(collections.isNotEmpty()) { "run reward pool is empty" }
        repeat(pool.maxRewards) {
            if (!Utils.coinFlip(pool.rewardProbability, random)) return@repeat
            if (collections.isEmpty()) {
                FortyFive.logger.warn(logTag, "not enough run reward collections for major difficulty $majorDifficulty")
                return rewards
            }
            val collection = collections.random(random)
            collections.remove(collection)
            require(collection.isNotEmpty()) { "run reward collection is empty" }
            val reward = collection.random(random)
            rewards.add(reward)
        }
        return rewards
    }

    private fun generateRunModifiers(biome: String, majorDifficulty: Int): List<RunModifier> {
        var checkDifficulty = majorDifficulty
        lateinit var pool: Pair<Float, List<String>>
        while (true) {
            if (checkDifficulty < 0) {
                throw RuntimeException("no run modifier pool for major difficulty $majorDifficulty")
            }
            val checkPool = RunGeneratorConfig.runModifierPools[checkDifficulty]
            checkDifficulty--
            checkPool ?: continue
            pool = checkPool
            break
        }
        val probabilityIncrease = RunGeneratorConfig.runModifierProbabilityChanges[biome]
        val modifiers = if (probabilityIncrease == null) {
            pool.second
        } else {
            pool.second + probabilityIncrease.filter { it in pool.second }
        }.map { RunModifier.get(it) }

        val selectedModifiers = mutableListOf<RunModifier>()
        repeat(RunGeneratorConfig.runModifiersMax) {
            if (!Utils.coinFlip(pool.first, random)) return@repeat

            val start = modifiers.indices.random(random)
            var current = start
            while (true) {
                val modifier = modifiers[current]
                if (modifier !in selectedModifiers && selectedModifiers.none { RunModifier.isBlacklisted(modifier, it) }) {
                    selectedModifiers.add(modifier)
                    break
                }
                current++
                current %= modifiers.size
                if (current == start) {
                    FortyFive.logger.warn(
                        logTag,
                        "cant find non-blacklisted modifier option.\n" +
                        "selectedModifiers = $selectedModifiers, modifiers = $modifiers"
                    )
                    break
                }
            }
        }
        return selectedModifiers
    }

    companion object {
        const val logTag: String = "RunGenerator"
    }

}
