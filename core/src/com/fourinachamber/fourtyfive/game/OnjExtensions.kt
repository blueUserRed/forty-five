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
        },

        OnjFunction("giveStatus", listOf(OnjString::class, OnjStatusEffect::class)) {
            OnjEffect(Effect.GiveStatus(
                triggerOrError(it[0].value as String),
                it[1].value as StatusEffect
            ))
        },


        OnjFunction("poison", listOf(OnjInt::class, OnjInt::class)) {
            OnjStatusEffect(StatusEffect.Poison(
                (it[1].value as Long).toInt(),
                (it[0].value as Long).toInt(),
                StatusEffect.StatusEffectTarget.ENEMY
            ))
        },

        OnjFunction("burning", listOf(OnjInt::class, OnjFloat::class)) {
            OnjStatusEffect(StatusEffect.Burning(
                (it[0].value as Long).toInt(),
                (it[1].value as Double).toFloat(),
                StatusEffect.StatusEffectTarget.ENEMY
            ))
        }

    )


    fun init() {
        for (func in customFunctions) OnjConfig.addFunction(func)

        OnjConfig.addCustomDataType("Effect", OnjEffect::class)
        OnjConfig.addCustomDataType("StatusEffect", OnjStatusEffect::class)
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

    class OnjStatusEffect(
        override val value: StatusEffect
    ) : OnjValue() {

        override fun toString(): String = "'__status-effect__'"
        override fun toString(indentationLevel: Int): String = toString()
        override fun toJsonString(): String = toString()
        override fun toJsonString(indentationLevel: Int): String = toString()

    }

}