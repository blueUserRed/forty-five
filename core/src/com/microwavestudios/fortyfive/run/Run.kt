package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.map.generation.BaseMapGenerator
import onj.builder.buildOnjObject
import onj.value.*
import kotlin.math.pow

data class Run(
    val name: String,
    val length: RunLength,
    val type: RunType,
    val difficulty: Int,
    val minSteps: Int,
    val maxSteps: Int,
    val modifiers: List<RunModifier>,
    val rewards: List<RunReward>,
    val biome: String,
    val fromArea: String,
    val maxPlayerHealth: Int,
    val initialPlayerHealth: Int,
    val mapGenerator: BaseMapGenerator
) {

    val behaviours: List<RunBehaviour> by lazy {
        accumulateBehaviours(modifiers, type, difficulty)
    }

    fun asOnj(): OnjObject = buildOnjObject {
        "name" with name
        "length" with length.asOnj()
        "type" with type.asOnj()
        "difficulty" with difficulty
        "modifiers" with modifiers.map { it.name() }
        "rewards" with rewards.map { it.asOnj() }
        "biome" with biome
        "minSteps" with minSteps
        "maxSteps" with maxSteps
        "fromArea" with fromArea
        "maxPlayerLives" with maxPlayerHealth
        "initialPlayerLives" with initialPlayerHealth
        "mapGenerator" with mapGenerator.asOnj()
    }

    companion object {

        fun accumulateBehaviours(
            modifiers: List<RunModifier>,
            runType: RunType,
            difficulty: Int
        ): List<RunBehaviour> {
            return modifiers.flatMap { it.behaviours }
        }

        fun fromOnj(onj: OnjObject): Run = Run(
            onj.get<String>("name"),
            RunLength.fromOnj(onj.get<OnjString>("length")),
            RunType.fromOnj(onj.get<OnjString>("type")),
            onj.get<Long>("difficulty").toInt(),
            onj.get<Long>("minSteps").toInt(),
            onj.get<Long>("maxSteps").toInt(),
            onj.get<OnjArray>("modifiers").value.map { RunModifier.get(it.value as String) },
            onj.get<OnjArray>("rewards").value.map {
                it as OnjNamedObject
                RunReward.fromOnj(it)
            },
            onj.get<String>("biome"),
            onj.get<String>("fromArea"),
            onj.get<Long>("maxPlayerLives").toInt(),
            onj.get<Long>("initialPlayerLives").toInt(),
            BaseMapGenerator.fromOnj(onj.get<OnjNamedObject>("mapGenerator"))
        )
    }
}

enum class RunLength(private val onjName: String, val displayName: String) {

    SHORT("short", "Short"),
    MEDIUM("medium", "Medium"),
    LONG("long", "Long")
    ;

    fun asOnj(): OnjValue = OnjString(onjName)

    companion object {

        fun fromOnj(onj: OnjString): RunLength = when (onj.value) {
            "short" -> SHORT
            "medium" -> MEDIUM
            "long" -> LONG
            else -> throw RuntimeException("unknown runlength: ${onj.value}")
        }
    }
}

enum class RunType(private val onjName: String, val displayName: String) {

    LIMITED("limited", "Limited"),
    CONSTRUCTED("constructed", "Constructed"),
    PROGRESS("progress", "Progress"),
    SPECIAL("special", "Special"),
    SPECIAL_NOT_IN_BOARD("special_not_in_board", "Special")
    ;

    fun asOnj(): OnjValue = OnjString(onjName)

    companion object {

        fun fromOnj(onj: OnjString): RunType = when (onj.value) {
            "limited" -> LIMITED
            "progress" -> PROGRESS
            "special" -> SPECIAL
            "special_not_in_board" -> SPECIAL_NOT_IN_BOARD
            "constructed" -> CONSTRUCTED
            else -> throw RuntimeException("unknown runtype: ${onj.value}")
        }
    }
}

abstract class DifficultyScaling {

    data object Linear : DifficultyScaling() {

        override fun scale(min: Float, max: Float, percent: Float): Float {
            return (max - min) * percent + min
        }

        override fun toOnj(): OnjObject = buildOnjObject {
            name("LinearScaling")
        }
    }

    data class Power(val power: Int) : DifficultyScaling() {

        override fun scale(min: Float, max: Float, percent: Float): Float {
            val adjPercent = percent.pow(power)
            return (max - min) * adjPercent + min
        }

        override fun toOnj(): OnjObject = buildOnjObject {
            name("PowerScaling")
            "power" with power
        }

    }

    abstract fun scale(min: Float, max: Float, percent: Float): Float
    abstract fun toOnj(): OnjObject

    companion object {
        fun fromOnj(onj: OnjNamedObject): DifficultyScaling = when (onj.name) {
            "LinearScaling" -> Linear
            "PowerScaling" -> Power(onj.get<Long>("power").toInt())
            else -> throw RuntimeException("no difficulty scaling with name: ${onj.name}")
        }
    }
}

sealed class RunReward {

    data class Cash(val amount: Int) : RunReward() {

        override fun displayText(): String = "Cash: $amount$"

        override fun asOnj(): OnjValue = buildOnjObject {
            name("CashReward")
            "amount" with amount
        }
    }


    abstract fun displayText(): String

    abstract fun asOnj(): OnjValue

    companion object {

        fun fromOnj(onj: OnjNamedObject): RunReward = when (onj.name) {
            "CashReward" -> Cash(onj.get<Long>("amount").toInt())
            else -> throw RuntimeException("unknown RunReward: ${onj.name}")
        }
    }

}
