package com.microwavestudios.fortyfive.game

import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardCostModifier
import com.microwavestudios.fortyfive.game.card.CardModifierData
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.Timeline
import onj.value.OnjArray
import kotlin.collections.forEach
import kotlin.collections.map

sealed class EncounterModifier {

    abstract val difficultyChange: Float

    data object Rain : EncounterModifier() {
        override val displayName: String = "Rain"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "Status effects don't work."
        override val difficultyChange: Float = 0.2f

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.NoStatusEffects)
    }

    data object Frost : EncounterModifier() {
        override val displayName: String = "Frost"
        override val iconHandle: ResourceHandle = "encounter_modifier_frost"
        override val description: String = "The revolver doesn't turn. Everlasting doesn't work."
        override val difficultyChange: Float = 1f

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.NoRevolverRotation)
    }

    data object BewitchedMist : EncounterModifier() {
        override val displayName: String = "Bewitched Mist"
        override val iconHandle: ResourceHandle = "encounter_modifier_bewitched_mist"
        override val description: String = "Revolver rotations are inverted."
        override val difficultyChange: Float = 0.1f

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.MirrorRevolverRotations)
    }

    data object Lookalike : EncounterModifier() {
        override val displayName: String = "Lookalike"
        override val iconHandle: ResourceHandle = "encounter_modifier_lookalike"
        override val description: String = "Whenever you place a bullet in the revolver, you get a copy of it in your hand."
        override val difficultyChange: Float = 0f // this modifier is so broken that correcting for it would be useless anyway

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.Lookalike)
    }

    data object Moist : EncounterModifier() {
        override val displayName: String = "Moist"
        override val iconHandle: ResourceHandle = "encounter_modifier_moist"
        override val description: String = "Every bullet in the revolver loses one damage every time it turns."
        override val difficultyChange: Float = 0.5f

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.Moist("moist modifier"))
    }

    class SteelNerves : EncounterModifier() {
        override val displayName: String = "Steel Nerves"
        override val iconHandle: ResourceHandle = "encounter_modifier_steel_nerves"
        override val description: String = "The revolver shoots automatically every ten seconds."
        override val difficultyChange: Float = 0.5f

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.SteelNerves())
    }

    data object DrawOneMoreCard : EncounterModifier() {
        override val displayName: String = "DrawOneMoreCard"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = ""
        override val difficultyChange: Float = 0f

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.DrawMoreCards(1))
    }

    data object Draft : EncounterModifier() {
        override val displayName: String = "Draft"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "You draft your deck from random cards before the encounter."
        override val difficultyChange: Float = 1f

        override fun intermediateScreen(): String = "draftScreen"
    }

    data object AnOfferYouCantRefuse : EncounterModifier() {
        override val displayName: String = "An Offer you can't refuse"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "All bullets cost 1 less, but shooting the revolver costs 1 reserve."
        override val difficultyChange: Float = -0.2f

        override fun behaviours(): List<EncounterBehaviour> = listOf(
            EncounterBehaviour.ChangeBulletCost(-1, "An offer you cant refuse"),
            EncounterBehaviour.ShootingRevolverCostsReserves(1)
        )
    }

    data object BulletSkipping : EncounterModifier() {
        override val displayName: String = "Bullet Skipping"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "When a bullet shoots, it turns twice, skipping the slot in between."
        override val difficultyChange: Float = -0.2f

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.BulletSkipping)
    }

    data object Sacrifice : EncounterModifier() {
        override val displayName: String = "Sacrifice"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "At the beginning of every turn, destroy target bullet."
        override val difficultyChange: Float = 0.65f

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.Sacrifice)
    }

    data object SorryNotSorry : EncounterModifier() {
        override val displayName: String = "Sorry not Sorry"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "At the beginning of every turn, return a random Bullet back to your hand."
        override val difficultyChange: Float = 0.0f

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.SorryNotSorry)
    }

    data object Confused : EncounterModifier() {
        override val displayName: String = "Confused"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "The revolver rotates when a card is placed down, not when it is shot. Everlasting doesn't work."
        override val difficultyChange: Float = 0.2f

        override fun behaviours(): List<EncounterBehaviour> = listOf(EncounterBehaviour.Confused)
    }

    abstract val displayName: String
    abstract val iconHandle: ResourceHandle
    abstract val description: String

    open fun behaviours(): List<EncounterBehaviour> = listOf()

    open fun intermediateScreen(): String? = null

    companion object {

        fun getFromName(name: String) = when (name.lowercase()) {
            "rain" -> Rain
            "frost" -> Frost
            "bewitchedmist" -> BewitchedMist
            "steelnerves" -> SteelNerves()
            "lookalike" -> Lookalike
            "moist" -> Moist
            "drawonemorecard" -> DrawOneMoreCard
            "draft" -> Draft
            "anofferyoucantrefuse" -> AnOfferYouCantRefuse
            "bulletskipping" -> BulletSkipping
            "sacrifice" -> Sacrifice
            "sorrynotsorry" -> SorryNotSorry
            "confused" -> Confused
            else -> throw RuntimeException("Unknown Encounter Modifier: $name")
        }

        val blacklist: List<List<String>> by lazy {
            val config = ConfigFileManager.getConfigFile("runGeneratorConfig")
            config
                .get<OnjArray>("encounterModifierBlacklist")
                .value
                .map { pool ->
                    pool as OnjArray
                    pool.value.map { (it.value as String).lowercase() }
                }
        }

        fun isValid(modifiersList: List<String>): Boolean {
            val modifiers = modifiersList.map { it.lowercase() }
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

        fun isBlacklisted(m1: String, m2: String): Boolean {
            blacklist.forEach { pool ->
                if (m1.lowercase() in pool && m2.lowercase() in pool) return true
            }
            return false
        }
    }
}
