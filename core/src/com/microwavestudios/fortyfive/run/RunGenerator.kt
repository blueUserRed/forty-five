package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.map.ChooseCardMapEvent
import com.microwavestudios.fortyfive.map.EmptyMapEvent
import com.microwavestudios.fortyfive.map.EncounterPlaceholderMapEvent
import com.microwavestudios.fortyfive.map.ShopMapEvent
import com.microwavestudios.fortyfive.map.generation.BaseMapGenerator
import com.microwavestudios.fortyfive.map.generation.PointCloudMapGenerator
import com.microwavestudios.fortyfive.map.generation.RadialMapGenerator
import com.microwavestudios.fortyfive.map.generation.ThreeLineMapGenerator
import com.microwavestudios.fortyfive.utils.Utils
import com.microwavestudios.fortyfive.utils.random
import com.microwavestudios.fortyfive.utils.unreachable
import com.microwavestudios.fortyfive.utils.weightedRandom
import kotlin.random.Random

class RunGenerator {

    private val random: Random = Random

    fun generateRun(forDifficulty: Int, forBiome: String, forArea: String, type: RunType): Run {
        require(type in arrayOf(RunType.LIMITED, RunType.CONSTRUCTED)) {
            "generateRun only works for Limited and Constructed Runs"
        }

        val modifiers = generateRunModifiers(forBiome, forDifficulty)

        val difficultyAdjustment = -modifiers.sumOf { it.difficultyAdjustment.toDouble() }
        val majorDifficulty = (forDifficulty + difficultyAdjustment.toInt()).coerceAtLeast(0)
        val minorDifficulty = 1f + difficultyAdjustment.fractionalPart().toFloat()

        val rewards = generateRunRewards(forDifficulty)

        val (minDiff, maxDiff, scaling) = when (type) {
            RunType.CONSTRUCTED -> RunGeneratorConfig.scalingConstructed
            RunType.LIMITED -> RunGeneratorConfig.scalingLimited
            else -> unreachable()
        }

        val mapGenerator = MapGens.mapGenFor(
            type,
            random,
            majorDifficulty,
            forDifficulty,
            minorDifficulty,
            enemyAmountRange(forDifficulty),
            modifiers,
            minDiff,
            maxDiff,
            scaling,
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

    private object MapGens {

        fun mapGenFor(
            type: RunType,
            random: Random,
            majorDifficulty: Int,
            unadjustedMajorDifficulty: Int,
            minorDifficulty: Float,
            enemyAmountRange: IntRange,
            runModifier: List<RunModifier>,
            minDiff: Float,
            maxDiff: Float,
            difficultyScaling: DifficultyScaling,
            biome: String
        ): BaseMapGenerator {
            val options = when (type) {
                RunType.LIMITED -> listOf(
                    10 to MapGenType.ThreeLine,
                    20 to MapGenType.PointCloud,
                    20 to MapGenType.Radial
                )
                RunType.CONSTRUCTED -> listOf(
                    10 to MapGenType.ThreeLine,
                    20 to MapGenType.PointCloud,
                    20 to MapGenType.Radial
                )
                else -> unreachable()
            }
            val chosen = options.weightedRandom(random)
            return when (chosen) {
                MapGenType.PointCloud -> pointCloudMapGen(
                    random,
                    majorDifficulty,
                    unadjustedMajorDifficulty,
                    minorDifficulty,
                    enemyAmountRange,
                    runModifier,
                    minDiff,
                    maxDiff,
                    difficultyScaling,
                    biome,
                )
                MapGenType.ThreeLine -> threeLineMapGen(
                    random,
                    majorDifficulty,
                    unadjustedMajorDifficulty,
                    minorDifficulty,
                    enemyAmountRange,
                    runModifier,
                    minDiff,
                    maxDiff,
                    difficultyScaling,
                    biome,
                )
                MapGenType.Radial -> radialMapGen(
                    random,
                    majorDifficulty,
                    unadjustedMajorDifficulty,
                    minorDifficulty,
                    enemyAmountRange,
                    runModifier,
                    minDiff,
                    maxDiff,
                    difficultyScaling,
                    biome,
                )
            }
        }

        enum class MapGenType { ThreeLine, PointCloud, Radial }

        fun decorationsFor(biome: String, random: Random): List<BaseMapGenerator.MapGeneratorDecoration> = when (biome) {
            "wasteland" -> {
                val moreSkulls = Utils.coinFlip(0.1f, random)
                val moreCacti = !moreSkulls && Utils.coinFlip(0.2f, random)
                wastelandDecorations(moreSkulls, moreCacti)
            }
            "bewitched_forest" -> {
                val moreSheep = Utils.coinFlip(0.3f, random)
                bewitchedForestDecorations(moreSheep)
            }
            "magenta_mountains" -> TODO()
            else -> unreachable()
        }

        fun fillEvents(
            random: Random,
            majorDifficulty: Int,
            unadjustedMajorDifficulty: Int,
            minorDifficulty: Float,
            enemyAmountRange: IntRange,
            runModifier: List<RunModifier>,
            minDiff: Float,
            maxDiff: Float,
            difficultyScaling: DifficultyScaling,
            biome: String
        ): List<BaseMapGenerator.MapGeneratorFillEvent> = listOf(
            BaseMapGenerator.MapGeneratorFillEvent(
                {
                    EncounterPlaceholderMapEvent(
                        false,
                        majorDifficulty,
                        unadjustedMajorDifficulty,
                        minorDifficulty,
                        runModifier,
                        enemyAmountRange,
                        biome,
                        difficultyScaling,
                        minDiff,
                        maxDiff,
                        random.nextLong()
                    )
                },
                null, null,
                100
            ),
            BaseMapGenerator.MapGeneratorFillEvent(
                {
                    ChooseCardMapEvent(
                        listOf(),
                        true,
                        0,
                        20, 10,
                        random.nextLong(),
                        3
                    )
                },
                2, null,
                20
            ),
            BaseMapGenerator.MapGeneratorFillEvent(
                { EmptyMapEvent() },
                2, 4,
                20
            ),
        )

        // parameters for symmetry with fillEvents() and in case encounters will be added, in which case
        // the parameters will be needed
        @Suppress("unused")
        fun fixedEvents(
            random: Random,
            majorDifficulty: Int,
            unadjustedMajorDifficulty: Int,
            minorDifficulty: Float,
            enemyAmountRange: IntRange,
            runModifier: List<RunModifier>,
            minDiff: Float,
            maxDiff: Float,
            difficultyScaling: DifficultyScaling,
            biome: String
        ): List<BaseMapGenerator.MapGeneratorFixedEvent> = listOf(
            BaseMapGenerator.MapGeneratorFixedEvent(
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
                4, null,
                1
            )
        )

        fun pointCloudMapGen(
            random: Random,
            majorDifficulty: Int,
            unadjustedMajorDifficulty: Int,
            minorDifficulty: Float,
            enemyAmountRange: IntRange,
            runModifier: List<RunModifier>,
            minDiff: Float,
            maxDiff: Float,
            difficultyScaling: DifficultyScaling,
            biome: String
        ): BaseMapGenerator = PointCloudMapGenerator.PointCloudMapGeneratorData(
            nodeProtectedArea = 20f,
            amountNodes = 16,
            roadLength = 240f,
            roadHeight = 110f,
            exclusionRadius = 10f,
            locationSignProtectedAreaWidth = 25f,
            locationSignProtectedAreaHeight = 30f,
            firstNodeEvent = { EmptyMapEvent() },
            lastNodeEvent = {
                EncounterPlaceholderMapEvent(
                    true,
                    majorDifficulty,
                    unadjustedMajorDifficulty,
                    minorDifficulty,
                    runModifier,
                    enemyAmountRange,
                    biome,
                    difficultyScaling,
                    minDiff,
                    maxDiff,
                    random.nextLong()
                )
            },
            randomStepsToLastNode = 4,
            majorDifficulty = unadjustedMajorDifficulty,
            horizontalExtension = 80f,
            verticalExtension = 50f,
            decorations = decorationsFor(biome, random),
            biome = biome,
            rotation = (-(Math.PI / 4).toFloat()..(Math.PI / 2).toFloat()).random(random),
            fillEvents = fillEvents(
                random,
                majorDifficulty,
                unadjustedMajorDifficulty,
                minorDifficulty,
                enemyAmountRange,
                runModifier,
                minDiff,
                maxDiff,
                difficultyScaling,
                biome
            ),
            fixedEvents = fixedEvents(
                random,
                majorDifficulty,
                unadjustedMajorDifficulty,
                minorDifficulty,
                enemyAmountRange,
                runModifier,
                minDiff,
                maxDiff,
                difficultyScaling,
                biome
            ),
        ).let { PointCloudMapGenerator(it) }

        fun threeLineMapGen(
            random: Random,
            majorDifficulty: Int,
            unadjustedMajorDifficulty: Int,
            minorDifficulty: Float,
            enemyAmountRange: IntRange,
            runModifier: List<RunModifier>,
            minDiff: Float,
            maxDiff: Float,
            difficultyScaling: DifficultyScaling,
            biome: String
        ): BaseMapGenerator = ThreeLineMapGenerator.ThreeLineMapGeneratorData(
            majorDifficulty = unadjustedMajorDifficulty,
            biome = biome,
            nodeProtectedArea = 20f,
            altLinesOffset = (50f..65f).random(random),
            mainLineNodes = 8,
            altLinesPadding = 0..2,
            varianceX = 12f,
            varianceY = 12f,
            roadLength = 270f,
            horizontalExtension = 80f,
            verticalExtension = 50f,
            locationSignProtectedAreaWidth = 25f,
            locationSignProtectedAreaHeight = 30f,
            firstNodeEvent = { EmptyMapEvent() },
            lastNodeEvent = {
                EncounterPlaceholderMapEvent(
                    true,
                    majorDifficulty,
                    unadjustedMajorDifficulty,
                    minorDifficulty,
                    runModifier,
                    enemyAmountRange,
                    biome,
                    difficultyScaling,
                    minDiff,
                    maxDiff,
                    random.nextLong()
                )
            },
            randomStepsToLastNode = 4,
            decorations = decorationsFor(biome, random),
            rotation = (-(Math.PI / 4).toFloat()..(Math.PI / 2).toFloat()).random(random),
            fillEvents = fillEvents(
                random,
                majorDifficulty,
                unadjustedMajorDifficulty,
                minorDifficulty,
                enemyAmountRange,
                runModifier,
                minDiff,
                maxDiff,
                difficultyScaling,
                biome
            ),
            fixedEvents = fixedEvents(
                random,
                majorDifficulty,
                unadjustedMajorDifficulty,
                minorDifficulty,
                enemyAmountRange,
                runModifier,
                minDiff,
                maxDiff,
                difficultyScaling,
                biome
            ),
        ).let { ThreeLineMapGenerator(it) }

        fun radialMapGen(
            random: Random,
            majorDifficulty: Int,
            unadjustedMajorDifficulty: Int,
            minorDifficulty: Float,
            enemyAmountRange: IntRange,
            runModifier: List<RunModifier>,
            minDiff: Float,
            maxDiff: Float,
            difficultyScaling: DifficultyScaling,
            biome: String
        ): RadialMapGenerator = RadialMapGenerator.RadialMapGeneratorData(
            majorDifficulty = unadjustedMajorDifficulty,
            biome = biome,
            nodeProtectedArea = 20f,
            horizontalExtension = 80f,
            verticalExtension = 50f,
            locationSignProtectedAreaWidth = 25f,
            locationSignProtectedAreaHeight = 30f,
            firstNodeEvent = { EmptyMapEvent() },
            lastNodeEvent = {
                EncounterPlaceholderMapEvent(
                    true,
                    majorDifficulty,
                    unadjustedMajorDifficulty,
                    minorDifficulty,
                    runModifier,
                    enemyAmountRange,
                    biome,
                    difficultyScaling,
                    minDiff,
                    maxDiff,
                    random.nextLong()
                )
            },
            circles = listOf(
                RadialMapGenerator.Circle(20f, 3, 0.005f),
                RadialMapGenerator.Circle(50f, 6, 0.005f),
                RadialMapGenerator.Circle(70f, 12, 0.005f),
                RadialMapGenerator.Circle(90f, 15, 0.005f),
                RadialMapGenerator.Circle(110f, 18, 0.005f),
            ),
            randomStepsToLastNode = 4,
            decorations = decorationsFor(biome, random),
            rotation = (-(Math.PI / 4).toFloat()..(Math.PI / 2).toFloat()).random(random),
            fillEvents = fillEvents(
                random,
                majorDifficulty,
                unadjustedMajorDifficulty,
                minorDifficulty,
                enemyAmountRange,
                runModifier,
                minDiff,
                maxDiff,
                difficultyScaling,
                biome
            ),
            fixedEvents = fixedEvents(
                random,
                majorDifficulty,
                unadjustedMajorDifficulty,
                minorDifficulty,
                enemyAmountRange,
                runModifier,
                minDiff,
                maxDiff,
                difficultyScaling,
                biome
            ),
        ).let { RadialMapGenerator(it) }

        private fun bewitchedForestDecorations(moreSheep: Boolean): List<BaseMapGenerator.MapGeneratorDecoration> = listOf(
            BaseMapGenerator.MapGeneratorDecoration(
                distribution = BaseMapGenerator.DecorationDistribution.RandomDistribution,
                decoration = "sheep",
                baseWidth = 8f,
                baseHeight = 8f,
                density = if (moreSheep) 0.002f else 0.001f,
                checkNodeCollisions = true,
                checkLineCollisions = false,
                checkDecorationCollisions = true,
                generateDecorationCollisions = true,
                onlyCheckCollisionsAtSpawnPoints = false,
                scale = (1f..1.1f),
                sortByY = false,
                animated = true,
                shrinkBoundsHeight = 0f,
                shrinkBoundsWidth = 0f,
            ),
            BaseMapGenerator.MapGeneratorDecoration(
                distribution = BaseMapGenerator.DecorationDistribution.RandomDistribution,
                decoration = "tree",
                baseWidth = 5f,
                baseHeight = 10f,
                density = 0.024f,
                checkNodeCollisions = true,
                checkLineCollisions = false,
                checkDecorationCollisions = true,
                generateDecorationCollisions = true,
                onlyCheckCollisionsAtSpawnPoints = true,
                scale = (1f..2f),
                sortByY = true,
                animated = true,
                shrinkBoundsHeight = 0f,
                shrinkBoundsWidth = 0f
            )
        )

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

    }

}
