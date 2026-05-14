package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.EncounterModifier
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.map.EncounterPlaceholderMapEvent
import com.microwavestudios.fortyfive.map.MapNodeBuilder
import com.microwavestudios.fortyfive.run.RunGenerator.Companion.logTag
import com.microwavestudios.fortyfive.utils.Utils
import com.microwavestudios.fortyfive.utils.fractionalPart
import com.microwavestudios.fortyfive.utils.unreachable
import com.microwavestudios.fortyfive.utils.zip
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.random.Random

data class Encounter(
    val enemiesGroups: List<String>,
    val encounterModifierNames: Set<String>,
    val forceCards: List<CardType>?,
    val forceConcreteEnemies: List<String>?,
    val shuffleCards: Boolean,
    val unadjustedMajorDifficulty: Int,
    val majorDifficulty: Int,
    val minorDifficulty: Float,
    val difficultyScalingInfo: Float, // additional info for debugging/balancing
    val special: Boolean,
) {

    val encounterModifier: List<EncounterModifier> by lazy {
        encounterModifierNames
            .map { EncounterModifier.getFromName(it) }
    }

    fun createEnemies(): List<Enemy> {
        val difficultyAdjustment = EncounterGenerator.justInTimeDifficultyAddition()
        val majorDifficulty = (majorDifficulty + difficultyAdjustment.toInt()).coerceAtLeast(0)
        val minorDifficulty = minorDifficulty + difficultyAdjustment.fractionalPart()
        val enemiesOnj = ConfigFileManager.getConfigFile("enemies")
        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
        val healthMultiplier = 1f + ((minorDifficulty - 1f) * RunGeneratorConfig.enemyHealthAdjustment)
        val enemies = forceConcreteEnemies ?:
            EncounterGenerator.generateConcreteEnemies(enemiesGroups, majorDifficulty)
        return enemies
            .map { enemy -> enemyPrototypes.find { it.name == enemy } ?: throw RuntimeException("unknown enemy $enemy") }
            .map { it.create((it.baseHealth * healthMultiplier).toInt()) }
    }

    fun asOnj(): OnjObject = buildOnjObject {
        "enemies" with enemiesGroups
        "encounterModifier" with encounterModifierNames
        "forceCards" with forceCards?.map { it.asOnj() }
        forceConcreteEnemies?.let { "forceConcreteEnemies" with forceConcreteEnemies }
        "shuffleCards" with shuffleCards
        "unadjustedMajorDifficulty" with unadjustedMajorDifficulty
        "majorDifficulty" with majorDifficulty
        "minorDifficulty" with minorDifficulty
        "difficultyScalingInfo" with difficultyScalingInfo
        "special" with special
    }

    companion object {

        fun fromOnj(onj: OnjObject): Encounter = Encounter(
            onj.get<OnjArray>("enemies").value.map { it.value as String },
            onj.get<OnjArray>("encounterModifier").value.map { it.value as String }.toSet(),
            onj.getOr<OnjArray?>("forceCards", null)?.value?.map { CardType.fromOnj(it as OnjObject) },
            onj.getOr<OnjArray?>("forceConcreteEnemies", null)?.value?.map { it.value as String },
            onj.getOr("shuffleCards", true),
            onj.get<Long>("unadjustedMajorDifficulty").toInt(),
            onj.get<Long>("majorDifficulty").toInt(),
            onj.get<Double>("minorDifficulty").toFloat(),
            onj.getOr<Double>("difficultyScalingInfo", -1.0).toFloat(),
            onj.getOr("special", false),
        )
    }

}

object EncounterGenerator {

    fun justInTimeDifficultyAddition(): Float {
        val profile = FortyFive.profileManager.currentProfile ?: return 0f
        val run = profile.activeRun ?: return 0f
        val scalingData = when (run.type) {
            RunType.CONSTRUCTED -> RunGeneratorConfig.scalingConstructed
            RunType.LIMITED -> RunGeneratorConfig.scalingLimited
            else -> return 0f
        }
        val encountersStarted = profile.encountersStartedInRun ?: return 0f
        return encountersStarted * scalingData.encounterStartedScale
    }

