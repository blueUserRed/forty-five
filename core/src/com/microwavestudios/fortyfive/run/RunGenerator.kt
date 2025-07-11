package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.map.ChooseCardMapEvent
import com.microwavestudios.fortyfive.map.EmptyMapEvent
import com.microwavestudios.fortyfive.map.EncounterMapEvent
import com.microwavestudios.fortyfive.map.ShopMapEvent
import com.microwavestudios.fortyfive.map.generation.BaseMapGenerator
import com.microwavestudios.fortyfive.map.generation.ThreeLineMapGenerator

class RunGenerator {

    fun generateRun(forDifficulty: Int, forBiome: String, forArea: String, type: RunType): Run {
        return Run(
            RunLength.MEDIUM,
            type,
            forDifficulty,
            listOf(RunReward.Cash(100)),
            forBiome,
            forArea,
            mapGen()
        )
    }

    private fun encounter(): Encounter = Encounter(
        enemies = listOf("Outlaw_test"),
        encounterModifierNames = setOf(),
        forceCards = null,
        shuffleCards = true,
        special = false
    )

    private fun mapGen(): BaseMapGenerator = ThreeLineMapGenerator.ThreeLineMapGeneratorData(
        majorDifficulty = 1,
        biome = "wasteland",
        nodeProtectedArea = 20f,
        altLinesOffset = 40f,
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
        lastNodeEvent = { EncounterMapEvent(encounter(), true) },
        mainEvent = ThreeLineMapGenerator.ThreeLineMapGeneratorEventSpawner(
            { EncounterMapEvent(encounter(), false) },
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
                        30478934789L,
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
                        "traveling_merchant",
                        20348920200L,
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
                nodeTexture = "map_node_choose_card"
            ),
        ),
        decorations = listOf(
            BaseMapGenerator.MapGeneratorDecoration(
                distribution = BaseMapGenerator.DecorationDistribution.RandomDistribution,
                decoration = "map_decoration_wasteland_cactus_1",
                baseWidth = 2f,
                baseHeight = 4f,
                density = 0.0024f,
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
                density = 0.0008f,
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
                density = 0.0004f,
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
                density = 0.0004f,
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
        ),
    ).let { ThreeLineMapGenerator(it) }

}