package com.microwavestudios.fortyfive.game.card

import com.microwavestudios.fortyfive.game.*
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.game.widgets.RevolverSlot
import com.microwavestudios.fortyfive.utils.*

/**
 * represents an effect a card can have
 * @param trigger tells the effect when to activate
 */
abstract class Effect(val data: EffectData) {

    private var executionCount: Int = 0

    protected fun cardDescName(card: Card): String = "[${card.title}]"

    protected abstract fun onTrigger(
        card: Card,
        triggerInformation: TriggerInformation,
        controller: GameController,
        situation: GameSituation
    ): Timeline

    fun trigger(
        card: Card,
        triggerInformation: TriggerInformation,
        controller: GameController,
        situation: GameSituation
    ): Timeline {
        executionCount++
        return onTrigger(card, triggerInformation, controller, situation)
    }

    open fun blocks(card: Card, controller: GameController): Boolean = false

    open fun animatesInAfterlife(): Boolean = false

    abstract fun useAlternateOnShotTriggerPosition(): Boolean

    protected fun cardsAffected(thisCard: Card, affected: List<Card>) {
        if (!data.cacheAffectedCards) return
        thisCard.lastEffectAffectedCardsCache = affected
    }

    fun checkTrigger(
        situation: GameSituation,
        triggerInformation: TriggerInformation,
        controller: GameController,
        onCard: Card
    ): Boolean {
        data.onlyTriggerInZones?.let { zones ->
            if (onCard.zone !in zones) return false
        }
        if (!checkConditions(controller, onCard)) return false
        if (data.maxExecutions != -1 && executionCount >= data.maxExecutions) return false
        if (!data.trigger.check(situation, onCard, triggerInformation, controller)) return false
        return true
    }

    private fun checkConditions(controller: GameController, card: Card): Boolean =
        data.condition?.check(controller) ?: true

    protected fun getSelectedBullets(
        bulletSelector: BulletSelector,
        controller: GameController,
        self: Card,
        situation: GameSituation,
        triggerInformation: TriggerInformation,
    ): Timeline = Timeline.timeline {

        when (bulletSelector) {

            is BulletSelector.RevolverCardByPredicate -> action {
                val cards = controller
                    .cardsInRevolverIndexed()
                    .filter { (index, card) -> bulletSelector.lambda(self, card, index, triggerInformation) }
                    .map { it.second }
                cardsAffected(self, cards)
                store("selectedCards", cards)
            }

            is BulletSelector.ByLambda -> action {
                val cards = bulletSelector.lambda(triggerInformation, self, situation)
                cardsAffected(self, cards)
                store("selectedCards", cards)
            }

            is BulletSelector.RevolverCardByPopup -> later {

                val selector = CardInRevolverSelector(
                    controller,
                    bulletSelector.text,
                    predicate = { card ->
                        bulletSelector.includeSelf || card != self
                    }
                )
                val promise = selector.startSelect()
                waitForPromise(promise)
                later {
                    val result = promise.getOrNull()
                    if (result == null) {
                        action { store("selectedCards", listOf<Card>()) }
                    } else {
                        val cards = listOf(result)
                        action {
                            cardsAffected(self, cards)
                            store("selectedCards", cards)
                        }
                    }
                }
            }
            is BulletSelector.HandCardByPopup -> later {

                val selector = CardInHandSelector(
                    controller,
                    bulletSelector.text,
                    predicate = { card ->
                        bulletSelector.includeSelf || card != self
                    }
                )
                val promise = selector.startSelect()
                waitForPromise(promise)
                later {
                    val result = promise.getOrNull()
                    if (result == null) {
                        action { store("selectedCards", listOf<Card>()) }
                    } else {
                        val cards = listOf(result)
                        action {
                            cardsAffected(self, cards)
                            store("selectedCards", cards)
                        }
                    }
                }
            }
        }
    }

    /**
     * creates a copy of this effect
     */
    abstract fun copy(data: EffectData = this.data): Effect

    /**
     * The Player gains reserves
     * @param amount the amount of reserves gained
     */
    class ReserveGain(val amount: EffectValue, data: EffectData) : Effect(data) {

        override fun copy(data: EffectData): Effect = ReserveGain(amount, data)

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline {
            val amount = amount(controller, card, triggerInformation, card) * (triggerInformation.multiplier ?: 1)
            return Timeline.timeline {
                action { controller.gainReserves(amount, card.actor) }
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "ReserveGain(amount=$amount)"
        }

    }