    fun generateConcreteEnemies(groupNames: List<String>, majorDifficulty: Int): List<String> {
        val enemyAmount = groupNames.size
        val random = Random
        val enemyGroups = RunGeneratorConfig.enemyGroups
        val enemies = when (enemyAmount) {
            1 -> generateConcreteSingleEnemy(groupNames, majorDifficulty, enemyGroups, random)
            2 -> generateConcreteTwoEnemies(groupNames, majorDifficulty, enemyGroups, random)
            3 -> generateConcreteThreeEnemies(groupNames, majorDifficulty, enemyGroups, random)
            else -> unreachable()
        }
        if (enemies != null) return enemies
        FortyFive.logger.warn(
            logTag,
            "couldn't generate concrete enemies for configuration: $groupNames and difficulty: $majorDifficulty"
        )
        // generate enemies that don't match exactly
        val config = mutableListOf<String>()
        var diff = 0
        repeat(enemyAmount) { i ->
            val enemyGroupName = groupNames[i]
            val group = enemyGroups[enemyGroupName]
            requireNotNull(group) { "unknown enemy $enemyGroupName" }
            val neededDiff = majorDifficulty - diff
            val result = group.entries.find { it.key <= neededDiff }
                ?: return@repeat
            val (enemyDiff, enemyName) = result
            config.add(enemyName)
            diff += enemyDiff
        }
        if (config.isEmpty()) {
            // all enemies are too difficult
            return listOf(
                enemyGroups[groupNames.first()]!!.minBy { it.key }.value
            )
        }
        return config
    }

    private fun generateConcreteSingleEnemy(
        groupNames: List<String>,
        majorDifficulty: Int,
        enemyGroups: Map<String, Map<Int, String>>,
        random: Random
    ): List<String>? {
        val enemy = groupNames.first()
        val group = enemyGroups[enemy]
        requireNotNull(group) { "Unknown enemy $enemy" }
        return group[majorDifficulty]?.let { listOf(it) }
    }

    private fun generateConcreteTwoEnemies(
        groupNames: List<String>,
        majorDifficulty: Int,
        enemyGroups: Map<String, Map<Int, String>>,
        random: Random
    ): List<String>? {
        val firstEnemy = groupNames.first()
        val secondEnemy = groupNames[1]
        val firstEnemyGroup = enemyGroups[firstEnemy]
        val secondEnemyGroup = enemyGroups[secondEnemy]
        requireNotNull(firstEnemyGroup) { "Unknown enemy $firstEnemy" }
        requireNotNull(secondEnemyGroup) { "Unknown enemy $secondEnemy" }

        val firstEnemyGroupList = firstEnemyGroup.toList()
        val offset = (0..firstEnemyGroupList.size).random(random)
        firstEnemyGroupList.indices.forEach { i ->
            val offsetIndex = (i + offset) % firstEnemyGroupList.size
            val (diff, name) = firstEnemyGroupList[offsetIndex]
            val secondDiff = majorDifficulty - diff
            if (secondDiff <= 0) return@forEach
            val secondEnemy = secondEnemyGroup[secondDiff]
                ?: return@forEach
            return listOf(name, secondEnemy)
        }
        return null
    }

    private fun generateConcreteThreeEnemies(
        groupNames: List<String>,
        majorDifficulty: Int,
        enemyGroups: Map<String, Map<Int, String>>,
        random: Random
    ): List<String>? {
        val firstEnemy = groupNames.first()
        val secondEnemy = groupNames[1]
        val thirdEnemy = groupNames[2]
        val firstEnemyGroup = enemyGroups[firstEnemy]
        val secondEnemyGroup = enemyGroups[secondEnemy]
        val thirdEnemyGroup = enemyGroups[thirdEnemy]
        requireNotNull(firstEnemyGroup) { "Unknown enemy $firstEnemy" }
        requireNotNull(secondEnemyGroup) { "Unknown enemy $secondEnemy" }
        requireNotNull(thirdEnemyGroup) { "Unknown enemy $thirdEnemy" }

        val firstEnemyGroupList = firstEnemyGroup.toList()
        val secondEnemyGroupList = firstEnemyGroup.toList()
        val offset = (0..firstEnemyGroupList.size).random(random)
        val secondOffset = (0..secondEnemyGroupList.size).random(random)
        firstEnemyGroupList.indices.forEach { i ->
            val offsetI = (i + offset) % firstEnemyGroupList.size
            val (firstDiff, firstName) = firstEnemyGroupList[offsetI]
            if (firstDiff >= majorDifficulty) return@forEach

            secondEnemyGroupList.indices.forEach { j ->
                val offsetJ = (j + secondOffset) % secondEnemyGroupList.size
                val (secondDiff, secondName) = secondEnemyGroupList[offsetJ]
                val thirdDiff = majorDifficulty - (firstDiff + secondDiff)
                if (thirdDiff <= 0) return@forEach
                val thirdName = thirdEnemyGroup[thirdDiff]
                    ?: return@forEach
                return listOf(firstName, secondName, thirdName)
            }
        }
        return null
    }

