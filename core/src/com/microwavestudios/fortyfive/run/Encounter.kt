package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.EncounterModifier
import com.microwavestudios.fortyfive.game.UserPrefs
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.map.EncounterPlaceholderMapEvent
import com.microwavestudios.fortyfive.map.MapNodeBuilder
import com.microwavestudios.fortyfive.run.RunGenerator.Companion.logTag
import com.microwavestudios.fortyfive.utils.Utils
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.random.Random

data class Encounter(
    val enemies: List<String>,
    val encounterModifierNames: Set<String>,
    val forceCards: List<String>?,
    val shuffleCards: Boolean,
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
        "minorDifficulty" with minorDifficulty
        "special" with special
    }

    companion object {

        fun fromOnj(onj: OnjObject): Encounter = Encounter(
            onj.get<OnjArray>("enemies").value.map { it.value as String },
            onj.get<OnjArray>("encounterModifier").value.map { it.value as String }.toSet(),
            onj.getOr<OnjArray?>("forceCards", null)?.value?.map { it.value as String },
            onj.getOr("shuffleCards", true),
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

        val difficultyAdjustment = modifiers
            .map { EncounterModifier.getFromName(it) }
            .sumOf { it.difficultyChange.toDouble() }

        majorDifficulty = (majorDifficulty + difficultyAdjustment).toInt().coerceAtLeast(0)
        minorDifficulty = ((minorDifficulty + difficultyAdjustment).absoluteValue % 1).toFloat().coerceAtLeast(0f)

    }

    private fun generateEncounterModifier(
        random: Random,
        baseModifiers: List<String>,
        placeholder: EncounterPlaceholderMapEvent
    ): List<String> {
        val pools = encounterModifierPools
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

            var modifiers = pool.modifiers
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


    val configFile: OnjObject by lazy {
        ConfigFileManager.getConfigFile("runGeneratorConfig")
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

    data class EncounterModifierPool(
        val majorDifficulty: Int,
        val modifierProbability: Float,
        val maxModifiers: Int,
        val modifiers: List<String>
    )

}
