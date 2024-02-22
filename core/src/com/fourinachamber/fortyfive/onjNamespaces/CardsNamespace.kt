package com.fourinachamber.fortyfive.onjNamespaces

import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.game.card.*
import com.fourinachamber.fortyfive.utils.Utils
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
            "mostExpensiveBulletInRevolver" with OnjEffectValue { controller, _ ->
                controller
                    .revolver
                    .slots
                    .mapNotNull { it.card }
                    .maxOfOrNull { it.cost }
                    ?: 0
            }
            "rotationAmount" with OnjEffectValue { _, card -> card!!.rotationCounter }
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

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector, float]")
    fun buffDmgMultiplier(trigger: OnjString, bulletSelector: OnjBulletSelector, amount: OnjFloat): OnjEffect {
        return OnjEffect(Effect.BuffDamageMultiplier(
            triggerOrError(trigger.value),
            amount.value.toFloat(),
            bulletSelector.value,
            false
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

    @RegisterOnjFunction(schema = "use Cards; params: [string, BulletSelector]")
    fun destroyTargetOrDestroySelf(trigger: OnjString, bulletSelector: OnjBulletSelector): OnjEffect {
        return OnjEffect(Effect.DestroyTargetOrDestroySelf(
            triggerOrError(trigger.value),
            bulletSelector.value,
            false
        ))
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

    @RegisterOnjFunction(schema = "use Cards; params: [string, string, int]")
    fun turnRevolver(trigger: OnjString, rotationDirection: OnjString, amount: OnjInt): OnjEffect {
        return OnjEffect(Effect.TurnRevolver(
            triggerOrError(trigger.value),
            when (rotationDirection.value) {
                "left" -> GameController.RevolverRotation.Left(amount.value.toInt())
                "right" -> GameController.RevolverRotation.Right(amount.value.toInt())
                "none" -> GameController.RevolverRotation.None
                else -> throw RuntimeException("unknown rotation direction: ${rotationDirection.value}")
            },
            false
        ))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [Effect]", type = OnjFunctionType.CONVERSION)
    fun canTriggerInHand(effect: OnjEffect): OnjEffect {
        return OnjEffect(effect.value.copy().apply { triggerInHand = true })
    }

    @RegisterOnjFunction(schema = "use Cards; params: [Effect]", type = OnjFunctionType.CONVERSION)
    fun hide(effect: OnjEffect): OnjEffect {
        return OnjEffect(effect.value.copy().apply { isHidden = true })
    }

    @RegisterOnjFunction(schema = "params: [*[]]")
    fun bNum(onjArr: OnjArray): OnjBulletSelector {
        val nums = mutableSetOf<Int>()
        var allowSelf = false

        for (value in onjArr.value) when (value) {
            is OnjInt -> {
                var num = value.value.toInt()
                num = Utils.convertSlotRepresentation(num)
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

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, EffectValue]")
    fun poison(turns: OnjEffectValue, damage: OnjEffectValue): OnjStatusEffect = OnjStatusEffect { controller, card, _ ->
        Poison(
            getStatusEffectValue(turns, controller, card, 1),
            getStatusEffectValue(damage, controller, card, 1)
        )
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue]")
    fun shield(shield: OnjEffectValue): OnjStatusEffect = OnjStatusEffect { controller, card, _ ->
        Shield(getStatusEffectValue(shield, controller, card, 1))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, float, boolean]")
    fun burning(
        rotations: OnjEffectValue,
        percent: OnjFloat,
        isInfinite: OnjBoolean
    ): OnjStatusEffect = OnjStatusEffect { controller, card, skipFirstRotation ->
        Burning(
            getStatusEffectValue(rotations, controller, card, 1),
            percent.value.toFloat(),
            isInfinite.value,
            skipFirstRotation
        )
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue]")
    fun fireResistance(turns: OnjEffectValue): OnjStatusEffect = OnjStatusEffect { controller, card, _ ->
        FireResistance(getStatusEffectValue(turns, controller, card, 1))
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, EffectValue]")
    fun bewitched(
        turns: OnjEffectValue,
        rotations: OnjEffectValue
    ): OnjStatusEffect = OnjStatusEffect { controller, card, skipFirstRotation ->
        Bewitched(
            getStatusEffectValue(turns, controller, card, 1),
            getStatusEffectValue(rotations, controller, card, 1),
            skipFirstRotation
        )
    }

    @RegisterOnjFunction(schema = "params: [{...*}]")
    fun negatePredicate(predicate: OnjObject): OnjObject = buildOnjObject {
        name("NegatePredicate")
        "value" with predicate
    }

    @RegisterOnjFunction(schema = "params: [int]", type = OnjFunctionType.CONVERSION)
    fun `val`(value: OnjInt): OnjEffectValue = OnjEffectValue { _, _ -> value.value.toInt() }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, float]", type = OnjFunctionType.OPERATOR)
    fun star(value: OnjEffectValue, multiplier: OnjFloat): OnjEffectValue = OnjEffectValue { controller, card ->
        (value.value(controller, card) * multiplier.value).toInt()
    }

    @RegisterOnjFunction(schema = "params: [{...*}]", type = OnjFunctionType.CONVERSION)
    fun activeChecker(value: OnjNamedObject): OnjActiveChecker {
        val predicate = GamePredicate.fromOnj(value)
        return OnjActiveChecker { controller -> predicate.check(controller) }
    }

   @Suppress("NAME_SHADOWING")
   private fun getStatusEffectValue(
       effectValue: OnjEffectValue,
       controller: GameController?,
       card: Card?,
       default: Int
   ) = controller?.let { controller ->
       card?.let { card ->
           effectValue.value(controller, card)
       }
   } ?: default

    private fun triggerOrError(trigger: String): Trigger = when (trigger) {
        "enter" -> Trigger.ON_ENTER
        "shot" -> Trigger.ON_SHOT
        "bounce" -> Trigger.ON_BOUNCE
        "leave" -> Trigger.ON_LEAVE
        "destroy" -> Trigger.ON_DESTROY
        "round start" -> Trigger.ON_ROUND_START
        "round end" -> Trigger.ON_ROUND_END
        "rotation" -> Trigger.ON_REVOLVER_ROTATION
        "card drawn" -> Trigger.ON_CARDS_DRAWN
        "special card drawn" -> Trigger.ON_SPECIAL_CARDS_DRAWN
        "any card destroyed" -> Trigger.ON_ANY_CARD_DESTROY
        "return home" -> Trigger.ON_RETURNED_HOME
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