    /**
     * Buffs (or debuffs) the damage of a card; only valid while the card that has this effect is still in the game
     * @param amount the amount the damage is changed by
     * @param bulletSelector tells the effect which bullets to apply the modifier to
     */
    class BuffDamage(
        val amount: EffectValue,
        private val bulletSelector: BulletSelector,
        private val activeChecker: CardModifierPredicate,
        private val validityChecker: CardModifierPredicate,
        data: EffectData
    ) : Effect(data) {

        override fun copy(data: EffectData): Effect = BuffDamage(amount, bulletSelector, activeChecker, validityChecker, data)

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            later {
                val amount = amount(controller, card, triggerInformation, card) * (triggerInformation.multiplier ?: 1)
                val modifier = CardDamageModifier(
                    damage = amount,
                    data = CardModifierData(
                        source = cardDescName(card),
                        sourceCard = card,
                        validityChecker = validityChecker,
                        activeChecker = activeChecker
                    )
                )
                include(getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation))
                action {
                    get<List<Card>>("selectedCards")
                        .forEach { it.addDamageModifier(modifier, controller) }
                }
            }
        }

        override fun blocks(card: Card, controller: GameController) = bulletSelector.blocks(controller, card)

        override fun toString(): String {
            return "BuffDmg(amount=$amount)"
        }

    }

    class BuffDamageMultiplier(
        val multiplier: Float,
        private val bulletSelector: BulletSelector,
        private val validityChecker: CardModifierPredicate,
        private val activeChecker: CardModifierPredicate,
        data: EffectData
    ) : Effect(data) {

        override fun copy(data: EffectData): Effect =
            BuffDamageMultiplier(multiplier, bulletSelector, validityChecker, activeChecker, data)

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            later {
                val multiplier = multiplier * (triggerInformation.multiplier ?: 1)
                val modifier = CardDamageModifier(
                    damageMultiplier = multiplier,
                    data = CardModifierData(
                        source = cardDescName(card),
                        sourceCard = card,
                        validityChecker = validityChecker,
                        activeChecker = activeChecker
                    )
                )
                include(getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation))
                action {
                    get<List<Card>>("selectedCards")
                        .forEach { it.addDamageModifier(modifier, controller) }
                }
            }
        }


        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun blocks(card: Card, controller: GameController) = bulletSelector.blocks(controller, card)

    }

    class BuffDamageTransformable(
        val amount: EffectValue,
        val multiplier: Float,
        private val bulletSelector: BulletSelector,
        private val reevaluateOn: Trigger,
        private val validityChecker: CardModifierPredicate,
        private val activeChecker: CardModifierPredicate,
        private val keepModifierActive: Boolean,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline { later {

            fun amount() = amount(controller, card, triggerInformation, card) * (triggerInformation.multiplier ?: 1)

            val data = CardModifierData(
                cardDescName(card),
                sourceCard = card,
                validityChecker = validityChecker,
                activeChecker = activeChecker,
                keepActive = keepModifierActive
            )

            val modifier = CardDamageModifier(
                damage = amount(),
                damageMultiplier = multiplier,
                data = data,
                transformers = listOf(
                    reevaluateOn to { old, _ -> CardDamageModifier(
                        damage = amount(),
                        damageMultiplier = multiplier,
                        data = data,
                        transformers = old.transformers
                    ) }
                )
            )

            include(getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation))
            action {
                get<List<Card>>("selectedCards")
                    .forEach { it.addDamageModifier(modifier, controller) }
            }
        } }

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect =
            BuffDamageTransformable(
                amount, multiplier, bulletSelector, reevaluateOn,
                validityChecker, activeChecker, keepModifierActive, data
            )

    }

    /**
     * lets the player draw cards
     * @param amount the amount of cards to draw
     */
    class Draw(val amount: EffectValue, data: EffectData) : Effect(data) {

        override fun copy(data: EffectData): Effect = Draw(amount, data)

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            delay(100)
            later {
                val amount = amount(controller, card, triggerInformation, card) * (triggerInformation.multiplier ?: 1)
                include(controller.drawCardsTimeline(amount, sourceCard = card))
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "Draw(amount=$amount)"
        }
    }

    class GiveStatus(
        val statusEffectCreator: StatusEffectCreator,
        val onlyStack: Boolean,
        data: EffectData
    ) : Effect(data) {

        override fun copy(data: EffectData): Effect = GiveStatus(statusEffectCreator, onlyStack, data)

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline { later {
            repeat(triggerInformation.multiplierOr1) {
                triggerInformation
                    .targetedEnemies
                    .mapNotNull { enemy ->
                        val statusEffect = statusEffectCreator(
                            controller,
                            card,
                            triggerInformation.isOnShot
                        )
                        if (onlyStack && enemy.statusEffects.none { it.canStackWith(statusEffect) }) {
                            null
                        } else {
                            controller.tryApplyStatusEffectToEnemyTimeline(statusEffect, enemy, card)
                        }
                    }
                    .collectTimeline()
                    .let { include(it) }
            }
        } }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "GiveStatus()"
        }

    }

    /**
     * puts a number of specific cards in the players hand
     */
    class PutCardInHand(
        val cardType: CardType,
        val amount: EffectValue,
        data: EffectData
    ) : Effect(data) {

        override fun copy(data: EffectData): Effect = PutCardInHand(cardType, amount, data)

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            later {
                val amount = amount(controller, card, triggerInformation, card) * (triggerInformation.multiplier ?: 1)
                include(controller.tryToPutCardsInHandTimeline(cardType, amount, sourceCard = card))
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "PutCardInHand(card=$cardType, amount=$amount)"
        }
    }

    /**
     * puts a number of specific cards in the players hand
     */
    class PutCardInStack(
        val cardType: CardType,
        val amount: EffectValue,
        val onTop: Boolean,
        data: EffectData
    ) : Effect(data) {

        override fun copy(data: EffectData): Effect = PutCardInStack(cardType, amount, onTop, data)

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            later {
                val amount = amount(controller, card, triggerInformation, card) * (triggerInformation.multiplier ?: 1)
                include(controller.putCardsInStackTimeline(cardType, amount, sourceCard = card, onTop = onTop))
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "PutCardInStack(card=$cardType, amount=$amount)"
        }
    }

    class Protect(
        val bulletSelector: BulletSelector,
        val shots: Int,
        val validityChecker: CardModifierPredicate,
        val activeChecker: CardModifierPredicate,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation))
            val protectingModifier = ProtectingModifier(
                shots = shots * triggerInformation.multiplierOr1,
                data = CardModifierData(
                    source = cardDescName(card),
                    sourceCard = card,
                    validityChecker = validityChecker,
                    activeChecker = activeChecker
                )
            )
            action {
                get<List<Card>>("selectedCards")
                    .forEach { it.protect(protectingModifier) }
            }
        }

        override fun blocks(card: Card, controller: GameController) = bulletSelector.blocks(controller, card)

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect = Protect(bulletSelector, shots, validityChecker, activeChecker, data)

        override fun toString(): String = "Protect()"
    }

    class ProtectParryOnly(
        val bulletSelector: BulletSelector,
        val parries: Int,
        val validityChecker: CardModifierPredicate,
        val activeChecker: CardModifierPredicate,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation))
            val protectingModifier = ProtectingModifier(
                shots = parries * triggerInformation.multiplierOr1,
                data = CardModifierData(
                    source = cardDescName(card),
                    sourceCard = card,
                    validityChecker = validityChecker,
                    activeChecker = activeChecker
                )
            )
            action {
                get<List<Card>>("selectedCards")
                    .forEach { it.protectParryOnly(protectingModifier) }
            }
        }

        override fun blocks(card: Card, controller: GameController) = bulletSelector.blocks(controller, card)

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect = ProtectParryOnly(bulletSelector, parries, validityChecker, activeChecker, data)

        override fun toString(): String = "ProtectParryOnly()"
    }

    class Destroy(
        val bulletSelector: BulletSelector,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation))
            includeLater(
                {
                    get<List<Card>>("selectedCards")
                        .map { controller.destroyCardTimeline(it, sourceCard = card) }
                        .collectTimeline()
                }
            )
        }

        override fun blocks(card: Card, controller: GameController): Boolean = bulletSelector.blocks(controller, card)

        override fun animatesInAfterlife(): Boolean = true

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect = Destroy(bulletSelector, data)
    }

    class CreateBulletsInAfterlife(
        val cardType: CardType,
        val amount: EffectValue,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = controller.createBulletsInAfterlifeTimeline(
            cardType,
            amount(controller, card, triggerInformation, card) * triggerInformation.multiplierOr1,
            card
        )

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun animatesInAfterlife(): Boolean = true

        override fun copy(data: EffectData): Effect = CreateBulletsInAfterlife(cardType, amount, data)

    }

    class DamageDirectly(
        val damage: EffectValue,
        val isSpray: Boolean,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            val damage = damage(controller, card, triggerInformation, card) * triggerInformation.multiplierOr1
            val enemies = if (isSpray) controller.allEnemies else triggerInformation.targetedEnemies
            enemies
                .map { it.damage(damage) }
                .collectTimeline()
                .let { include(it) }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = DamageDirectly(damage, isSpray, data)
    }

    class DamagePlayer(val damage: EffectValue, data: EffectData) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            include(
                controller.damagePlayerTimeline(
                    damage(controller, card, triggerInformation, card) * triggerInformation.multiplierOr1
                )
            )
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = DamagePlayer(damage, data)
    }

    class KillPlayer(data: EffectData) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = controller.playerDeathTimeline()

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = KillPlayer(data)

    }

    class BounceBullet(
        val bulletSelector: BulletSelector,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation))
            includeLater(
                {
                    get<List<Card>>("selectedCards")
                        .map { controller.bounceBulletTimeline(it) }
                        .collectTimeline()
                }
            )
        }

        override fun blocks(card: Card, controller: GameController): Boolean = bulletSelector.blocks(controller, card)

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect = BounceBullet(bulletSelector, data)
    }

    class GivePlayerStatus(
        val statusEffectCreator: StatusEffectCreator,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            repeat(triggerInformation.multiplierOr1) {
                include(
                    controller.tryApplyStatusEffectToPlayerTimeline(statusEffectCreator(
                        controller,
                        card,
                        triggerInformation.isOnShot
                    ), card)
                )
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = GivePlayerStatus(statusEffectCreator, data)
    }

    class RemoveAllPlayerStatusEffects(data: EffectData) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            include(controller.removeAllPlayerStatusEffectsTimeline())
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = RemoveAllPlayerStatusEffects(data)
    }

    class TurnRevolver(
        val rotation: RevolverRotation,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            include(controller.rotateRevolverTimeline(rotation.withAmount(rotation.amount * triggerInformation.multiplierOr1)))
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = TurnRevolver(rotation, data)
    }

    class DestroyTargetOrDestroySelf(
        val bulletSelector: BulletSelector,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            var destroySelf = false
            action {
                destroySelf = controller
                    .cardsInRevolver()
                    .size < 2
            }
            includeLater(
                { getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation) },
                { !destroySelf }
            )
            includeLater(
                {
                    get<List<Card>>("selectedCards")
                        .map { controller.destroyCardTimeline(it) }
                        .collectTimeline()
                },
                { !destroySelf }
            )
            includeLater(
                { controller.destroyCardTimeline(card) },
                { destroySelf }
            )
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect = DestroyTargetOrDestroySelf(bulletSelector, data)
    }

    class DischargePoison(
        private val turns: EffectValue,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            triggerInformation.targetedEnemies.forEach { enemy ->
                includeLater({
                    val turns = turns(controller, card, triggerInformation, card) * triggerInformation.multiplierOr1
                    enemy
                        .statusEffects
                        .filterIsInstance<Poison>()
                        .firstOrNull()
                        ?.discharge(turns, StatusEffectTarget.EnemyTarget(enemy), controller)
                        ?: Timeline()
                })
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = DischargePoison(turns, data)
    }

    class AddEncounterModifierWhileBulletIsInRevolver(
        private val encounterModifierName: String,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            action {
                controller.addTemporaryEncounterModifier(
                    modifier = EncounterModifier.getFromName(encounterModifierName),
                    validityChecker = { card.inZone(GameControllerImpl.Zone.REVOLVER) }
                )
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect =
            AddEncounterModifierWhileBulletIsInRevolver(encounterModifierName, data)
    }

    class DrawFromBottomOfDeck(val amount: EffectValue, data: EffectData) : Effect(data) {

        override fun copy(data: EffectData): Effect = DrawFromBottomOfDeck(amount, data)

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            delay(100)
            val amount = amount(controller, card, triggerInformation, card) * (triggerInformation.multiplier ?: 1)
            include(controller.drawCardsTimeline(amount, fromBottom = true))
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "Draw(amount=$amount)"
        }
    }

    class Search(
        private val cardPredicate: CardPredicate,
        private val amount: Int,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            later {
                controller
                    .cardStack
                    .cards()
                    .filter { cardPredicate.check(it, controller, card) }
                    .shuffled()
                    .take(amount * triggerInformation.multiplierOr1)
                    .also { cardsAffected(card, it) }
                    .map { controller.putCardFromStackInHandTimeline(it, card) }
                    .collectTimeline()
                    .let { include(it) }
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = Search(cardPredicate, amount, data)
    }

    class ToTopCard(
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            action { card.changeStackPosition(Card.StackPosition.TOP, controller) }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = ToTopCard(data)

    }

    class BeHyperactive(data: EffectData) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline { later {
           val slot = controller
               .revolver
               .slots
               .filter { it.card == null }
               .randomOrNull()
            if (slot == null) {
                include(controller.bounceBulletTimeline(card))
            } else {
                include(controller.switchSlotOfBulletInRevolverTimeline(card, slot.num))
            }
        } }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = BeHyperactive(data)
    }

    class DescendBullet(data: EffectData) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = controller.descendBulletTimeline()

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = DescendBullet(data)

        override fun animatesInAfterlife(): Boolean = true
    }

    class Resurrect(
        val revolverSlotGetter: RevolverSlotGetter,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            later {
                val promise = revolverSlotGetter(controller, card, triggerInformation)
                waitForPromise(promise)
                later {
                    val slot = promise.getOrNull() ?: return@later
                    if (slot.card != null) return@later
                    include(controller.resurrectTimeline(slot.num))
                }
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = true

        override fun copy(data: EffectData): Effect = Resurrect(revolverSlotGetter, data)

        override fun animatesInAfterlife(): Boolean = true
    }

    class Discard(
        val bulletSelector: BulletSelector,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation))
            includeLater({
                get<List<Card>>("selectedCards")
                    .map { controller.destroyCardInHandTimeline(it, card) }
                    .collectTimeline()
            })
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect = Discard(bulletSelector, data)

    }

    class ShuffleCardFromHandIntoStack(
        val bulletSelector: BulletSelector,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation))
            includeLater({
                get<List<Card>>("selectedCards")
                    .map { controller.shuffleCardFromHandIntoStackTimeline(it, card) }
                    .collectTimeline()
            })
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect = ShuffleCardFromHandIntoStack(bulletSelector, data)

    }

    class AddTemporaryBehaviour(
        val behaviour: BulletBehaviour,
        val bulletSelector: BulletSelector,
        val sourceCardPredicate: CardPredicate,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(
            card: Card,
            triggerInformation: TriggerInformation,
            controller: GameController,
            situation: GameSituation
        ): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, situation, triggerInformation))
            lateinit var timeline: Timeline
            action { timeline = this }
            later {
                val cards = timeline.get<List<Card>>("selectedCards")
                cards.forEach { card ->
                    val condition = { sourceCardPredicate.check(card, controller, card) }
                    card.addTemporaryBehaviour(behaviour, condition)
                }
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect =
            AddTemporaryBehaviour(behaviour, bulletSelector, sourceCardPredicate, data)

    }

}

