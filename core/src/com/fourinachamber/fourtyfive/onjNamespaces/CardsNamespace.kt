package com.fourinachamber.fourtyfive.onjNamespaces

import com.fourinachamber.fourtyfive.game.BulletSelector
import com.fourinachamber.fourtyfive.game.Effect
import com.fourinachamber.fourtyfive.game.StatusEffect
import com.fourinachamber.fourtyfive.game.Trigger
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.Namespace.OnjNamespace
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.value.*
import kotlin.reflect.KClass

@Suppress("unused") // variables and functions are read via reflection
@OnjNamespace
object CardsNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "BulletSelector" to OnjBulletSelector::class,
        "StatusEffect" to OnjStatusEffect::class,
        "Effect" to OnjEffect::class
    )

    @RegisterOnjFunction(schema = "params: [string, int]")
    fun reserveGain(trigger: OnjString, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.ReserveGain(triggerOrError(trigger.value), amount.value.toInt()))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector, int]")
    fun buffDmg(trigger: OnjString, bulletSelector: OnjBulletSelector, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.BuffDamage(triggerOrError(trigger.value), amount.value.toInt(), bulletSelector.value))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector, int]")
    fun giftDmg(trigger: OnjString, bulletSelector: OnjBulletSelector, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.GiftDamage(triggerOrError(trigger.value), amount.value.toInt(), bulletSelector.value))
    }

    @RegisterOnjFunction(schema = "params: [string, int]")
    fun draw(trigger: OnjString, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.Draw(triggerOrError(trigger.value), amount.value.toInt()))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, StatusEffect]")
    fun giveStatus(trigger: OnjString, effect: OnjStatusEffect): OnjEffect {
        return OnjEffect(Effect.GiveStatus(triggerOrError(trigger.value), effect.value))
    }

    @RegisterOnjFunction(schema = "params: [string]")
    fun destroy(trigger: OnjString): OnjEffect {
        return OnjEffect(Effect.Destroy(triggerOrError(trigger.value)))
    }

    @RegisterOnjFunction(schema = "params: [string, string, int]")
    fun putCardInHand(trigger: OnjString, name: OnjString, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.PutCardInHand(triggerOrError(trigger.value), name.value, amount.value.toInt()))
    }

    @RegisterOnjFunction(schema = "params: [*[]]")
    fun bNum(onjArr: OnjArray): OnjBulletSelector {
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


        return OnjBulletSelector { self, other, slot ->
            // when self === other allowSelf must be true, even if the slot is correct
            if (self === other) allowSelf
            else nums.contains(slot)
        }
    }

    @RegisterOnjFunction(schema = "params: [string]")
    fun bSelectByName(name: OnjString): OnjBulletSelector {
        return OnjBulletSelector { _, other, _ -> other.name == name.value }
    }

    @RegisterOnjFunction(schema = "params: [int, int]")
    fun poison(turns: OnjInt, damage: OnjInt): OnjStatusEffect {
        return OnjStatusEffect(StatusEffect.Poison(
            damage.value.toInt(),
            turns.value.toInt(),
            StatusEffect.StatusEffectTarget.ENEMY
        ))
    }

    @RegisterOnjFunction(schema = "params: [int, float]")
    fun burning(turns: OnjInt, percent: OnjFloat): OnjStatusEffect {
        return OnjStatusEffect(StatusEffect.Burning(
            turns.value.toInt(),
            percent.value.toFloat(),
            StatusEffect.StatusEffectTarget.ENEMY
        ))
    }


    private fun triggerOrError(trigger: String): Trigger = when (trigger) {
        "enter" -> Trigger.ON_ENTER
        "shot" -> Trigger.ON_SHOT
        "destroy" -> Trigger.ON_DESTROY
        "round start" -> Trigger.ON_ROUND_START
        else -> throw RuntimeException("unknown trigger: $trigger")
    }

}

/**
 * an Effect that can be applied to a card that was read from an onj file
 */
class OnjEffect(
    override val value: Effect
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--effect--'")
    }
}

/**
 * a bullet-selector that was read from an onj file
 * @see BulletSelector
 */
class OnjBulletSelector(
    override val value: BulletSelector
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--bullet-selector--'")
    }

}

/**
 * a status effect that was read from an onj file
 */
class OnjStatusEffect(
    override val value: StatusEffect
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--status-effect--'")
    }

}
