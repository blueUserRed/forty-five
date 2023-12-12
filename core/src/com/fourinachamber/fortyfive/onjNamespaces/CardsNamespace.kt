package com.fourinachamber.fortyfive.onjNamespaces

import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.game.card.BulletSelector
import com.fourinachamber.fortyfive.game.card.Effect
import com.fourinachamber.fortyfive.game.card.EffectValue
import com.fourinachamber.fortyfive.game.card.Trigger
import com.fourinachamber.fortyfive.utils.Utils
import com.fourinachamber.fortyfive.utils.toIntRange
import onj.builder.buildOnjObject
import onj.customization.Namespace.*
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
        "Effect" to OnjEffect::class,
        "EffectValue" to OnjEffectValue::class,
        "ActiveChecker" to OnjActiveChecker::class,
    )

    @OnjNamespaceVariables
    val variables: Map<String, OnjObject> = mapOf(
        "value" to buildOnjObject {
            "mostExpensiveBulletInRevolver" with OnjEffectValue { controller ->
                controller
                    .revolver
                    .slots
                    .mapNotNull { it.card }
                    .maxOfOrNull { it.cost }
                    ?: 0
            }
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [string, EffectValue]")
    fun reserveGain(trigger: OnjString, amount: OnjEffectValue): OnjEffect {
        return OnjEffect(Effect.ReserveGain(triggerOrError(trigger.value), amount.value, false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector, EffectValue]")
    fun buffDmg(trigger: OnjString, bulletSelector: OnjBulletSelector, amount: OnjEffectValue): OnjEffect {
        return OnjEffect(
            Effect.BuffDamage(
            triggerOrError(trigger.value),
            amount.value,
            bulletSelector.value,
            false
        ))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector, EffectValue, ActiveChecker]")
    fun buffDmg(
        trigger: OnjString,
        bulletSelector: OnjBulletSelector,
        amount: OnjEffectValue,
        activeChecker: OnjActiveChecker
    ): OnjEffect {
        return OnjEffect(Effect.BuffDamage(
            triggerOrError(trigger.value),
            amount.value,
            bulletSelector.value,
            false,
            activeChecker.value,
        ))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector, EffectValue]")
    fun giftDmg(trigger: OnjString, bulletSelector: OnjBulletSelector, amount: OnjEffectValue): OnjEffect {
        return OnjEffect(
            Effect.GiftDamage(
            triggerOrError(trigger.value),
            amount.value,
            bulletSelector.value,
            false
        ))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, EffectValue]")
    fun draw(trigger: OnjString, amount: OnjEffectValue): OnjEffect {
        return OnjEffect(Effect.Draw(triggerOrError(trigger.value), amount.value, false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, StatusEffect]")
    fun giveStatus(trigger: OnjString, effect: OnjStatusEffect): OnjEffect {
        return OnjEffect(Effect.GiveStatus(triggerOrError(trigger.value), effect.value, false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, StatusEffect]")
    fun givePlayerStatus(trigger: OnjString, effect: OnjStatusEffect): OnjEffect {
        return OnjEffect(Effect.GivePlayerStatus(triggerOrError(trigger.value), effect.value, false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, string, EffectValue]")
    fun putCardInHand(trigger: OnjString, name: OnjString, amount: OnjEffectValue): OnjEffect {
        return OnjEffect(
            Effect.PutCardInHand(
            triggerOrError(trigger.value),
            name.value,
            amount.value,
            false
        ))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector, int]")
    fun protect(trigger: OnjString, bulletSelector: OnjBulletSelector, shots: OnjInt): OnjEffect {
        return OnjEffect(Effect.Protect(
            triggerOrError(trigger.value),
            bulletSelector.value,
            shots.value.toInt(),
            false
        ))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector]")
    fun destroy(trigger: OnjString, bulletSelector: OnjBulletSelector): OnjEffect {
        return OnjEffect(Effect.Destroy(triggerOrError(trigger.value), bulletSelector.value, false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, EffectValue]")
    fun damageDirect(trigger: OnjString, damage: OnjEffectValue): OnjEffect {
        return OnjEffect(Effect.DamageDirectly(triggerOrError(trigger.value), damage.value, false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, EffectValue]")
    fun damagePlayer(trigger: OnjString, damage: OnjEffectValue): OnjEffect {
        return OnjEffect(Effect.DamagePlayer(triggerOrError(trigger.value), damage.value, false))
    }

    @RegisterOnjFunction(schema = "params: [string]")
    fun killPlayer(trigger: OnjString): OnjEffect {
        return OnjEffect(Effect.KillPlayer(triggerOrError(trigger.value), false))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector]")
    fun bounce(trigger: OnjString, bulletSelector: OnjBulletSelector): OnjEffect {
        return OnjEffect(Effect.BounceBullet(triggerOrError(trigger.value), bulletSelector.value, false))
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
                num = Utils.externalToInternalSlotRepresentation(num)
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

    @RegisterOnjFunction(schema = "params: [*, *]")
    fun poison(turns: OnjValue, damage: OnjValue): OnjStatusEffect = OnjStatusEffect {
        Poison(
            getIntParamFromOnj(turns),
            getIntParamFromOnj(damage)
        )
    }

    @RegisterOnjFunction(schema = "params: [*, float, boolean]")
    fun burning(rotations: OnjValue, percent: OnjFloat, isInfinite: OnjBoolean): OnjStatusEffect = OnjStatusEffect {
        Burning(
            getIntParamFromOnj(rotations),
            percent.value.toFloat(),
            isInfinite.value
        )
    }

    @RegisterOnjFunction(schema = "params: [*]")
    fun fireResistance(turns: OnjValue): OnjStatusEffect = OnjStatusEffect {
        FireResistance(getIntParamFromOnj(turns))
    }

    @RegisterOnjFunction(schema = "params: [*, *]")
    fun bewitched(turns: OnjValue, rotations: OnjValue): OnjStatusEffect = OnjStatusEffect {
        Bewitched(
            getIntParamFromOnj(turns),
            getIntParamFromOnj(rotations),
        )
    }

    @RegisterOnjFunction(schema = "params: [int]")
    fun wardOfTheWitch(amount: OnjInt): OnjStatusEffect = OnjStatusEffect {
        WardOfTheWitch(amount.value.toInt())
    }

    @RegisterOnjFunction(schema = "params: [int]")
    fun wrathOfTheWitch(amount: OnjInt): OnjStatusEffect = OnjStatusEffect {
        WrathOfTheWitch(amount.value.toInt())
    }

    @RegisterOnjFunction(schema = "params: [{...*}]")
    fun negatePredicate(predicate: OnjObject): OnjObject = buildOnjObject {
        name("NegatePredicate")
        "value" with predicate
    }

    @RegisterOnjFunction(schema = "params: [int]", type = OnjFunctionType.CONVERSION)
    fun `val`(value: OnjInt): OnjEffectValue = OnjEffectValue { value.value.toInt() }

    @RegisterOnjFunction(schema = "params: [{...*}]", type = OnjFunctionType.CONVERSION)
    fun activeChecker(value: OnjNamedObject): OnjActiveChecker {
        val predicate = GamePredicate.fromOnj(value)
        return OnjActiveChecker { controller -> predicate.check(controller) }
    }

    private fun getIntParamFromOnj(value: OnjValue): Int = when (value) {
        is OnjInt -> value.value.toInt()
        is OnjArray -> value.toIntRange().random()
        else -> throw RuntimeException("expected parameter to be either an int or an array of two ints")
    }

    private fun triggerOrError(trigger: String): Trigger = when (trigger) {
        "enter" -> Trigger.ON_ENTER
        "shot" -> Trigger.ON_SHOT
        "destroy" -> Trigger.ON_DESTROY
        "round start" -> Trigger.ON_ROUND_START
        "round end" -> Trigger.ON_ROUND_END
        "rotation" -> Trigger.ON_REVOLVER_ROTATION
        "card drawn" -> Trigger.ON_CARDS_DRAWN
        "special card drawn" -> Trigger.ON_SPECIAL_CARDS_DRAWN
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

class OnjEffectValue(
    override val value: EffectValue
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--effect-value--'")
    }
}

class OnjActiveChecker(
    override val value: (controller: GameController) -> Boolean
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--active-checker--'")
    }
}
