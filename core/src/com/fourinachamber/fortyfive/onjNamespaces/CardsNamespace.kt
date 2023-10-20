package com.fourinachamber.fortyfive.onjNamespaces

import com.fourinachamber.fortyfive.game.*
import onj.builder.buildOnjObject
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.Namespace.OnjNamespace
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.customization.OnjFunction.RegisterOnjFunction.OnjFunctionType
import onj.value.*
import kotlin.reflect.KClass

@Suppress("unused") // variables and functions are read via reflection
@OnjNamespace
object CardsNamespace { // TODO: something like GameNamespace would be a more accurate name

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "BulletSelector" to OnjBulletSelector::class,
        "StatusEffect" to OnjStatusEffect::class,
        "Effect" to OnjEffect::class
    )

    @RegisterOnjFunction(schema = "params: [string, int]")
    fun reserveGain(trigger: OnjString, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.ReserveGain(triggerOrError(trigger.value), amount.value.toInt(), false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector, int]")
    fun buffDmg(trigger: OnjString, bulletSelector: OnjBulletSelector, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.BuffDamage(
            triggerOrError(trigger.value),
            amount.value.toInt(),
            bulletSelector.value,
            false
        ))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector, int]")
    fun giftDmg(trigger: OnjString, bulletSelector: OnjBulletSelector, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.GiftDamage(
            triggerOrError(trigger.value),
            amount.value.toInt(),
            bulletSelector.value,
            false
        ))
    }

    @RegisterOnjFunction(schema = "params: [string, int]")
    fun draw(trigger: OnjString, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.Draw(triggerOrError(trigger.value), amount.value.toInt(), false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, StatusEffect]")
    fun giveStatus(trigger: OnjString, effect: OnjStatusEffect): OnjEffect {
        return OnjEffect(Effect.GiveStatus(triggerOrError(trigger.value), effect.value, false))
    }

    @RegisterOnjFunction(schema = "params: [string, string, int]")
    fun putCardInHand(trigger: OnjString, name: OnjString, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.PutCardInHand(
            triggerOrError(trigger.value),
            name.value,
            amount.value.toInt(),
            false
        ))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector]")
    fun protect(trigger: OnjString, bulletSelector: OnjBulletSelector): OnjEffect {
        return OnjEffect(Effect.Protect(triggerOrError(trigger.value), bulletSelector.value, false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector]")
    fun destroy(trigger: OnjString, bulletSelector: OnjBulletSelector): OnjEffect {
        return OnjEffect(Effect.Destroy(triggerOrError(trigger.value), bulletSelector.value, false))
    }

    @RegisterOnjFunction(schema = "params: [string, int]")
    fun damageDirect(trigger: OnjString, damage: OnjInt): OnjEffect {
        return OnjEffect(Effect.DamageDirectly(triggerOrError(trigger.value), damage.value.toInt(), false))
    }

    @RegisterOnjFunction(schema = "params: [string, int]")
    fun damagePlayer(trigger: OnjString, damage: OnjInt): OnjEffect {
        return OnjEffect(Effect.DamagePlayer(triggerOrError(trigger.value), damage.value.toInt(), false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [Effect]", type = OnjFunctionType.CONVERSION)
    fun canTriggerInHand(effect: OnjEffect): OnjEffect {
        return OnjEffect(effect.value.copy().apply { triggerInHand = true })
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
                nums.add(num - 1)
            }
            is OnjString -> {
                if (value.value.lowercase() != "this") {
                    throw RuntimeException("string '${value.value}' not allowed in bNum")
                }
                allowSelf = true
            }
            else -> throw RuntimeException("bNum only allows ints or strings!")
        }


        return OnjBulletSelector(BulletSelector.ByLambda { self, other, slot, _ ->
            // when self === other allowSelf must be true, even if the slot is correct
            if (self === other) allowSelf
            else nums.contains(slot)
        })
    }

    @RegisterOnjFunction(schema = "params: [string]")
    fun bSelectByName(name: OnjString): OnjBulletSelector {
        return OnjBulletSelector(BulletSelector.ByLambda { _, other, _, _ -> other.name == name.value })
    }

    @RegisterOnjFunction(schema = "params: [boolean, boolean]")
    fun bSelectTarget(includeSelf: OnjBoolean, optional: OnjBoolean): OnjBulletSelector {
        return OnjBulletSelector(BulletSelector.ByPopup(includeSelf.value, optional.value))
    }

    @RegisterOnjFunction("params: []")
    fun bSelectNeighbors(): OnjBulletSelector {
        return OnjBulletSelector(BulletSelector.ByLambda { self, _, slot, controller ->
            val thisSlot = controller.revolver.slots.indexOfFirst { it.card === self }
            val neighbors = arrayOf(
                if (thisSlot == 4) 0 else thisSlot + 1,
                if (thisSlot == 0) 4 else thisSlot - 1
            )
            return@ByLambda slot in neighbors
        })
    }

    @RegisterOnjFunction("params: []")
    fun bSelectSelf(): OnjBulletSelector {
        return OnjBulletSelector(BulletSelector.ByLambda { self, other, _, _ -> self === other })
    }

    @RegisterOnjFunction(schema = "params: [int, int]")
    fun poison(turns: OnjInt, damage: OnjInt): OnjStatusEffect = OnjStatusEffect {
        Poison(
            turns.value.toInt(),
            damage.value.toInt()
        )
    }

    @RegisterOnjFunction(schema = "params: [int?, float]")
    fun burning(rotations: OnjValue, percent: OnjFloat): OnjStatusEffect = OnjStatusEffect {
        Burning(
            if (rotations.isInt()) (rotations.value as Long).toInt() else 0,
            percent.value.toFloat(),
            rotations.isNull()
        )
    }

    @RegisterOnjFunction(schema = "params: [int]")
    fun fireResistance(turns: OnjInt): OnjStatusEffect = OnjStatusEffect {
        FireResistance(turns.value.toInt())
    }

    @RegisterOnjFunction(schema = "params: [int, int]")
    fun bewitched(turns: OnjInt, rotations: OnjInt): OnjStatusEffect = OnjStatusEffect {
        Bewitched(
            turns.value.toInt(),
            rotations.value.toInt(),
        )
    }

    @RegisterOnjFunction(schema = "params: [{...*}]")
    fun negatePredicate(predicate: OnjObject): OnjObject = buildOnjObject {
        name("NegatePredicate")
        "value" with predicate
    }

    private fun triggerOrError(trigger: String): Trigger = when (trigger) {
        "enter" -> Trigger.ON_ENTER
        "shot" -> Trigger.ON_SHOT
        "destroy" -> Trigger.ON_DESTROY
        "round start" -> Trigger.ON_ROUND_START
        "round end" -> Trigger.ON_ROUND_END
        "rotation" -> Trigger.ON_REVOLVER_ROTATION
        "card drawn" -> Trigger.ON_CARDS_DRAWN
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
    override val value: StatusEffectCreator
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--status-effect--'")
    }

}
