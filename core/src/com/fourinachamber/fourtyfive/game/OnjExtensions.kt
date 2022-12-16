package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import onj.*

object OnjExtensions {

    private val customFunctions: Array<OnjFunction> = arrayOf(

        OnjFunction("reserveGain", listOf(OnjString::class, OnjInt::class)) {
            OnjEffect(Effect.ReserveGain(triggerOrError(it[0].value as String), (it[1].value as Long).toInt()))
        },

        OnjFunction("buffDmg", listOf(OnjString::class, OnjBulletSelector::class, OnjInt::class)) {
            OnjEffect(Effect.BuffDamage(
                triggerOrError(it[0].value as String),
                (it[2].value as Long).toInt(),
                (it[1] as OnjBulletSelector).value
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

        OnjFunction("destroy", listOf(OnjString::class)) {
            OnjEffect(Effect.Destroy(triggerOrError(it[0].value as String)))
        },

        OnjFunction("putCardInHand", listOf(OnjString::class, OnjString::class)) {
            OnjEffect(Effect.PutCardInHand(
                triggerOrError(it[0].value as String),
                it[1].value as String,
                (it[2] as OnjInt).value.toInt(),
            ))
        },

        /////////////////////////////////////////////////////////////////////////

        OnjFunction("bNum", listOf(OnjArray::class)) { params ->
            val onjArr = params[0] as OnjArray
            val nums = mutableSetOf<Int>()
            var allowSelf = false

            for (value in onjArr.value) when (value) {
                is OnjInt -> {
                    var num = value.value.toInt()
                    // convert slot from external representation (1 comes after 5)
                    // to internal representation (4 comes after 5)
                    num = if (num == 5) 5 else 5 - num
                    nums.add(num)
                }
                is OnjString -> {
                    if (value.value.lowercase() != "this") {
                        throw RuntimeException("string '${value.value}' not allowed in bNum")
                    }
                    allowSelf = true
                }
                else -> throw RuntimeException("bNum only allows ints or strings!")
            }


            OnjBulletSelector { self, other, slot ->
                // when self === other allowSelf must be true, even if the slot is correct
                if (self === other) allowSelf
                else nums.contains(slot)
            }
        },

        OnjFunction("bSelectByName", listOf(OnjString::class)) {
            val name = it[0].value as String
            OnjBulletSelector { _, other, _ -> other.name == name }
        },

        /////////////////////////////////////////////////////////////////////////

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
        },

        /////////////////////////////////////////////////////////////////////////

        OnjFunction("color", listOf(OnjString::class)) {
            OnjColor(Color.valueOf(it[0].value as String))
        }

    )


    fun init() {
        for (func in customFunctions) OnjConfig.addFunction(func)

        OnjConfig.addCustomDataType("Effect", OnjEffect::class)
        OnjConfig.addCustomDataType("StatusEffect", OnjStatusEffect::class)
        OnjConfig.addCustomDataType("Color", OnjStatusEffect::class)
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
    class OnjBulletSelector(
        override val value: BulletSelector
    ) : OnjValue() {

        override fun toString(): String = "'__bullet-selector__'"
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

    //TODO: swap all usages Color.valueOf with this
    class OnjColor(
        override val value: Color
    ) : OnjValue() {

        override fun toString(): String = "color($value)"
        override fun toString(indentationLevel: Int): String = toString()
        override fun toJsonString(): String = toString()
        override fun toJsonString(indentationLevel: Int): String = toString()

    }

}