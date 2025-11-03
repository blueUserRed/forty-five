package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.utils.toIntRange
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.collections.associate
import kotlin.collections.map

object RunGeneratorConfig {

    val configFile: OnjObject by lazy {
        ConfigFileManager.getConfigFile("runGeneratorConfig")
    }

    val enemiesFile: OnjObject by lazy {
        ConfigFileManager.getConfigFile("enemies")
    }

    val runRewardPools: List<RunRewardPool> by lazy {
        configFile
            .get<OnjArray>("runRewardPools")
            .value
            .map { obj ->
                obj as OnjObject
                val majorDifficulty = obj.get<Long>("majorDifficulty").toInt()
                val maxRewards = obj.get<Long>("maxRewards").toInt()
                val rewardProbability = obj.get<Double>("rewardProbability").toFloat()
                val pools = obj
                    .get<OnjArray>("rewards")
                    .value
                    .map { pool ->
                        pool as OnjArray
                        pool.value.map { RunReward.fromOnj(it as OnjNamedObject) }
                    }
                RunRewardPool(
                    majorDifficulty,
                    maxRewards,
                    rewardProbability,
                    pools
                )
            }
    }

    val runModifiersMax: Int by lazy { configFile.get<Long>("runModifiersMax").toInt() }

    val runModifierPools: Map<Int, Pair<Float, List<String>>> by lazy {
        configFile
            .get<OnjArray>("runModifierPools")
            .value
            .associate { pool ->
                pool as OnjObject
                val modifiers = pool
                    .get<OnjArray>("modifiers")
                    .value
                    .map { it.value as String }
                val majDifficulty = pool.get<Long>("majorDifficulty").toInt()
                majDifficulty to (pool.get<Double>("modifierProbability").toFloat() to modifiers)
            }
    }

    val encounterModifierPools: List<EncounterModifierPool> by lazy {
        configFile
            .get<OnjArray>("encounterModifierPools")
            .value
            .map { obj ->
                obj as OnjObject
                val majDiff = obj.get<Long>("majorDifficulty").toInt()
                val modifierProbability = obj.get<Double>("modifierProbability").toFloat()
                val maxModifiers = obj.get<Long>("maxModifiers").toInt()
                val modifiers = obj.get<OnjArray>("modifiers").value.map { it.value as String }
                EncounterModifierPool(majDiff, modifierProbability, maxModifiers, modifiers)
            }
    }

    val runModifierProbabilityChanges: Map<String, List<String>> by lazy {
        configFile
            .get<OnjArray>("runModifierProbabilityIncreases")
            .value
            .associate { obj ->
                obj as OnjObject
                val biome = obj.get<String>("biome")
                val moreLikely = obj.get<OnjArray>("makeMoreLikely").value.map { it.value as String }
                biome to moreLikely
            }
    }

    val enemyConfig: List<EnemyConfig> by lazy {
        configFile
            .get<OnjArray>("enemies")
            .value
            .map { obj ->
                obj as OnjObject
                var amount = obj.get<OnjArray>("amount").toIntRange()
                require(amount.first >= 1) { "Encounter has to have at least one enemy" }
                require(amount.last <= 3) { "Encounter cant have more than three enemies" }
                EnemyConfig(
                    obj.get<Long>("majorDifficulty").toInt(),
                    amount,
                    obj.get<OnjArray>("allowedEnemies").value.map { it.value as String }
                )
            }
    }

    val enemyGroups: Map<String, Map<Int, String>> by lazy {
        enemiesFile
            .get<OnjArray>("enemyGroups")
            .value
            .associate { obj ->
                obj as OnjObject
                val groupName = obj.get<String>("groupName")
                val variants = obj
                    .get<OnjArray>("variants")
                    .value
                    .associate {
                        it as OnjObject
                        it.get<Long>("majorDifficulty").toInt() to it.get<String>("name")
                    }
                groupName to variants
            }
    }

    val enemyProbabilityIncrease: List<EnemyProbabilityIncrease> by lazy {
        configFile
            .get<OnjArray>("enemyProbabilityIncrease")
            .value
            .map { obj ->
                obj as OnjObject
                EnemyProbabilityIncrease(
                    obj.get<String>("enemy"),
                    obj.get<String>("biome"),
                    obj.get<Long>("bonusPoints").toInt()
                )
            }
    }

    val enemyProbabilityRandomPoints: Int by lazy {
        configFile.get<Long>("enemyProbabilityRandomPoints").toInt()
    }

}

data class EnemyProbabilityIncrease(
    val enemy: String,
    val biome: String,
    val bonusPoints: Int
)

data class EnemyConfig(
    val majorDifficulty: Int,
    val amount: IntRange,
    val allowedEnemies: List<String>
)

data class EncounterModifierPool(
    val majorDifficulty: Int,
    val modifierProbability: Float,
    val maxModifiers: Int,
    val modifiers: List<String>
)

data class RunRewardPool(
    val majorDifficulty: Int,
    val maxRewards: Int,
    val rewardProbability: Float,
    val rewards: List<List<RunReward>>
)