    fun generate(
        placeholder: EncounterPlaceholderMapEvent,
        node: MapNodeBuilder,
        startNode: MapNodeBuilder,
        run: Run
    ): Encounter {

        val random = Random(placeholder.seed)

        var majorDifficulty = placeholder.majorDifficulty
        var minorDifficulty = placeholder.minorDifficulty

        var baseModifiers = run.behaviours.mapNotNull { it.addEncounterModifier() }
        if (!EncounterModifier.isValid(baseModifiers)) {
            FortyFive.logger.warn("EncounterGen", "Run behaviours generated invalid list of encounter modifiers: ${run.behaviours}")
            baseModifiers = listOf()
        }

        val modifiers = generateEncounterModifier(random, baseModifiers, placeholder)

        var difficultyAdjustment = -modifiers
            .map { EncounterModifier.getFromName(it) }
            .sumOf { it.difficultyChange.toDouble() }

        val difficultyScaling = difficultyScale(node, placeholder, startNode)
        difficultyAdjustment += difficultyScaling

        majorDifficulty = (majorDifficulty + difficultyAdjustment.toInt()).coerceAtLeast(0)
        minorDifficulty = (minorDifficulty + difficultyAdjustment.fractionalPart()).toFloat()

        val enemies = generateEnemies(random, placeholder)

        return Encounter(
            enemies,
            modifiers.toSet(),
            null, null,
            true,
            placeholder.unadjustedMajorDifficulty,
            majorDifficulty,
            minorDifficulty,
            difficultyScaling,
            placeholder.genExtraction
        )
    }

    private fun difficultyScale(
        node: MapNodeBuilder,
        placeholder: EncounterPlaceholderMapEvent,
        startNode: MapNodeBuilder
    ): Float {
        val currentDistance = node.distance
        require(currentDistance != -1) { "node distance is -1" }
        val maxDistance = maxDistance(startNode)
        val percent = currentDistance.toFloat() / maxDistance.toFloat()
        return placeholder.difficultyScaling.scale(placeholder.scaleMin, placeholder.scaleMax, percent)
    }

    private fun maxDistance(node: MapNodeBuilder): Int {
        val localMaxDist = node.edgesTo.maxOf { it.distance }
        if (localMaxDist <= node.distance) return node.distance
        val maxEdges = node.edgesTo.filter { it.distance == localMaxDist }
        return maxEdges.maxOf { maxDistance(it) }
    }

    private fun generateEnemies(
        random: Random,
        placeholder: EncounterPlaceholderMapEvent
    ): List<String> {
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

        val allowedConfigurations = RunGeneratorConfig.enemyConfigurations.filter { configuration ->
            configuration.all { it in config.allowedEnemies }
        }

        if (allowedConfigurations.isEmpty()) {
            FortyFive.logger.warn(
                logTag,
                "No allowable enemy configuration for difficulty ${placeholder.unadjustedMajorDifficulty}"
            )
            val allConfigurations = RunGeneratorConfig.enemyConfigurations
            require(allConfigurations.isNotEmpty()) { "No enemy configurations are defined" }
            return allConfigurations.first()
        }

        val enemyAmountGenerationOrder = when (placeholder.amountEnemies.random(random)) {
            1 -> arrayOf(1, 2, 3)
            2 -> arrayOf(2, 1, 3)
            3 -> arrayOf(3, 2, 1)
            else -> unreachable()
        }

        var configurations: List<List<String>>? = null
        enemyAmountGenerationOrder.forEach { amount ->
            if (configurations != null) return@forEach
            val configurationsWithCorrectSize = allowedConfigurations.filter { it.size == amount }
            if (configurationsWithCorrectSize.isEmpty()) return@forEach
            configurations = configurationsWithCorrectSize
        }
        requireNotNull(configurations) // should never happen as allowedConfigurations is never empty

        val winner = configurations
            .zip { scoreEnemyConfiguration(random, it, placeholder) }
            .maxBy { it.second }
            .first

        return winner
    }

    private fun scoreEnemyConfiguration(
        random: Random,
        enemies: List<String>,
        placeholder: EncounterPlaceholderMapEvent
    ): Int {
        val distinctEnemies = enemies.distinct().size
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
        val probabilityIncreases = RunGeneratorConfig.enemyProbabilityIncrease
        var bonusPoints = 0
        probabilityIncreases
            .forEach {
                if (it.biome != placeholder.biome || it.enemy !in enemies) return@forEach
                bonusPoints += it.bonusPoints
            }
        val randomPoints = (0..RunGeneratorConfig.enemyProbabilityRandomPoints).random(random)
        return distinctEnemiesPoints + bonusPoints + randomPoints
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
