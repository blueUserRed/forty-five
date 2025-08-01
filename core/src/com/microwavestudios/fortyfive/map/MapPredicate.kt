package com.microwavestudios.fortyfive.map

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.card.RandomCardSelection
import onj.builder.buildOnjObject
import onj.value.OnjNamedObject
import onj.value.OnjObject

interface MapPredicate {

    fun check(currentMap: DetailMap): Boolean

    fun asOnj(): OnjObject

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
    }

    class Not(val negate: MapPredicate) : MapPredicate {

        override fun check(currentMap: DetailMap): Boolean = !negate.check(currentMap)

        override fun asOnj(): OnjObject = buildOnjObject {
            name("Not")
            "negate" with negate.asOnj()
        }
    }

    object ProgressRunCompleted : MapPredicate {

        override fun check(currentMap: DetailMap): Boolean = currentMap.completedProgressRun

        override fun asOnj(): OnjObject = buildOnjObject {
            name("ProgressRunCompleted")
        }
    }

    object Never : MapPredicate {

        override fun check(currentMap: DetailMap): Boolean = false

        override fun asOnj(): OnjObject = buildOnjObject {
            name("Never")
        }
    }

    companion object {

        fun fromOnj(onj: OnjNamedObject): MapPredicate = when (onj.name) {

            "PlayerHasCard" -> PlayerHasCard(onj.get<String>("card"))

            "Not" -> Not(fromOnj(onj.get<OnjNamedObject>("negate")))

            "Never" -> Never

            "ProgressRunCompleted" -> ProgressRunCompleted

            else -> throw RuntimeException("unknown MapPredicate: ${onj.name}")
        }
    }

}
