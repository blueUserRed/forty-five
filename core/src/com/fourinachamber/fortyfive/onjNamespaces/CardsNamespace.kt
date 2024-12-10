package com.fourinachamber.fortyfive.onjNamespaces

import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.game.card.*
import com.fourinachamber.fortyfive.game.card.Trigger.Companion.triggerForSituation
import com.fourinachamber.fortyfive.game.controller.GameController
import com.fourinachamber.fortyfive.game.controller.NewGameController
import com.fourinachamber.fortyfive.game.controller.RevolverRotation
import com.fourinachamber.fortyfive.utils.Utils
import com.fourinachamber.fortyfive.utils.toIntRange
import onj.builder.buildOnjObject
import onj.customization.Namespace.*
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.customization.OnjFunction.RegisterOnjFunction.OnjFunctionType
import onj.value.*
import kotlin.math.floor
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
        "PassiveEffect" to OnjPassiveEffect::class,
        "CardPredicate" to OnjCardPredicate::class,
        "Zone" to OnjZone::class,
        "Trigger" to OnjTrigger::class,
    )

    @OnjNamespaceVariables
    val variables: Map<String, OnjObject> = mapOf(
        "value" to buildOnjObject {
            "mostExpensiveBulletInRevolver" with OnjEffectValue { controller, _, _ ->
                controller
                    .revolver
                    .slots
                    .mapNotNull { it.card }
                    .maxOfOrNull { it.baseCost }
                    ?: 0
            }
            "rotationAmount" with OnjEffectValue { _, card, _ -> card!!.rotationCounter }
            "bulletInSlot2" with OnjEffectValue { controller, _, _ ->
                controller.revolver.getCardInSlot(5 - 2)?.curDamage(controller) ?: 0
            }
            "amountOfCardsDrawn" with OnjEffectValue { _, _, triggerValue -> triggerValue!!.amountOfCardsDrawn }
            "sourceCardDamage" with OnjEffectValue { controller, _, triggerInformation ->
                triggerInformation!!.sourceCard!!.curDamage(controller)
            }
        },
        "zone" to buildOnjObject {
            NewGameController.Zone.entries.forEach {
                it.name.lowercase() with OnjZone(it)
            }
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue]")
    fun reserveGain(amount: OnjEffectValue): OnjEffect = OnjEffect(Effect.ReserveGain(amount.value, EffectData()))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, EffectValue]")
    fun buffDmg(bulletSelector: OnjBulletSelector, amount: OnjEffectValue): OnjEffect = OnjEffect(
        Effect.BuffDamage(
            amount.value,
            bulletSelector.value,
            data = EffectData()
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, EffectValue, ActiveChecker]")
    fun buffDmg(
        bulletSelector: OnjBulletSelector,
        amount: OnjEffectValue,
        activeChecker: OnjActiveChecker
    ): OnjEffect = OnjEffect(Effect.BuffDamage(
        amount.value,
        bulletSelector.value,
        activeChecker.value,
        EffectData()
    ))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, float]")
    fun buffDmgMultiplier(bulletSelector: OnjBulletSelector, amount: OnjFloat): OnjEffect =
        OnjEffect(
            Effect.BuffDamageMultiplier(
                amount.value.toFloat(),
                bulletSelector.value,
                data = EffectData()
            )
        )

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, EffectValue]")
    fun giftDmg(bulletSelector: OnjBulletSelector, amount: OnjEffectValue): OnjEffect = OnjEffect(
        Effect.GiftDamage(
            amount.value,
            bulletSelector.value,
            EffectData()
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue]")
    fun draw(amount: OnjEffectValue): OnjEffect = OnjEffect(Effect.Draw(amount.value, EffectData()))

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue]")
    fun drawFromBottomOfDeck(amount: OnjEffectValue): OnjEffect =
        OnjEffect(Effect.DrawFromBottomOfDeck(amount.value, EffectData()))

    @RegisterOnjFunction(schema = "use Cards; params: [StatusEffect]")
    fun giveStatus(effect: OnjStatusEffect): OnjEffect =
        OnjEffect(Effect.GiveStatus(effect.value, EffectData()))

    @RegisterOnjFunction(schema = "use Cards; params: [StatusEffect]")
    fun givePlayerStatus(effect: OnjStatusEffect): OnjEffect =
        OnjEffect(Effect.GivePlayerStatus(effect.value, EffectData()))

    @RegisterOnjFunction(schema = "use Cards; params: [string, EffectValue]")
    fun putCardInHand(name: OnjString, amount: OnjEffectValue): OnjEffect = OnjEffect(
        Effect.PutCardInHand(
        name.value,
        amount.value,
        EffectData()
    ))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, int, boolean]")
    fun protect(bulletSelector: OnjBulletSelector, shots: OnjInt, onlyValidWhileCardIsInGame: OnjBoolean): OnjEffect =
        OnjEffect(Effect.Protect(
            bulletSelector.value,
            shots.value.toInt(),
            onlyValidWhileCardIsInGame.value,
            EffectData()
        ))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector]")
    fun destroy(bulletSelector: OnjBulletSelector): OnjEffect =
        OnjEffect(Effect.Destroy(bulletSelector.value, EffectData()))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector]")
    fun destroyTargetOrDestroySelf(bulletSelector: OnjBulletSelector): OnjEffect =
        OnjEffect(
            Effect.DestroyTargetOrDestroySelf(
                bulletSelector.value,
                EffectData()
            )
        )

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, boolean]")
    fun damageDirect(damage: OnjEffectValue, isSpray: OnjBoolean): OnjEffect =
        OnjEffect(Effect.DamageDirectly(damage.value, isSpray.value, EffectData()))

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue]")
    fun damagePlayer(damage: OnjEffectValue): OnjEffect = OnjEffect(Effect.DamagePlayer(damage.value, EffectData()))

    @RegisterOnjFunction(schema = "params: []")
    fun killPlayer(): OnjEffect = OnjEffect(Effect.KillPlayer(EffectData()))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector]")
    fun bounce(bulletSelector: OnjBulletSelector): OnjEffect =
        OnjEffect(Effect.BounceBullet(bulletSelector.value, EffectData()))

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue]")
    fun discharge(turns: OnjEffectValue): OnjEffect = OnjEffect(Effect.DischargePoison(turns.value, EffectData()))

    @RegisterOnjFunction(schema = "params: [string]")
    fun addEncounterModifierWhileBulletIsInGame(encounterModifierName: OnjString): OnjEffect =
        OnjEffect(Effect.AddEncounterModifierWhileBulletIsInGame(
            encounterModifierName.value,
            EffectData()
        ))

    @RegisterOnjFunction(schema = "use Cards; params: [string, int]")
    fun turnRevolver(rotationDirection: OnjString, amount: OnjInt): OnjEffect = OnjEffect(Effect.TurnRevolver(
        when (rotationDirection.value) {
            "left" -> RevolverRotation.Left(amount.value.toInt())
            "right" -> RevolverRotation.Right(amount.value.toInt())
            "none" -> RevolverRotation.None
            else -> throw RuntimeException("unknown rotation direction: ${rotationDirection.value}")
        },
        EffectData()
    ))

    @RegisterOnjFunction(schema = "use Cards; params: [int, CardPredicate]")
    fun search(amount: OnjInt, predicate: OnjCardPredicate) = OnjEffect(
        Effect.Search(
            predicate.value,
            amount.value.toInt(),
            EffectData()
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Zone, Zone, string]")
    fun zoneChange(oldZone: OnjZone, newZone: OnjZone, whichCardTriggers: OnjString): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.ZoneChange> { gameSituation, card, triggerInformation, controller ->
            val triggers = WhichCardTriggers.fromOnj(whichCardTriggers.value).check(card, gameSituation.card)
            when {
                !triggers -> false
                oldZone.value != gameSituation.oldZone -> false
                newZone.value != gameSituation.newZone -> false
                else -> true
            }
        }
    )

    @RegisterOnjFunction(schema = "params: []")
    fun shot(): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.OnShot> { situation, card, info, controller -> situation.card === card }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Zone, string]")
    fun enterZone(newZone: OnjZone, whichCardTriggers: OnjString): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.ZoneChange> { gameSituation, card, triggerInformation, controller ->
            val triggers = WhichCardTriggers.fromOnj(whichCardTriggers.value).check(card, gameSituation.card)
            when {
                !triggers -> false
                newZone.value != gameSituation.newZone -> false
                else -> true
            }
        }
    )

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

        return OnjBulletSelector(BulletSelector.ByPredicate { self, other, slot, _ ->
            // when self === other allowSelf must be true, even if the slot is correct
            if (self === other) allowSelf
            else nums.contains(slot)
        })
    }

    @RegisterOnjFunction(schema = "params: [string]")
    fun bSelectByName(name: OnjString): OnjBulletSelector {
        return OnjBulletSelector(BulletSelector.ByPredicate { _, other, _, _ -> other.name == name.value })
    }

    @RegisterOnjFunction(schema = "params: [boolean, boolean]")
    fun bSelectTarget(includeSelf: OnjBoolean, optional: OnjBoolean): OnjBulletSelector {
        return OnjBulletSelector(BulletSelector.ByPopup(includeSelf.value, optional.value))
    }

    @RegisterOnjFunction(schema = "params: []")
    fun bSelectSourceBullet(): OnjBulletSelector {
        return OnjBulletSelector(BulletSelector.ByLambda { info, _ -> listOf(info.sourceCard!!) })
    }

    @RegisterOnjFunction("params: []")
    fun bSelectNeighbors(): OnjBulletSelector {
        return OnjBulletSelector(BulletSelector.ByPredicate { self, _, slot, triggerInformation ->
            val thisSlot = triggerInformation.controller.revolver.slots.indexOfFirst { it.card === self }
            val neighbors = arrayOf(
                if (thisSlot == 4) 0 else thisSlot + 1,
                if (thisSlot == 0) 4 else thisSlot - 1
            )
            return@ByPredicate slot in neighbors
        })
    }

    @RegisterOnjFunction("params: []")
    fun bSelectSelf(): OnjBulletSelector {
        return OnjBulletSelector(BulletSelector.ByLambda { _, card -> listOf(card) })
    }

    @RegisterOnjFunction("params: []")
    fun bSelectCachedBullets() = OnjBulletSelector(
        BulletSelector.ByLambda { _, card -> card.lastEffectAffectedCardsCache }
    )

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

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, float, boolean]")
    fun burningPlayer(
        rotations: OnjEffectValue,
        percent: OnjFloat,
        isInfinite: OnjBoolean
    ): OnjStatusEffect = OnjStatusEffect { controller, card, skipFirstRotation ->
        BurningPlayer(
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
    fun `val`(value: OnjInt): OnjEffectValue = OnjEffectValue { _, _, _ -> value.value.toInt() }

    @RegisterOnjFunction(schema = "params: [int[2]]", type = OnjFunctionType.CONVERSION)
    fun `val`(value: OnjArray): OnjEffectValue = OnjEffectValue { _, _, _ -> value.toIntRange().random().also { println("val called; result: $it") } }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, float]", type = OnjFunctionType.OPERATOR)
    fun star(value: OnjEffectValue, multiplier: OnjFloat): OnjEffectValue = OnjEffectValue { controller, card, triggerInformation ->
        (value.value(controller, card, triggerInformation) * multiplier.value).toInt()
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, int]", type = OnjFunctionType.OPERATOR)
    fun star(value: OnjEffectValue, multiplier: OnjInt): OnjEffectValue = OnjEffectValue { controller, card, triggerInformation ->
        floor(value.value(controller, card, triggerInformation) * multiplier.value.toFloat()).toInt()
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
           effectValue.value(controller, card, null)
       }
    } ?: default

    @RegisterOnjFunction(schema = "params: [int]")
    fun cardsWithCost(cost: OnjInt) = OnjCardPredicate(CardPredicate.cost(cost.value.toInt()))


    enum class WhichCardTriggers {
        ONLY_SELF {
            override fun check(
                self: Card,
                triggered: Card
            ): Boolean = self === triggered
        },
        ONLY_OTHERS {
            override fun check(
                self: Card,
                triggered: Card
            ): Boolean = self !== triggered
        },
        ALL_CARDS {
            override fun check(
                self: Card,
                triggered: Card
            ): Boolean = true
        }

        ;

        abstract fun check(self: Card, triggered: Card): Boolean

        companion object {

            fun fromOnj(name: String): WhichCardTriggers = when (name) {
                "onlySelf" -> ONLY_SELF
                "onlyOthers" -> ONLY_OTHERS
                "allCards" -> ALL_CARDS
                else -> throw RuntimeException("'$name' must be one of 'onlySelf', 'onlyOthers', 'allCards'")
            }
        }
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

class OnjPassiveEffect(
    override val value: PassiveEffectPrototype
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--passive-effect--'")
    }
}

class OnjCardPredicate(
    override val value: CardPredicate
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--card-predicate--'")
    }
}

class OnjTrigger(
    override val value: Trigger
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--trigger--'")
    }
}

class OnjZone(
    override val value: NewGameController.Zone
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--zone--'")
    }
}
