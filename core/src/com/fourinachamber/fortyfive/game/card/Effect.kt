package com.fourinachamber.fortyfive.game.card

import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.game.controller.GameController
import com.fourinachamber.fortyfive.game.controller.NewGameController
import com.fourinachamber.fortyfive.game.controller.RevolverRotation
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.utils.*

/**
 * represents an effect a card can have
 * @param trigger tells the effect when to activate
 */
abstract class Effect(val data: EffectData) {

    protected fun cardDescName(card: Card): String = "[${card.title}]"

    /**
     * called when the effect triggers
     * @return a timeline containing the actions of this effect
     */
    @MainThreadOnly
    abstract fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline

    open fun blocks(card: Card, controller: GameController): Boolean = false

    abstract fun useAlternateOnShotTriggerPosition(): Boolean

    protected fun cardsAffected(thisCard: Card, affected: List<Card>) {
        if (!data.cacheAffectedCards) return
        thisCard.lastEffectAffectedCardsCache = affected
    }

    /**
     * checks if this effect is triggered by [triggerToCheck] and returns a timeline containing the actions of this
     * effect if it was
     */
    @MainThreadOnly
    fun checkTrigger(
        triggerToCheck: Trigger,
        triggerInformation: TriggerInformation,
        controller: GameController,
        onCard: Card
    ): Timeline? {
        if (data.trigger.checkTrigger(triggerToCheck, triggerInformation, onCard)) {
            FortyFiveLogger.debug("Effect", "effect $this triggered")
            return onTrigger(onCard, triggerInformation, controller)
        }
        return null
    }

    fun checkConditions(controller: GameController, card: Card): Boolean = data.condition?.check(controller) ?: true

