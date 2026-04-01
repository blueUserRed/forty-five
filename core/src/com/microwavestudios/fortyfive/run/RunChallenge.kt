package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.utils.requireNot
import onj.builder.buildOnjObject
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.text.NumberFormat

abstract class RunChallenge {

    class ReduceStepAmount(val reduceBy: Int) : RunChallenge() {

        override val description: String = "The maximum step amount is reduced by $reduceBy"

        override fun asOnj(): OnjObject = buildOnjObject {
            name("ReduceStepAmount")
            "reduceBy" with reduceBy
        }

        override fun behaviours(): List<RunBehaviour> = listOf(RunBehaviour.ModifySteps(-reduceBy))
    }

    class PriceIncrease(val multiplier: Double) : RunChallenge() {

        override val description: String

        init {
            val numberFormat = NumberFormat.getInstance()
            numberFormat.maximumFractionDigits = 1
            val num = numberFormat.format((multiplier * 100) - 100)
            description = "Prices increase by $num%"
        }

        override fun behaviours(): List<RunBehaviour> = listOf(
            RunBehaviour.PriceChange(multiplier)
        )

        override fun asOnj(): OnjObject = buildOnjObject {
            name("PriceIncrease")
            "multiplier" with multiplier
        }
    }

    class DifficultyIncrease(val increase: Float) : RunChallenge() {

        override val description: String = "The difficulty of enemies increases"

        override fun behaviours(): List<RunBehaviour> = listOf(RunBehaviour.DifficultyChange(increase))

        override fun asOnj(): OnjObject = buildOnjObject {
            name("DifficultyIncrease")
            "increase" with increase
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
        registerRunChallenge("PriceIncrease") { obj ->
            RunChallenge.PriceIncrease(obj.get<Double>("multiplier"))
        }
        registerRunChallenge("DifficultyIncrease") { obj ->
            RunChallenge.DifficultyIncrease(obj.get<Double>("increase").toFloat())
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
