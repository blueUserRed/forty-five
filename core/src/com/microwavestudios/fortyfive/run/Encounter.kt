package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.EncounterModifier
import com.microwavestudios.fortyfive.game.UserPrefs
import com.microwavestudios.fortyfive.game.enemy.Enemy
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjObject

data class Encounter(
    val enemies: List<String>,
    val encounterModifierNames: Set<String>,
    val forceCards: List<String>?,
    val shuffleCards: Boolean,
    val special: Boolean,
) {

    val encounterModifier: List<EncounterModifier>
        get() = encounterModifierNames
            .map { EncounterModifier.getFromName(it) }
            .filter { !UserPrefs.disableRtMechanics || !it.isRtBased }

    fun createEnemies(): List<Enemy> {
        val enemiesOnj = ConfigFileManager.getConfigFile("enemies")
        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
        return enemies
            .map { enemy -> enemyPrototypes.find { it.name == enemy } ?: throw RuntimeException("unknown enemy $enemy") }
            .map { it.create(it.baseHealth) }
    }

    fun asOnj(): OnjObject = buildOnjObject {
        "enemies" with enemies
        "encounterModifier" with encounterModifierNames
        "forceCards" with forceCards
        "shuffleCards" with shuffleCards
        "special" with special
    }

    companion object {

        fun fromOnj(onj: OnjObject): Encounter = Encounter(
            onj.get<OnjArray>("enemies").value.map { it.value as String },
            onj.get<OnjArray>("encounterModifier").value.map { it.value as String }.toSet(),
            onj.getOr<OnjArray?>("forceCards", null)?.value?.map { it.value as String },
            onj.getOr("shuffleCards", true),
            onj.getOr("special", false),
        )
    }

}