    protected fun getSelectedBullets(
        bulletSelector: BulletSelector,
        controller: GameController,
        self: Card,
        triggerInformation: TriggerInformation,
    ): Timeline = Timeline.timeline {

        when (bulletSelector) {

            is BulletSelector.ByPredicate -> action {
                val cards = controller
                    .cardsInRevolverIndexed()
                    .filter { (index, card) -> bulletSelector.lambda(self, card, index, triggerInformation) }
                    .map { it.second }
                cardsAffected(self, cards)
                store("selectedCards", cards)
            }

            is BulletSelector.ByLambda -> action {
                val cards = bulletSelector.lambda(triggerInformation, self)
                cardsAffected(self, cards)
                store("selectedCards", cards)
            }

            is BulletSelector.ByPopup -> {
                includeLater(
                    { Timeline.timeline {
                        include(controller.cardSelectionPopupTimeline(
                            "Select Target Bullet",
                            if (bulletSelector.includeSelf) null else self
                        ))
                        action {
                            val cards = listOf(get<Card>("selectedCard"))
                            cardsAffected(self, cards)
                            store("selectedCards", cards)
                        }
                    } },
                    { !bulletSelector.blocks(controller, self) }
                )
                includeLater(
                    { Timeline.timeline {
                       action {
                           store("selectedCards", listOf<Card>())
                       }
                    } },
                    { bulletSelector.blocks(controller, self) }
                )
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

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
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
        private val activeChecker: (controller: GameController) -> Boolean = { true },
        data: EffectData
    ) : Effect(data) {

        override fun copy(data: EffectData): Effect = BuffDamage(amount, bulletSelector, activeChecker, data)

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            val modifier = Card.CardModifier(
                damage = amount,
                source = cardDescName(card),
                validityChecker = { card.inGame },
                activeChecker = activeChecker
            )

            return Timeline.timeline {
                include(getSelectedBullets(bulletSelector, controller, card, triggerInformation))
                action {
                    get<List<Card>>("selectedCards")
                        .forEach { it.addModifier(modifier) }
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
        private val activeChecker: (controller: GameController) -> Boolean = { true },
        data: EffectData
    ) : Effect(data) {

        override fun copy(data: EffectData): Effect =
            BuffDamageMultiplier(multiplier, bulletSelector, activeChecker, data)

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val multiplier = multiplier * (triggerInformation.multiplier ?: 1)
            val modifier = Card.CardModifier(
                damageMultiplier = multiplier,
                source = cardDescName(card),
                validityChecker = { card.inGame },
                activeChecker = activeChecker
            )

            return Timeline.timeline {
                include(getSelectedBullets(bulletSelector, controller, card, triggerInformation))
                action {
                    get<List<Card>>("selectedCards")
                        .forEach { it.addModifier(modifier) }
                }
            }
        }


        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun blocks(card: Card, controller: GameController) = bulletSelector.blocks(controller, card)

    }

    /**
     * gifts a card a buff (or debuff) of its damage (stays valid even after the card left the game)
     * @param amount the amount by which the damage is changed
     * @param bulletSelector tells this effect which bullets to apply this effect to
     */
    class GiftDamage(
        val amount: EffectValue,
        private val bulletSelector: BulletSelector,
        data: EffectData
    ) : Effect(data) {

        override fun copy(data: EffectData): Effect = GiftDamage(amount, bulletSelector, data)

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            val modifier = Card.CardModifier(
                damage = amount,
                source = cardDescName(card)
            )
            return Timeline.timeline {
                include(getSelectedBullets(bulletSelector, controller, card, triggerInformation))
                action {
                    get<List<Card>>("selectedCards")
                        .forEach { it.addModifier(modifier) }
                }
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun blocks(card: Card, controller: GameController) = bulletSelector.blocks(controller, card)

        override fun toString(): String {
            return "GiftDamage(amount=$amount)"
        }

    }

    /**
     * lets the player draw cards
     * @param amount the amount of cards to draw
     */
    class Draw(val amount: EffectValue, data: EffectData) : Effect(data) {

        override fun copy(data: EffectData): Effect = Draw(amount, data)

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            delay(GraphicsConfig.bufferTime)
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            include(controller.drawCardsTimeline(amount))
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "Draw(amount=$amount)"
        }
    }

    class GiveStatus(
        val statusEffectCreator: StatusEffectCreator,
        data: EffectData
    ) : Effect(data) {

        override fun copy(data: EffectData): Effect = GiveStatus(statusEffectCreator, data)

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            triggerInformation
                .targetedEnemies
                .map {
                    controller.tryApplyStatusEffectToEnemyTimeline(statusEffectCreator(
                        controller,
                        card,
                        triggerInformation.isOnShot
                    ), it)
                }
                .collectTimeline()
                .let { include(it) }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "GiveStatus()"
        }

    }

    /**
     * puts a number of specific cards in the players hand
     */
    class PutCardInHand(
        val cardName: String,
        val amount: EffectValue,
        data: EffectData
    ) : Effect(data) {

    override fun copy(data: EffectData): Effect = PutCardInHand(cardName, amount, data)

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            include(controller.tryToPutCardsInHandTimeline(cardName, amount))
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "PutCardInHand(amount=$amount)"
        }
    }

    class Protect(
        val bulletSelector: BulletSelector,
        val shots: Int,
        val onlyValidWhileCardIsInGame: Boolean,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, triggerInformation))
            action {
                get<List<Card>>("selectedCards")
                    .forEach { it.protect(
                        cardDescName(card),
                        shots,
                        validityChecker = if (onlyValidWhileCardIsInGame) {
                            { card.inGame }
                        } else {
                            { true }
                        }
                    )}
            }
        }

        override fun blocks(card: Card, controller: GameController) = bulletSelector.blocks(controller, card)

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect = Protect(bulletSelector, shots, onlyValidWhileCardIsInGame, data)

        override fun toString(): String = "Protect()"
    }

    class Destroy(
        val bulletSelector: BulletSelector,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, triggerInformation))
            includeLater(
                {
                    get<List<Card>>("selectedCards")
                        .map { controller.destroyCardTimeline(it) }
                        .collectTimeline()
                },
                { true }
            )
        }

        override fun blocks(card: Card, controller: GameController): Boolean = bulletSelector.blocks(controller, card)

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(data: EffectData): Effect = Destroy(bulletSelector, data)
    }

    class DamageDirectly(
        val damage: EffectValue,
        val isSpray: Boolean,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            val damage = damage(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
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

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(controller.damagePlayerTimeline(damage(controller, card, triggerInformation)))
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = DamagePlayer(damage, data)
    }

    class KillPlayer(data: EffectData) : Effect(data) {

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline =
            controller.playerDeathTimeline()

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = KillPlayer(data)

    }

    class BounceBullet(
        val bulletSelector: BulletSelector,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, triggerInformation))
            includeLater(
                {
                    get<List<Card>>("selectedCards")
                        .map { controller.bounceBulletTimeline(it) }
                        .collectTimeline()
                },
                { true }
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

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(
                controller.tryApplyStatusEffectToPlayerTimeline(statusEffectCreator(
                    controller,
                    card,
                    triggerInformation.isOnShot
                ))
            )
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = GivePlayerStatus(statusEffectCreator, data)
    }

    class TurnRevolver(
        val rotation: RevolverRotation,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(controller.rotateRevolverTimeline(rotation))
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = TurnRevolver(rotation, data)
    }

    class DestroyTargetOrDestroySelf(
        val bulletSelector: BulletSelector,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            var destroySelf = false
            action {
                destroySelf = controller
                    .cardsInRevolver()
                    .size < 2
            }
            includeLater(
                { getSelectedBullets(bulletSelector, controller, card ,triggerInformation) },
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

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            triggerInformation.targetedEnemies.forEach { enemy ->
                includeLater(
                    {
                        enemy
                            .statusEffects
                            .filterIsInstance<Poison>()
                            .firstOrNull()
                            ?.discharge(turns(controller, card, triggerInformation), StatusEffectTarget.EnemyTarget(enemy), controller)
                            ?: Timeline()
                    },
                    { true }
                )
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = DischargePoison(turns, data)
    }

    class AddEncounterModifierWhileBulletIsInGame(
        private val encounterModifierName: String,
        data: EffectData
    ) : Effect(data) {

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            action {
                controller.addTemporaryEncounterModifier(
                    modifier = EncounterModifier.getFromName(encounterModifierName),
                    validityChecker = { card.inGame }
                )
            }
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect =
            AddEncounterModifierWhileBulletIsInGame(encounterModifierName, data)
    }

    class DrawFromBottomOfDeck(val amount: EffectValue, data: EffectData) : Effect(data) {

        override fun copy(data: EffectData): Effect = DrawFromBottomOfDeck(amount, data)

        override fun onTrigger(card: Card, triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            delay(GraphicsConfig.bufferTime)
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
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
            controller: GameController
        ): Timeline = Timeline.timeline {
            var timeline: Timeline? = null
            action {
                timeline = controller
                    .cardStack
                    .filter { cardPredicate.check(it, controller) }
                    .shuffled()
                    .take(amount)
                    .also { cardsAffected(card, it) }
                    .map { controller.putCardFromStackInHandTimeline(it, card) }
                    .collectTimeline()
            }
            includeLater(
                { timeline!! },
                { true }
            )
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(data: EffectData): Effect = Search(cardPredicate, amount, data)
    }

}

/**
 * used for telling an effect which bullet to apply a modifier to
 */
sealed class BulletSelector {

    class ByPredicate(
        val lambda: (self: Card, other: Card, slot: Int, triggerInformation: TriggerInformation) -> Boolean
    ) : BulletSelector() {

        override fun blocks(controller: GameController, self: Card): Boolean = false
        override fun useAlternateOnShotTriggerPosition(): Boolean = false
    }

    class ByLambda(val lambda: (triggerInformation: TriggerInformation, card: Card) -> List<Card>) : BulletSelector() {

        override fun blocks(controller: GameController, self: Card): Boolean = false
        override fun useAlternateOnShotTriggerPosition(): Boolean = false
    }

    class ByPopup(val includeSelf: Boolean, val optional: Boolean) : BulletSelector() {

        override fun blocks(controller: GameController, self: Card): Boolean {
            if (optional) return false
            val bulletsInRevolver = controller.cardsInRevolver()
            if (bulletsInRevolver.size >= 2) return false
            if (bulletsInRevolver.isEmpty()) return true
            if (!includeSelf && bulletsInRevolver[0] === self) return true
            return false
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = true
    }

    abstract fun blocks(controller: GameController, self: Card): Boolean

    abstract fun useAlternateOnShotTriggerPosition(): Boolean
}

typealias EffectValue = (controller: GameController, card: Card?, triggerInformation: TriggerInformation?) -> Int


sealed class Trigger {

    abstract fun checkTrigger(other: Trigger, info: TriggerInformation, thisCard: Card): Boolean

    data object Never : Trigger() {
        override fun checkTrigger(other: Trigger, info: TriggerInformation, thisCard: Card): Boolean = false
    }

    class GameSituation(val situation: GameSituations, val anyCardTriggers: Boolean) : Trigger() {

        override fun checkTrigger(other: Trigger, info: TriggerInformation, thisCard: Card): Boolean {
            if (other !is GameSituation) return false
            if (info.sourceCard != null && !anyCardTriggers && info.sourceCard !== thisCard) return false
            return situation == other.situation
        }
    }

    class ZoneChange(
        val oldZone: NewGameController.Zone?,
        val newZone: NewGameController.Zone?,
        val anyCardTriggers: Boolean,
    ) : Trigger() {

        override fun checkTrigger(other: Trigger, info: TriggerInformation, thisCard: Card): Boolean {
            if (other !is ZoneChange) return false
            if (info.sourceCard != null && !anyCardTriggers && info.sourceCard !== thisCard) return false
            if (oldZone != null && other.oldZone != null && oldZone != other.oldZone) return false
            if (newZone != null && other.newZone != null && newZone != other.newZone) return false
            return true
        }
    }

    class CardRotatedInSlot(val slot: Int, val anyCardTriggers: Boolean) : Trigger() {

        override fun checkTrigger(other: Trigger, info: TriggerInformation, thisCard: Card): Boolean {
            if (other !is CardRotatedInSlot) return false
            if (info.sourceCard != null && !anyCardTriggers && info.sourceCard !== thisCard) return false
            return other.slot == slot
        }
    }

    class CardsDrawn(val amount: IntRange, val special: Boolean?, val fromBottom: Boolean?) : Trigger() {

        override fun checkTrigger(other: Trigger, info: TriggerInformation, thisCard: Card): Boolean {
            if (other !is CardsDrawn) return false
            if (!(other.amount intersection amount)) return false
            if (special != null && special != other.special) return false
            if (fromBottom != null && fromBottom != other.fromBottom) return false
            return true
        }
    }

}

enum class GameSituations {
    ON_SHOT,
    ON_ROUND_START,
    ON_ROUND_END,
    ON_DESTROY,
    ON_REVOLVER_ROTATION,
    ON_RETURNED_HOME,
    ON_RIGHT_CLICK,
}

data class TriggerInformation(
    val multiplier: Int? = null,
    val controller: GameController,
    val targetedEnemies: List<Enemy> = listOf(), // TODO: fix
    val isOnShot: Boolean = false,
    val amountOfCardsDrawn: Int = 0,
    val sourceCard: Card? = null,
)

data class EffectData(
    val trigger: Trigger = Trigger.Never,
    val isHidden: Boolean = false,
    val cacheAffectedCards: Boolean = false,
    val condition: GamePredicate? = null,
    val canPreventEnteringGame: Boolean = false
)