/**
 * used for telling an effect which bullet to apply a modifier to
 */
sealed class BulletSelector {

    class RevolverCardByPredicate(
        val lambda: (self: Card, other: Card, slot: Int, triggerInformation: TriggerInformation) -> Boolean
    ) : BulletSelector() {

        override fun blocks(controller: GameController, self: Card): Boolean = false
        override fun useAlternateOnShotTriggerPosition(): Boolean = false
    }

    class ByLambda(
        val lambda: (triggerInformation: TriggerInformation, card: Card, situation: GameSituation) -> List<Card>
    ) : BulletSelector() {

        override fun blocks(controller: GameController, self: Card): Boolean = false
        override fun useAlternateOnShotTriggerPosition(): Boolean = false
    }

    class RevolverCardByPopup(val includeSelf: Boolean, val optional: Boolean, val text: String) : BulletSelector() {

        override fun blocks(controller: GameController, self: Card): Boolean {
            if (optional) return false
            val bulletsInRevolver = controller.cardsInRevolver()
            if (bulletsInRevolver.size >= 2) return false
            if (bulletsInRevolver.isEmpty()) return true
            if (!includeSelf && bulletsInRevolver.first() === self) return true
            return false
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = true
    }

    class HandCardByPopup(val includeSelf: Boolean, val optional: Boolean, val text: String) : BulletSelector() {

