package com.fourinachamber.fourtyfive.game

import onj.*

object OnjExtensions {

    private val customFunctions: Array<OnjFunction> = arrayOf(

        OnjFunction("reserveGain", listOf(OnjString::class, OnjInt::class)) {
            OnjEffect(Effect.ReserveGain(triggerOrError(it[0].value as String), (it[1].value as Long).toInt()))
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

    class OnjEffect(
        override val value: Effect
    ) : OnjValue() {

        override fun toString(): String = "'__effect__'"
        override fun toString(indentationLevel: Int): String = toString()
        override fun toJsonString(): String = toString()
        override fun toJsonString(indentationLevel: Int): String = toString()

    }

}