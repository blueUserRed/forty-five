package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.EncounterModifier
import com.microwavestudios.fortyfive.utils.unreachable
import onj.value.OnjArray

abstract class RunModifier(val name: String) {

    data object AllRain : RunModifier("allRain") {

        override val difficultyAdjustment: Float = 0f // handled by encounter
        override val description: String = "All Encounters have the Rain Modifier"
        override fun behaviours(): List<RunBehaviour> = listOf(RunBehaviour.ModifyAllEncounters("rain"))
    }

    data object AllMoist : RunModifier("allMoist") {

        override val difficultyAdjustment: Float = 0f // handled by encounter
        override val description: String = "All Encounters have the Moist Modifier"
        override fun behaviours(): List<RunBehaviour> = listOf(RunBehaviour.ModifyAllEncounters("moist"))
    }

    data object AllBewitchedMist : RunModifier("allBewitchedMist") {

        override val difficultyAdjustment: Float = 0f // handled by encounter
        override val description: String = "All Encounters have the Bewitched Mist Modifier"
        override fun behaviours(): List<RunBehaviour> = listOf(RunBehaviour.ModifyAllEncounters("bewitchedMist"))
    }

    data object AllFrozen : RunModifier("allFrozen") {

        override val difficultyAdjustment: Float = 0f // handled by encounter
        override val description: String = "All Encounters have the Frozen Modifier"
        override fun behaviours(): List<RunBehaviour> = listOf(RunBehaviour.ModifyAllEncounters("frozen"))
    }

    abstract val description: String

    abstract val difficultyAdjustment: Float

    open fun name(): String = this::class.simpleName ?: unreachable()
    open fun behaviours(): List<RunBehaviour> = listOf()

    companion object {

        fun get(name: String): RunModifier = when (name.lowercase()) {
            "allrain" -> AllRain
            "allmoist" -> AllMoist
            "allfrozen" -> AllFrozen
            "allbewitchedmist" -> AllBewitchedMist
            else -> throw RuntimeException("unknown RunModifier: $name")
        }

        val blacklist: List<List<RunModifier>> by lazy {
            val config = ConfigFileManager.getConfigFile("runGeneratorConfig")
            config
                .get<OnjArray>("runModifierBlacklist")
                .value
                .map { pool ->
                    pool as OnjArray
                    pool.value.map { get(it.value as String) }
                }
        }

        fun isValid(modifiers: List<RunModifier>): Boolean {
            modifiers.forEach { modifier ->
                blacklist.forEach { list ->
                    if (modifier !in list) return@forEach
                    list.forEach { toCheck ->
                        if (toCheck == modifier) return@forEach
                        if (toCheck in modifiers) return false
                    }
                }
            }
            return true
        }

        fun isBlacklisted(m1: RunModifier, m2: RunModifier): Boolean {
            blacklist.forEach { pool ->
                if (m1 in pool && m2 in pool) return true
            }
            return false
        }
    }
}
