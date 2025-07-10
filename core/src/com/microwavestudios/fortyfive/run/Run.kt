package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.map.generation.BaseMapGenerator
import onj.builder.buildOnjObject
import onj.value.*

data class Run(
    val length: RunLength,
    val type: RunType,
    val difficulty: Int,
    val rewards: List<RunReward>,
    val biome: String,
    val mapGenerator: BaseMapGenerator
) {

    fun asOnj(): OnjObject = buildOnjObject {
        "length" with length.asOnj()
        "type" with type.asOnj()
        "difficulty" with difficulty
        "rewards" with rewards.map { it.asOnj() }
        "biome" with biome
        "mapGenerator" with mapGenerator.asOnj()
    }

    companion object {

        fun fromOnj(onj: OnjObject): Run = Run(
            RunLength.fromOnj(onj.get<OnjString>("length")),
            RunType.fromOnj(onj.get<OnjString>("type")),
            onj.get<Long>("difficulty").toInt(),
            onj.get<OnjArray>("rewards").value.map {
                it as OnjNamedObject
                RunReward.fromOnj(it)
            },
            onj.get<String>("biome"),
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
    PROGRESS("progress", "Progress"),
    CONSTRUCTED("constructed", "Constructed")
    ;

    fun asOnj(): OnjValue = OnjString(onjName)

    companion object {

        fun fromOnj(onj: OnjString): RunType = when (onj.value) {
            "limited" -> LIMITED
            "progress" -> PROGRESS
            "constructed" -> CONSTRUCTED
            else -> throw RuntimeException("unknown runtype: ${onj.value}")
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