        override fun blocks(
            controller: GameController,
            self: Card
        ): Boolean {
            if (optional) return false
            val bulletsInHand = controller.cardsInHand
            if (bulletsInHand.size >= 2) return false
            if (bulletsInHand.isEmpty()) return true
            if (!includeSelf && bulletsInHand.first() === self) return true
            return false
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = true
    }

    abstract fun blocks(controller: GameController, self: Card): Boolean

    abstract fun useAlternateOnShotTriggerPosition(): Boolean
}

typealias EffectValue = (controller: GameController, card: Card?, triggerInformation: TriggerInformation?, self: Card?) -> Int

typealias RevolverSlotGetter = (
    controller: GameController,
    card: Card,
    triggerInformation: TriggerInformation
) -> Promise<out RevolverSlot?>

fun interface Trigger {

    fun check(
        gameSituation: GameSituation,
        card: Card,
        triggerInformation: TriggerInformation,
        controller: GameController
    ): Boolean

    companion object {
        val Never = Trigger { _, _, _, _ -> false }

        inline fun <reified T : GameSituation> triggerForSituation(
            crossinline block: (
                gameSituation: T,
                card: Card,
                triggerInformation: TriggerInformation,
                controller: GameController
            ) -> Boolean = { _, _, _, _, -> true },
        ): Trigger = Trigger { gameSituation, card, triggerInformation, controller ->
            if (!T::class.isInstance(gameSituation)) return@Trigger false
            block(gameSituation as T, card, triggerInformation, controller)
        }

    }
}

sealed class GameSituation(val relevantCards: List<Card>) {

