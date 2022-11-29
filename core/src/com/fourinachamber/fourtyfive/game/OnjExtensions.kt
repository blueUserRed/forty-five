package com.fourinachamber.fourtyfive.game

import onj.*

object OnjExtensions {

    private val customFunctions: Array<OnjFunction> = arrayOf(

        OnjFunction("reserveGain", listOf(OnjString::class, OnjInt::class)) {
            OnjEffect(Effect.ReserveGain(triggerOrError(it[0].value as String), (it[1].value as Long).toInt()))
        },

        OnjFunction("buffDmg", listOf(OnjString::class, OnjInt::class)) {
            OnjEffect(Effect.BuffDamage(
                triggerOrError(it[0].value as String),
                (it[1].value as Long).toInt()
            ))
        },

        OnjFunction("draw", listOf(OnjString::class, OnjInt::class)) {
            OnjEffect(Effect.Draw(
                triggerOrError(it[0].value as String),
                (it[1].value as Long).toInt()
            ))
        }

    )


    fun init() {
        for (func in customFunctions) OnjConfig.addFunction(func)

        OnjConfig.addCustomDataType("Effect", OnjEffect::class)
    }

    private fun triggerOrError(trigger: String): Trigger = when (trigger) {
        "enter" -> Trigger.ON_ENTER
        "shot" -> Trigger.ON_SHOT
        "round start" -> Trigger.ON_ROUND_START
        else -> throw RuntimeException("unknown trigger: $trigger")
    }

//    private fun bNumArray(onj: OnjArray): Array<Int> {
//        return onj
//            .value
//            .map {
//                if (it !is OnjInt) throw RuntimeException("only ints are allowed in a bNum Array!")
//                it.value.toInt()
//            }
//            .onEach {
//                if (it !in -1..5) {
//                    throw RuntimeException("only numbers between -1 and 5 are allowed in a bNum Array")
//                }
//            }
//            .toTypedArray()
//    }

    class OnjEffect(
        override val value: Effect
    ) : OnjValue() {

        override fun toString(): String = "'__effect__'"
        override fun toString(indentationLevel: Int): String = toString()
        override fun toJsonString(): String = toString()
        override fun toJsonString(indentationLevel: Int): String = toString()

    }

}