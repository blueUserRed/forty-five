package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.utils.requireNot
import onj.builder.buildOnjObject
import onj.value.OnjNamedObject
import onj.value.OnjObject

abstract class RunChallenge {

    class ReduceStepAmount(val reduceBy: Int) : RunChallenge() {

        override val description: String = "The maximum step amount is reduced by $reduceBy"

        override fun asOnj(): OnjObject = buildOnjObject {
            name("ReduceStepAmount")
            "reduceBy" with reduceBy
        }
    }

    abstract val description: String

    open fun behaviours(): List<RunBehaviour> = listOf()

    abstract fun asOnj(): OnjObject

}

object RunChallengeFactory {

    private val creator: MutableMap<String, (onj: OnjObject) -> RunChallenge> = mutableMapOf()

    init {
        registerRunChallenge("ReduceStepAmount") { obj ->
            RunChallenge.ReduceStepAmount(obj.get<Long>("reduceBy").toInt())
        }
    }

    fun registerRunChallenge(name: String, challengeCreator: (onj: OnjObject) -> RunChallenge) {
        requireNot(creator.containsKey(name)) { "Run Challenge $name already exists" }
        creator[name] = challengeCreator
    }

    fun get(onj: OnjNamedObject): RunChallenge {
        val creator = creator[onj.name]
        requireNotNull(creator) { "No Run Challenge with name ${onj.name}" }
        return creator(onj)
    }

}
