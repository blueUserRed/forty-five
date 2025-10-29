package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.EncounterModifier
import com.microwavestudios.fortyfive.utils.unreachable
import onj.value.OnjArray

abstract class RunModifier {

    abstract class ModifyAllEncounters(val encounterModifier: EncounterModifier) : RunModifier() {
        override val difficultyAdjustment: Float = 0f // handled by encounter
        override fun addModifier(): EncounterModifier? = encounterModifier
    }

    data object AllRain : ModifyAllEncounters(EncounterModifier.Rain)
    data object AllMoist : ModifyAllEncounters(EncounterModifier.Moist)
    data object AllBewitchedMist : ModifyAllEncounters(EncounterModifier.BewitchedMist)
    data object AllFrozen : ModifyAllEncounters(EncounterModifier.Frost)


    abstract val difficultyAdjustment: Float

    open fun addModifier(): EncounterModifier? = null

    open fun name(): String = this::class.simpleName ?: unreachable()

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

        fun isBlacklisted(m1: RunModifier, m2: RunModifier): Boolean {
            blacklist.forEach { pool ->
                if (m1 in pool && m2 in pool) return true
            }
            return false
        }
    }
}
