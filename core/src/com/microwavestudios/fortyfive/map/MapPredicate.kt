package com.microwavestudios.fortyfive.map

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.card.RandomCardSelection
import onj.builder.buildOnjObject
import onj.value.OnjNamedObject
import onj.value.OnjObject

interface MapPredicate {

    fun check(currentMap: DetailMap): Boolean

    fun asOnj(): OnjObject

    fun getMessage(): String

    class PlayerHasCard(val card: String) : MapPredicate {

        override fun check(currentMap: DetailMap): Boolean {
            val profile = FortyFive.profileManager.currentProfile ?: return false
            val cards = if (profile.isRunActive) profile.backpack!! else profile.cardCollection
            return card !in cards
        }

        override fun asOnj(): OnjObject = buildOnjObject {
            name("PlayerHasCard")
            "card" with card
        }

        override fun getMessage(): String {
            val proto = RandomCardSelection
                .allCardPrototypes
                .find { it.name == card }
                ?: throw RuntimeException("unknown card: $card")
            val name = proto.title
            return "You must have the card: $name"
        }
    }

    class Not(val negate: MapPredicate) : MapPredicate {

        override fun check(currentMap: DetailMap): Boolean = !negate.check(currentMap)

        override fun asOnj(): OnjObject = buildOnjObject {
            name("Not")
            "negate" with negate.asOnj()
        }

        override fun getMessage(): String = "The following must be false: ${negate.getMessage()}"
    }

    companion object {

        fun fromOnj(onj: OnjNamedObject): MapPredicate = when (onj.name) {

            "PlayerHasCard" -> PlayerHasCard(onj.get<String>("card"))

            "Not" -> Not(fromOnj(onj.get<OnjNamedObject>("negate")))

            else -> throw RuntimeException("unknown MapPredicate: ${onj.name}")
        }
    }

}
