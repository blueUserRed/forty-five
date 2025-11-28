package com.microwavestudios.fortyfive.onjNamespaces

import com.microwavestudios.fortyfive.game.*
import com.microwavestudios.fortyfive.game.card.*
import com.microwavestudios.fortyfive.game.card.Trigger.Companion.triggerForSituation
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl.Zone
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.utils.Utils
import com.microwavestudios.fortyfive.utils.toIntRange
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
        "CardModifierPredicate" to OnjCardModifierPredicate::class,
        "CardPredicate" to OnjCardPredicate::class,
        "Zone" to OnjZone::class,
        "Trigger" to OnjTrigger::class,
        "VariableTextureSelector" to OnjVariableTextureSelector::class,
    )

    @OnjNamespaceVariables
    val variables: Map<String, OnjObject> = mapOf(
        "value" to buildOnjObject {
            "mostExpensiveBulletInRevolver" with OnjEffectValue { controller, _, _, _ ->
                controller
                    .revolver
                    .slots
                    .mapNotNull { it.card }
                    .maxOfOrNull { it.baseCost }
                    ?: 0
            }
            "rotationAmount" with OnjEffectValue { _, _, _, self -> self!!.rotationCounter }
            "bulletInSlot2" with OnjEffectValue { controller, _, _, _->
                controller.revolver.getCardInSlot(5 - 2)?.curDamage(controller) ?: 0
            }
            "amountOfCardsDrawn" with OnjEffectValue { _, _, triggerValue, _ -> triggerValue!!.amountOfCardsDrawn }
            "sourceCardDamage" with OnjEffectValue { controller, _, triggerInformation, _ ->
                triggerInformation!!.sourceCard!!.curDamage(controller)
            }
            "uniqueCardsInTheStack" with OnjEffectValue { controller, _, _, _ ->
                controller
                    .cardStack
                    .cards()
                    .map { it.name }
                    .toSet()
                    .size
            }
            // TODO: necessary? replace with rotationCounter functions
            "timeInRevolver"  with OnjEffectValue { controller, _, _, self ->
                controller.turnCounter - self!!.enteredOnTurn!!
            }
            "mostUniqueStatusEffectsOnEnemy" with OnjEffectValue { controller, _, _, _ ->
                controller
                    .allEnemies
                    .maxOf { it.statusEffects.toSet().size }
            }
            "highestPoison" with OnjEffectValue { controller, _, _, _ ->
                controller
                    .allEnemies
                    .maxOfOrNull { enemy ->
                        enemy.statusEffects.filterIsInstance<Poison>().firstOrNull()?.damage ?: 0
                    }
                    ?: 0
            }
        },
        "zone" to buildOnjObject {
            Zone.entries.forEach {
                it.name.lowercase() with OnjZone(it)
            }
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Zone]")
    fun sourceCardInZone(zone: OnjZone): OnjCardModifierPredicate = OnjCardModifierPredicate { _, _, modifier ->
        modifier.sourceCard?.inZone(zone.value) ?: false
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue]")
    fun reserveGain(amount: OnjEffectValue): OnjEffect = OnjEffect(Effect.ReserveGain(amount.value, EffectData()))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, EffectValue, CardModifierPredicate, CardModifierPredicate]")
    fun buffDmg(
        bulletSelector: OnjBulletSelector,
        amount: OnjEffectValue,
        activeChecker: OnjCardModifierPredicate,
        validityChecker: OnjCardModifierPredicate,
    ): OnjEffect = OnjEffect(
        Effect.BuffDamage(
            amount.value,
            bulletSelector.value,
            activeChecker = activeChecker.value,
            validityChecker = validityChecker.value,
            data = EffectData()
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, EffectValue, CardModifierPredicate]")
    fun buffDmgLimitActive(
        bulletSelector: OnjBulletSelector,
        amount: OnjEffectValue,
        activeChecker: OnjCardModifierPredicate,
    ): OnjEffect = OnjEffect(
        Effect.BuffDamage(
            amount.value,
            bulletSelector.value,
            activeChecker = activeChecker.value,
            validityChecker = { _, _, _, -> true },
            data = EffectData()
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, EffectValue, CardModifierPredicate]")
    fun buffDmgLimitValidity(
        bulletSelector: OnjBulletSelector,
        amount: OnjEffectValue,
        validityChecker: OnjCardModifierPredicate,
    ): OnjEffect = OnjEffect(
        Effect.BuffDamage(
            amount.value,
            bulletSelector.value,
            activeChecker = { _, _, _, -> true },
            validityChecker = validityChecker.value,
            data = EffectData()
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, EffectValue]")
    fun buffDmg(
        bulletSelector: OnjBulletSelector,
        amount: OnjEffectValue,
    ): OnjEffect = OnjEffect(
        Effect.BuffDamage(
            amount.value,
            bulletSelector.value,
            activeChecker = { _, _, _, -> true },
            validityChecker = { _, _, _, -> true },
            data = EffectData()
        )
    )


    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, float, CardModifierPredicate, CardModifierPredicate]")
    fun buffDmgMultiplier(
        bulletSelector: OnjBulletSelector,
        multiplier: OnjFloat,
        activeChecker: OnjCardModifierPredicate,
        validityChecker: OnjCardModifierPredicate,
    ): OnjEffect = OnjEffect(
        Effect.BuffDamageMultiplier(
            multiplier.value.toFloat(),
            bulletSelector.value,
            activeChecker = activeChecker.value,
            validityChecker = validityChecker.value,
            data = EffectData()
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, float]")
    fun buffDmgMultiplier(
        bulletSelector: OnjBulletSelector,
        multiplier: OnjFloat,
    ): OnjEffect = OnjEffect(
        Effect.BuffDamageMultiplier(
            multiplier.value.toFloat(),
            bulletSelector.value,
            activeChecker = { _, _, _ -> true },
            validityChecker = { _, _, _ -> true },
            data = EffectData()
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, EffectValue, Trigger, CardModifierPredicate, CardModifierPredicate, boolean]")
    fun buffDmgTransformable(
        bulletSelector: OnjBulletSelector,
        amount: OnjEffectValue,
        reevaluateOn: OnjTrigger,
        activeChecker: OnjCardModifierPredicate,
        validityChecker: OnjCardModifierPredicate,
        keepModifierActive: OnjBoolean
    ): OnjEffect = OnjEffect(
        Effect.BuffDamageTransformable(
            amount.value,
            bulletSelector.value,
            reevaluateOn = reevaluateOn.value,
            activeChecker = activeChecker.value,
            validityChecker = validityChecker.value,
            keepModifierActive = keepModifierActive.value,
            data = EffectData()
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, EffectValue, Trigger, CardModifierPredicate]")
    fun buffDmgTransformableLimitActive(
        bulletSelector: OnjBulletSelector,
        amount: OnjEffectValue,
        reevaluateOn: OnjTrigger,
        activeChecker: OnjCardModifierPredicate,
    ): OnjEffect = OnjEffect(
        Effect.BuffDamageTransformable(
            amount.value,
            bulletSelector.value,
            reevaluateOn = reevaluateOn.value,
            activeChecker = activeChecker.value,
            validityChecker = { _, _, _ -> true },
            keepModifierActive = true,
            data = EffectData()
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, EffectValue, Trigger]")
    fun buffDmgTransformable(
        bulletSelector: OnjBulletSelector,
        amount: OnjEffectValue,
        reevaluateOn: OnjTrigger,
    ): OnjEffect = OnjEffect(
        Effect.BuffDamageTransformable(
            amount.value,
            bulletSelector.value,
            reevaluateOn = reevaluateOn.value,
            activeChecker = { _, _, _ -> true },
            validityChecker = { _, _, _ -> true },
            keepModifierActive = true,
            data = EffectData()
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
        )
    )

    @RegisterOnjFunction(schema = "use Cards; params: [string, EffectValue]")
    fun putCardsInStack(name: OnjString, amount: OnjEffectValue): OnjEffect =
        OnjEffect(Effect.PutCardInStack(
            name.value,
            amount.value,
            EffectData()
        ))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, int]")
    fun protect(bulletSelector: OnjBulletSelector, shots: OnjInt): OnjEffect =
        OnjEffect(Effect.Protect(
            bulletSelector.value,
            shots.value.toInt(),
            activeChecker = { _, _, _ -> true },
            validityChecker = { _, _, _ -> true },
            data = EffectData()
        ))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, int, CardModifierPredicate]")
    fun protectLimitActive(bulletSelector: OnjBulletSelector, shots: OnjInt, activeChecker: OnjCardModifierPredicate): OnjEffect =
        OnjEffect(Effect.Protect(
            bulletSelector.value,
            shots.value.toInt(),
            activeChecker = activeChecker.value,
            validityChecker = { _, _, _ -> true },
            data = EffectData()
        ))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, int, CardModifierPredicate]")
    fun protectLimitValidity(bulletSelector: OnjBulletSelector, shots: OnjInt, validityChecker: OnjCardModifierPredicate): OnjEffect =
        OnjEffect(Effect.Protect(
            bulletSelector.value,
            shots.value.toInt(),
            activeChecker = { _, _, _ -> true },
            validityChecker = validityChecker.value,
            data = EffectData()
        ))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, int, CardModifierPredicate, CardModifierPredicate]")
    fun protect(
        bulletSelector: OnjBulletSelector,
        shots: OnjInt,
        activeChecker: OnjCardModifierPredicate,
        validityChecker: OnjCardModifierPredicate
    ): OnjEffect =
        OnjEffect(Effect.Protect(
            bulletSelector.value,
            shots.value.toInt(),
            activeChecker = activeChecker.value,
            validityChecker = validityChecker.value,
            data = EffectData()
        ))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, int]")
    fun protectParryOnly(bulletSelector: OnjBulletSelector, parries: OnjInt): OnjEffect =
        OnjEffect(Effect.ProtectParryOnly(
            bulletSelector.value,
            parries.value.toInt(),
            activeChecker = { _, _, _ -> true },
            validityChecker = { _, _, _ -> true },
            data = EffectData()
        ))

    @RegisterOnjFunction(schema = "use Cards; params: [BulletSelector, int, CardModifierPredicate]")
    fun protectParryOnlyLimitValidity(bulletSelector: OnjBulletSelector, parries: OnjInt, validityChecker: OnjCardModifierPredicate): OnjEffect =
        OnjEffect(Effect.ProtectParryOnly(
            bulletSelector.value,
            parries.value.toInt(),
            activeChecker = { _, _, _ -> true },
            validityChecker = validityChecker.value,
            data = EffectData()
        ))

    @RegisterOnjFunction(schema = "params: []")
    fun cleanse(): OnjEffect = OnjEffect(Effect.RemoveAllPlayerStatusEffects(EffectData()))

    @RegisterOnjFunction(schema = "params: []")
    fun beHyperactive(): OnjEffect = OnjEffect(Effect.BeHyperactive(EffectData()))

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
    fun addEncounterModifierWhileBulletIsInRevolver(encounterModifierName: OnjString): OnjEffect =
        OnjEffect(Effect.AddEncounterModifierWhileBulletIsInRevolver(
            encounterModifierName.value,
            EffectData()
        ))

    @RegisterOnjFunction(schema = "params: []")
    fun toTopCard(): OnjEffect = OnjEffect(Effect.ToTopCard(EffectData()))

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

    @RegisterOnjFunction(schema = "params: []")
    fun shot(): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.OnShot> { situation, card, info, controller -> situation.card === card }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [CardPredicate]")
    fun afterShot(predicate: OnjCardPredicate): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.AfterShot> { gameSituation, card, triggerInformation, controller ->
            predicate.value.check(gameSituation.card, controller, card)
        }
    )

    @RegisterOnjFunction(schema = "params: []")
    fun replaced(): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.CardReplaced> { gameSituation, card, triggerInformation, controller ->
            gameSituation.replaced === card
        }
    )

    @RegisterOnjFunction(schema = "params: []")
    fun turnBegin(): OnjTrigger = OnjTrigger(triggerForSituation<GameSituation.TurnBegin>())

    @RegisterOnjFunction(schema = "params: []")
    fun turnEnd(): OnjTrigger = OnjTrigger(triggerForSituation<GameSituation.TurnEnd>())

    @RegisterOnjFunction(schema = "params: [boolean]")
    fun statusEffectInflicted(onlyFromBullet: OnjBoolean): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.StatusEffectApplied> { situation, card, info, controller ->
            if (onlyFromBullet.value) info.sourceCard != null else true
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Zone, Zone, boolean, CardPredicate]")
    fun zoneChange(
        oldZone: OnjZone,
        newZone: OnjZone,
        triggerBefore: OnjBoolean,
        predicate: OnjCardPredicate,
    ): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.ZoneChange> { situation, card, _, controller ->
            val triggers = predicate.value.check(situation.card, controller, card)
            when {
                !triggers -> false
                situation.before != triggerBefore.value -> false
                situation.oldZone != oldZone.value -> false
                situation.newZone != newZone.value -> false
                else -> true
            }
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Zone, boolean, CardPredicate]")
    fun changedInOrOutOfZone(
        zone: OnjZone,
        triggerBefore: OnjBoolean,
        predicate: OnjCardPredicate,
    ): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.ZoneChange> { situation, card, _, controller ->
            val triggers = predicate.value.check(situation.card, controller, card)
            when {
                !triggers -> false
                situation.before != triggerBefore.value -> false
                zone.value == situation.newZone || zone.value == situation.oldZone -> true
                else -> false
            }
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [CardPredicate]")
    fun rightClicked(
        predicate: OnjCardPredicate
    ): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.CardRightClicked> { situation, card, _, controller ->
            predicate.value.check(situation.card, controller, card)
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Zone, CardPredicate]")
    fun enterZone(newZone: OnjZone, predicate: OnjCardPredicate): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.ZoneChange> { situation, card, triggerInformation, controller ->
            val triggers = predicate.value.check(situation.card, controller, card)
            when {
                situation.before -> false
                !triggers -> false
                newZone.value != situation.newZone -> false
                else -> true
            }
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Zone, CardPredicate]")
    fun leaveZone(oldZone: OnjZone, predicate: OnjCardPredicate): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.ZoneChange> { situation, card, triggerInformation, controller ->
            val triggers = predicate.value.check(situation.card, controller, card)
            when {
                !triggers -> false
                !situation.before -> false
                oldZone.value != situation.oldZone -> false
                else -> true
            }
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Zone, CardPredicate]")
    fun leaveZoneNoShot(oldZone: OnjZone, predicate: OnjCardPredicate): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.ZoneChange> { situation, card, triggerInformation, controller ->
            val triggers = predicate.value.check(situation.card, controller, card)
            when {
                !triggers -> false
                !situation.before -> false
                situation.afterShot -> false
                oldZone.value != situation.oldZone -> false
                else -> true
            }
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Zone, CardPredicate, boolean]")
    fun leaveZone(oldZone: OnjZone, predicate: OnjCardPredicate, before: OnjBoolean): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.ZoneChange> { situation, card, triggerInformation, controller ->
            val triggers = predicate.value.check(situation.card, controller, card)
            when {
                !triggers -> false
                situation.before != before.value -> false
                oldZone.value != situation.oldZone -> false
                else -> true
            }
        }
    )

    @RegisterOnjFunction(schema = "params: []")
    fun rotation(): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.RevolverRotation>()
    )

    @RegisterOnjFunction(schema = "params: [boolean, int]")
    fun cardsDrawn(mustBeSpecial: OnjBoolean, minimum: OnjInt): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.CardsDrawn> { situation, _, _, _ ->
            when {
                mustBeSpecial.value && !situation.isSpecial -> false
                situation.amount < minimum.value -> false
                else -> true
            }
        }
    )

    @RegisterOnjFunction(schema = "params: [boolean, boolean, boolean]")
    fun cardsDrawn(mustBeSpecial: OnjBoolean, mustBeFromBottom: OnjBoolean, mustIncludeSelf: OnjBoolean): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.CardsDrawn> { situation, card, _, _ ->
            when {
                mustBeSpecial.value && !situation.isSpecial -> false
                mustBeFromBottom.value && !situation.isFromBottom -> false
                mustIncludeSelf.value && !situation.cards.any { it === card } -> false
                else -> true
            }
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [CardPredicate]")
    fun cardDestroyed(predicate: OnjCardPredicate): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.CardDestroyed> { situation, card, _, controller ->
            predicate.value.check(situation.card, controller, card)
        }
    )

    @RegisterOnjFunction(schema = "params: [float]")
    fun playerHealthLowerThanPercent(percent: OnjFloat): OnjTrigger = OnjTrigger(
        triggerForSituation<GameSituation.PlayerHealthChanged> { situation, _, _, _ ->
            val curPercent = situation.newHealth.toFloat() / situation.baseHealth.toFloat()
            curPercent < percent.value
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Trigger, CardPredicate]", type = OnjFunctionType.INFIX)
    fun mustMatchPredicate(toModify: OnjTrigger, predicate: OnjCardPredicate): OnjTrigger = OnjTrigger(
        Trigger { situation, card, information, controller ->
            val originalTrigger = toModify.value.check(situation, card, information, controller)
            if (!originalTrigger) return@Trigger false
            predicate.value.check(card, controller, card)
        }
    )

    @RegisterOnjFunction(schema = "use Cards; params: [Zone]")
    fun inZone(zone: OnjZone): OnjCardPredicate = OnjCardPredicate(CardPredicate.inZone(zone.value))

    @RegisterOnjFunction(schema = "use Cards; params: [Zone[]]")
    fun inZone(zones: OnjArray): OnjCardPredicate {
        val zonesArr = Array(zones.value.size) { zones.value[it].value as Zone }
        return OnjCardPredicate(CardPredicate.inZone(*zonesArr))
    }

    @RegisterOnjFunction(schema = "params: [int]")
    fun inRevolverSlot(slot: OnjInt): OnjCardPredicate = OnjCardPredicate(
        CardPredicate.inRevolverSlot(Utils.convertSlotRepresentation(slot.value.toInt()))
    )

    @RegisterOnjFunction(schema = "params: []")
    fun startedInDeck(): OnjCardPredicate = OnjCardPredicate(
        CardPredicate.startedInDeck()
    )

    @RegisterOnjFunction(schema = "params: []")
    fun trueCardModifier(): OnjCardPredicate = OnjCardPredicate { _, _, _ -> true }

    @RegisterOnjFunction(schema = "params: []")
    fun isSelf(): OnjCardPredicate = OnjCardPredicate(CardPredicate.isSelf())

    @RegisterOnjFunction(schema = "params: [string]")
    fun hasName(name: OnjString): OnjCardPredicate = OnjCardPredicate(CardPredicate.hasName(name.value))

    @RegisterOnjFunction(schema = "params: [int]")
    fun costs(cost: OnjInt) = OnjCardPredicate(CardPredicate.cost(cost.value.toInt()))

    @RegisterOnjFunction(schema = "params: [int]")
    fun rotationCount(count: OnjInt) = OnjCardPredicate(CardPredicate.rotationCount(count.value.toInt()))

    @RegisterOnjFunction(schema = "params: []")
    fun inHomeSlot() = OnjCardPredicate(CardPredicate.inHomeSlot())

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, EffectValue]", type = OnjFunctionType.INFIX)
    fun equals(rhs: OnjEffectValue, lhs: OnjEffectValue): OnjCardPredicate = OnjCardPredicate { card, controller, self ->
        rhs.value(controller, card, null, self) == lhs.value(controller, card, null, self)
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, int]", type = OnjFunctionType.INFIX)
    fun equals(rhs: OnjEffectValue, lhs: OnjInt): OnjCardPredicate = OnjCardPredicate { card, controller, self ->
        rhs.value(controller, card, null, self) == lhs.value.toInt()
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, EffectValue]", type = OnjFunctionType.INFIX)
    fun lessThan(rhs: OnjEffectValue, lhs: OnjEffectValue): OnjCardPredicate = OnjCardPredicate { card, controller, self ->
        rhs.value(controller, card, null, self) < lhs.value(controller, card, null, self)
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, int]", type = OnjFunctionType.INFIX)
    fun lessThan(rhs: OnjEffectValue, lhs: OnjInt): OnjCardPredicate = OnjCardPredicate { card, controller, self ->
        rhs.value(controller, card, null, self) < lhs.value.toInt()
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, EffectValue]", type = OnjFunctionType.INFIX)
    fun moreThan(rhs: OnjEffectValue, lhs: OnjEffectValue): OnjCardPredicate = OnjCardPredicate { card, controller, self ->
        rhs.value(controller, card, null, self) > lhs.value(controller, card, null, self)
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, int]", type = OnjFunctionType.INFIX)
    fun moreThan(rhs: OnjEffectValue, lhs: OnjInt): OnjCardPredicate = OnjCardPredicate { card, controller, self ->
        rhs.value(controller, card, null, self) > lhs.value.toInt()
    }

    @RegisterOnjFunction(schema = "use Cards; params: [CardPredicate]")
    fun not(predicate: OnjCardPredicate): OnjCardPredicate = OnjCardPredicate(CardPredicate.not(predicate.value))

    @RegisterOnjFunction(schema = "use Cards; params: [CardPredicate, CardPredicate]", type = OnjFunctionType.INFIX)
    fun and(first: OnjCardPredicate, second: OnjCardPredicate): OnjCardPredicate =
        OnjCardPredicate(CardPredicate.and(first.value, second.value))

    @RegisterOnjFunction(schema = "use Cards; params: [CardPredicate, CardPredicate]", type = OnjFunctionType.INFIX)
    fun or(first: OnjCardPredicate, second: OnjCardPredicate): OnjCardPredicate =
        OnjCardPredicate(CardPredicate.or(first.value, second.value))

    @RegisterOnjFunction(schema = "use Cards; params: [CardPredicate]", type = OnjFunctionType.CONVERSION)
    fun bSelect(predicate: OnjCardPredicate): OnjBulletSelector = OnjBulletSelector(
        BulletSelector.ByLambda { info, card ->
            val p = predicate.value
            val controller = info.controller
            controller.allCards.filter { cardToCheck ->
                p.check(cardToCheck, controller, card)
            }
        }
    )


    @RegisterOnjFunction(schema = "params: [boolean, boolean, string]")
    fun bSelectRevolverTarget(includeSelf: OnjBoolean, optional: OnjBoolean, text: OnjString): OnjBulletSelector =
        OnjBulletSelector(BulletSelector.ByPopup(includeSelf.value, optional.value, text.value))

    @RegisterOnjFunction(schema = "params: []")
    fun bSelectSourceBullet(): OnjBulletSelector = OnjBulletSelector(BulletSelector.ByLambda { info, card ->
        listOf(
            info.sourceCard ?: throw RuntimeException("effect of $card doesn't result in any source bullet")
        )
    })

    @RegisterOnjFunction("params: []")
    fun bSelectNeighbors(): OnjBulletSelector {
        return OnjBulletSelector(BulletSelector.RevolverCardByPredicate { self, _, slot, triggerInformation ->
            val thisSlot = triggerInformation.controller.revolver.slots.indexOfFirst { it.card === self }
            val neighbors = arrayOf(
                if (thisSlot == 4) 0 else thisSlot + 1,
                if (thisSlot == 0) 4 else thisSlot - 1
            )
            return@RevolverCardByPredicate slot - 1 in neighbors
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

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue]")
    fun poison(damage: OnjEffectValue): OnjStatusEffect = OnjStatusEffect { controller, card, _ ->
        Poison(
            getStatusEffectValue(damage, controller, card, 1),
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

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue]")
    fun frozen(shots: OnjEffectValue): OnjStatusEffect = OnjStatusEffect { controller, card, skipFirstRotation ->
        Frozen(
            getStatusEffectValue(shots, controller, card, 1),
            skipFirstRotation
        )
    }

    @RegisterOnjFunction(schema = "params: [{...*}]")
    fun negatePredicate(predicate: OnjObject): OnjObject = buildOnjObject {
        name("NegatePredicate")
        "value" with predicate
    }

    @RegisterOnjFunction(schema = "params: [int]", type = OnjFunctionType.CONVERSION)
    fun `val`(value: OnjInt): OnjEffectValue = OnjEffectValue { _, _, _, _ -> value.value.toInt() }

    @RegisterOnjFunction(schema = "params: [int[2]]", type = OnjFunctionType.CONVERSION)
    fun `val`(value: OnjArray): OnjEffectValue = OnjEffectValue { _, _, _, _ -> value.toIntRange().random() }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, float]", type = OnjFunctionType.OPERATOR)
    fun star(value: OnjEffectValue, multiplier: OnjFloat): OnjEffectValue = OnjEffectValue { controller, card, triggerInformation, self ->
        (value.value(controller, card, triggerInformation, self) * multiplier.value).toInt()
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, int]", type = OnjFunctionType.OPERATOR)
    fun star(value: OnjEffectValue, multiplier: OnjInt): OnjEffectValue = OnjEffectValue { controller, card, triggerInformation, self ->
        floor(value.value(controller, card, triggerInformation, self) * multiplier.value.toFloat()).toInt()
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, EffectValue]", type = OnjFunctionType.OPERATOR)
    fun star(value: OnjEffectValue, value2: OnjEffectValue): OnjEffectValue = OnjEffectValue { controller, card, triggerInformation, self ->
        val first = value.value(controller, card, triggerInformation, self)
        val second = value2.value(controller, card, triggerInformation, self)
        first * second
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, int]", type = OnjFunctionType.INFIX)
    fun atMost(value: OnjEffectValue, max: OnjInt): OnjEffectValue = OnjEffectValue { controller, card, triggerInformation, self ->
        val first = value.value(controller, card, triggerInformation, self)
        first.coerceAtMost(max.value.toInt())
    }

    @RegisterOnjFunction(schema = "params: []")
    fun damage(): OnjEffectValue = OnjEffectValue { controller, card, triggerInformation, self ->
        card?.curDamage(controller) ?: 0
    }

    @RegisterOnjFunction(schema = "params: []")
    fun lastTurnRotationCounter(): OnjEffectValue = OnjEffectValue { _, card, _, self ->
        card?.lastTurnRotationCounter ?: 0
    }

    @RegisterOnjFunction(schema = "params: []")
    fun turnRotationCounter(): OnjEffectValue = OnjEffectValue { _, card, _, self ->
        card?.turnRotationCounter ?: 0
    }

    @RegisterOnjFunction(schema = "params: []")
    fun slotNumber(): OnjEffectValue = OnjEffectValue { controller, card, _, self ->
        val num = controller
            .revolver
            .slots
            .find { it.card == card }
            ?.num
        num?.let { Utils.convertSlotRepresentation(it) } ?: 0
    }

    @RegisterOnjFunction(schema = "params: []")
    fun slotNumberOfSelf(): OnjEffectValue = OnjEffectValue { controller, card, _, self ->
        val num = controller
            .revolver
            .slots
            .find { it.card == self }
            ?.num
        num?.let { Utils.convertSlotRepresentation(it) } ?: 0
    }

    @RegisterOnjFunction(schema = "use Cards; params: [CardPredicate]")
    fun damageOfCard(predicate: OnjCardPredicate): OnjEffectValue = OnjEffectValue { controller, card, triggerInformation, self ->
        val p = predicate.value
        val card = controller.allCards.firstOrNull { cardToCheck ->
            p.check(cardToCheck, controller, card)
        }
        card?.curDamage(controller) ?: 0
    }

    @RegisterOnjFunction(schema = "use Cards; params: [CardPredicate]")
    fun countCards(predicate: OnjCardPredicate): OnjEffectValue = OnjEffectValue { controller, card, triggerInformation, self ->
        val p = predicate.value
        var count = 0
        controller.allCards.forEach { cardToCheck ->
            if (p.check(cardToCheck, controller, card)) count++
        }
        count
    }

    @RegisterOnjFunction(schema = "params: [{...*}]", type = OnjFunctionType.CONVERSION)
    fun modifierPredicate(value: OnjNamedObject): OnjCardModifierPredicate {
        val predicate = GamePredicate.fromOnj(value)
        return OnjCardModifierPredicate { controller, _, _ -> predicate.check(controller) }
    }

    @RegisterOnjFunction(schema = "use Cards; params: [EffectValue, int]")
    fun numberBasedVariableTexture(value: OnjEffectValue, base: OnjInt): OnjVariableTextureSelector = OnjVariableTextureSelector(
        VariableTextureSelector(
            { controller, card ->
                value.value(controller, card, null, card).toString()
            },
            base.value.toString()
        )
    )

    @Suppress("NAME_SHADOWING")
    private fun getStatusEffectValue(
        effectValue: OnjEffectValue,
        controller: GameController?,
        card: Card?,
        default: Int
    ) = controller?.let { controller ->
       card?.let { card ->
           effectValue.value(controller, card, null, card)
       }
    } ?: default

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

class OnjCardModifierPredicate(
    override val value: CardModifierPredicate
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--active-checker--'")
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
    override val value: Zone
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--zone--'")
    }
}

class OnjVariableTextureSelector(
    override val value: VariableTextureSelector
) : OnjValue() {
    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--VariableTextureSelector--'")
    }
}
