package com.microwavestudios.fortyfive.map

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.card.RandomCardSelection
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.math.min

interface MapPredicate {

    fun check(currentMap: DetailMap, thisEvent: MapEvent? = null): Boolean

    fun asOnj(): OnjObject

    class PlayerHasCard(val card: CardType) : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean {
            val profile = FortyFive.profileManager.currentProfile ?: return false
            val cards = if (profile.isRunActive) profile.backpack!! else profile.cardCollection
            return card !in cards
        }

        override fun asOnj(): OnjObject = buildOnjObject {
            name("PlayerHasCard")
            "card" with card
        }
    }

    class RunCompleted(val runName: String) : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean {
            val profile = FortyFive.profileManager.currentProfile ?: return false
            return profile.isSpecialRunCompleted(runName)
        }

        override fun asOnj(): OnjObject = buildOnjObject {
            name("RunCompleted")
            "runName" with runName
        }

    }

    class RunInCurrentBoard(val runName: String) : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean {
            val profile = FortyFive.profileManager.currentProfile ?: return false
            val runBoard = profile.runBoardForArea(profile.currentAreaMap)
            return runBoard.progressRun?.name == runName ||
                    runBoard.specialRuns.any { it.name == runName }
        }

        override fun asOnj(): OnjObject = buildOnjObject {
            name("RunInCurrentBoard")
            "runName" with runName
        }

    }

    class MinimumRunsWon(val minimum: Int) : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean {
            val profile = FortyFive.profileManager.currentProfile ?: return false
            return profile.wonRuns >= minimum
        }

        override fun asOnj(): OnjObject = buildOnjObject {
            name("MinimumRunsWon")
            "minimum" with minimum
        }
    }

    object MinStepsReached : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean {
            val profile = FortyFive.profileManager.currentProfile ?: return false
            val run = profile.activeRun ?: return false
            return (profile.usedSteps ?: 0) >= run.minSteps
        }

        override fun asOnj(): OnjObject = buildOnjObject {
            name("MinStepsReached")
        }
    }

    object CurrentNodeBlocks : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean {
            val profile = FortyFive.profileManager.currentProfile ?: return false
            val mapSaver = profile.currentMapSaver
            return thisEvent?.isBlocking(mapSaver.currentMap) ?: false
        }

        override fun asOnj(): OnjObject = buildOnjObject {
            name("CurrentNodeBlocks")
        }
    }

    object CurrentNodeCompleted : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean {
            return thisEvent?.isCompleted ?: false
        }

        override fun asOnj(): OnjObject = buildOnjObject {
            name("CurrentNodeCompleted")
        }
    }

    class Not(val negate: MapPredicate) : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean = !negate.check(currentMap, thisEvent)

        override fun asOnj(): OnjObject = buildOnjObject {
            name("Not")
            "negate" with negate.asOnj()
        }
    }

    class Or(val predicates: List<MapPredicate>) : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean = predicates.any { it.check(currentMap, thisEvent) }

        override fun asOnj(): OnjObject = buildOnjObject {
            name("Or")
            "predicates" with predicates.map { it.asOnj() }
        }
    }

    class And(val predicates: List<MapPredicate>) : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean = predicates.all { it.check(currentMap, thisEvent) }

        override fun asOnj(): OnjObject = buildOnjObject {
            name("And")
            "predicates" with predicates.map { it.asOnj() }
        }
    }

    object Never : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean = false

        override fun asOnj(): OnjObject = buildOnjObject {
            name("Never")
        }
    }

    object Always : MapPredicate {

        override fun check(currentMap: DetailMap, thisEvent: MapEvent?): Boolean = true

        override fun asOnj(): OnjObject = buildOnjObject {
            name("Always")
        }
    }

    companion object {

        fun fromOnj(onj: OnjNamedObject): MapPredicate = when (onj.name) {

            "PlayerHasCard" -> PlayerHasCard(CardType.fromOnj(onj.get<OnjObject>("card")))

            "RunCompleted" -> RunCompleted(onj.get<String>("runName"))

            "MinimumRunsWon" -> MinimumRunsWon(onj.get<Long>("minimum").toInt())

            "RunInCurrentBoard" -> RunInCurrentBoard(onj.get<String>("runName"))

            "MinStepsReached" -> MinStepsReached

            "CurrentNodeCompleted" -> CurrentNodeCompleted

            "CurrentNodeBlocks" -> CurrentNodeBlocks

            "Not" -> Not(fromOnj(onj.get<OnjNamedObject>("negate")))

            "Or" -> Or(onj.get<OnjArray>("predicates").value.map { fromOnj(it as OnjNamedObject) })

            "And" -> And(onj.get<OnjArray>("predicates").value.map { fromOnj(it as OnjNamedObject) })

            "Never" -> Never

            "Always" -> Always

            else -> throw RuntimeException("unknown MapPredicate: ${onj.name}")
        }
    }

}