    class ZoneChange(
        val card: Card,
        val oldZone: GameControllerImpl.Zone,
        val newZone: GameControllerImpl.Zone,
        val before: Boolean,
        val afterShot: Boolean,
    ) : GameSituation(listOf(card)) {
        init { require(oldZone != newZone) { "oldZone must be != newZone" } }
    }

    class RevolverRotation(
        val rotation: com.microwavestudios.fortyfive.game.controller.RevolverRotation
    ) : GameSituation(listOf())

    class PlayerHealthChanged(val oldHealth: Int, val newHealth: Int, val baseHealth: Int) : GameSituation(listOf())

    class CardsDrawn(
        val amount: Int,
        val isSpecial: Boolean,
        val isFromBottom: Boolean,
        val cards: List<Card>
    ) : GameSituation(cards)

    class StatusEffectApplied(
        val statusEffect: StatusEffect,
        val toPlayer: Boolean,
    ) : GameSituation(listOf())

    class CardReplaced(
        val replaced: Card,
        val newCard: Card
    ) : GameSituation(listOf(replaced))

    data object TurnEnd : GameSituation(listOf())
    data object TurnBegin : GameSituation(listOf())

    class OnShot(val card: Card) : GameSituation(listOf(card))
    class CardDestroyed(val card: Card) : GameSituation(listOf(card))

    class AfterShot(val card: Card) : GameSituation(listOf(card))

    class CardRightClicked(val card: Card) : GameSituation(listOf(card))

    class CardCompletedFullRotation(val card: Card) : GameSituation(listOf(card))
}

data class TriggerInformation(
    val multiplier: Int? = null,
    val controller: GameController,
    val targetedEnemies: List<Enemy> = listOf(),
    val isOnShot: Boolean = false,
    val amountOfCardsDrawn: Int = 0,
    val sourceCard: Card? = null,
) {
    val multiplierOr1: Int
        get() = multiplier ?: 1
}

data class EffectData(
    val trigger: Trigger = Trigger.Never,
    val isHidden: Boolean = false,
    val cacheAffectedCards: Boolean = false,
    val condition: GamePredicate? = null,
    val maxExecutions: Int = -1,
    val onlyTriggerInZones: List<GameControllerImpl.Zone>? = null,
    val canPreventEnteringGame: Boolean = false
)
