package com.fourinachamber.fortyfive.game.card

import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.utils.*

/**
 * represents an effect a card can have
 * @param trigger tells the effect when to activate
 */
abstract class Effect(val trigger: Trigger) {

    lateinit var card: Card

    abstract var triggerInHand: Boolean

    var isHidden: Boolean = false

    protected val cardDescName: String
        get() = "[${card.title}]"

    /**
     * called when the effect triggers
     * @return a timeline containing the actions of this effect
     */
    @MainThreadOnly
    abstract fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline

    abstract fun blocks(controller: GameController): Boolean

    abstract fun useAlternateOnShotTriggerPosition(): Boolean

    /**
     * checks if this effect is triggered by [triggerToCheck] and returns a timeline containing the actions of this
     * effect if it was
     */
    @MainThreadOnly
    fun checkTrigger(triggerToCheck: Trigger, triggerInformation: TriggerInformation, controller: GameController): Timeline? {
        if (triggerToCheck == trigger) {
            FortyFiveLogger.debug("Effect", "effect $this triggered")
            return onTrigger(triggerInformation, controller)
        }
        return null
    }

    protected fun getSelectedBullets(
        bulletSelector: BulletSelector,
        controller: GameController,
        self: Card,
        triggerInformation: TriggerInformation,
    ): Timeline = Timeline.timeline {

        when (bulletSelector) {

            is BulletSelector.ByPredicate -> action {
                val cards = controller
                    .revolver
                    .slots
                    .mapIndexed { index, revolverSlot -> index to revolverSlot }
                    .filter { it.second.card != null }
                    .filter { (index, slot) -> bulletSelector.lambda(self, slot.card!!, index, triggerInformation) }
                    .map { it.second.card!! }
                store("selectedCards", cards)
            }

            is BulletSelector.ByLambda -> action {
                store("selectedCards", bulletSelector.lambda(triggerInformation))
            }

            is BulletSelector.ByPopup -> {
                includeLater(
                    { Timeline.timeline {
                        include(controller.cardSelectionPopupTimeline(
                            "Select Target Bullet",
                            if (bulletSelector.includeSelf) null else self
                        ))
                        action {
                            store("selectedCards", listOf(get<Card>("selectedCard")))
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
    abstract fun copy(): Effect

    /**
     * The Player gains reserves
     * @param amount the amount of reserves gained
     */
    class ReserveGain(trigger: Trigger, val amount: EffectValue, override var triggerInHand: Boolean) : Effect(trigger) {

        override fun copy(): Effect = ReserveGain(trigger, amount, triggerInHand).also {
            it.isHidden = isHidden
        }

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            return Timeline.timeline {
                action { controller.gainReserves(amount, card.actor) }
            }
        }

        override fun blocks(controller: GameController) = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "ReserveGain(trigger=$trigger, amount=$amount)"
        }

    }

    /**
     * Buffs (or debuffs) the damage of a card; only valid while the card that has this effect is still in the game
     * @param amount the amount the damage is changed by
     * @param bulletSelector tells the effect which bullets to apply the modifier to
     */
    class BuffDamage(
        trigger: Trigger,
        val amount: EffectValue,
        private val bulletSelector: BulletSelector,
        override var triggerInHand: Boolean,
        private val activeChecker: (controller: GameController) -> Boolean = { true }
    ) : Effect(trigger) {

        override fun copy(): Effect = BuffDamage(trigger, amount, bulletSelector, triggerInHand, activeChecker).also {
            it.isHidden = isHidden
        }

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            val modifier = Card.CardModifier(
                damage = amount,
                source = cardDescName,
                validityChecker = { card.inGame },
                activeChecker = activeChecker
            )

            return Timeline.timeline {
                include(getSelectedBullets(bulletSelector, controller, this@BuffDamage.card, triggerInformation))
                action {
                    get<List<Card>>("selectedCards")
                        .forEach { it.addModifier(modifier) }
                }
            }
        }

        override fun blocks(controller: GameController) = bulletSelector.blocks(controller, card)

        override fun toString(): String {
            return "BuffDmg(trigger=$trigger, amount=$amount)"
        }

    }

    class BuffDamageMultiplier(
        trigger: Trigger,
        val multiplier: Float,
        private val bulletSelector: BulletSelector,
        override var triggerInHand: Boolean,
        private val activeChecker: (controller: GameController) -> Boolean = { true }
    ) : Effect(trigger) {

        override fun copy(): Effect =
            BuffDamageMultiplier(trigger, multiplier, bulletSelector, triggerInHand, activeChecker).also {
                it.isHidden = isHidden
            }

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val multiplier = multiplier * (triggerInformation.multiplier ?: 1)
            val modifier = Card.CardModifier(
                damageMultiplier = multiplier,
                source = cardDescName,
                validityChecker = { card.inGame },
                activeChecker = activeChecker
            )

            return Timeline.timeline {
                include(getSelectedBullets(bulletSelector, controller, this@BuffDamageMultiplier.card, triggerInformation))
                action {
                    get<List<Card>>("selectedCards")
                        .forEach { it.addModifier(modifier) }
                }
            }
        }


        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun blocks(controller: GameController) = bulletSelector.blocks(controller, card)

    }

    /**
     * gifts a card a buff (or debuff) of its damage (stays valid even after the card left the game)
     * @param amount the amount by which the damage is changed
     * @param bulletSelector tells this effect which bullets to apply this effect to
     */
    class GiftDamage(
        trigger: Trigger,
        val amount: EffectValue,
        private val bulletSelector: BulletSelector,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun copy(): Effect = GiftDamage(trigger, amount, bulletSelector, triggerInHand).also {
            it.isHidden = isHidden
        }

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            val modifier = Card.CardModifier(
                damage = amount,
                source = cardDescName
            )
            return Timeline.timeline {
                include(getSelectedBullets(bulletSelector, controller, this@GiftDamage.card, triggerInformation))
                action {
                    get<List<Card>>("selectedCards")
                        .forEach { it.addModifier(modifier) }
                }
            }
        }


        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun blocks(controller: GameController) = bulletSelector.blocks(controller, card)

        override fun toString(): String {
            return "GiftDamage(trigger=$trigger, amount=$amount)"
        }

    }

    /**
     * lets the player draw cards
     * @param amount the amount of cards to draw
     */
    class Draw(trigger: Trigger, val amount: EffectValue, override var triggerInHand: Boolean) : Effect(trigger) {

        override fun copy(): Effect = Draw(trigger, amount, triggerInHand).also {
            it.isHidden = isHidden
        }

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            delay(GraphicsConfig.bufferTime)
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            include(controller.drawCardPopupTimeline(amount))
        }

        override fun blocks(controller: GameController) = false


        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "Draw(trigger=$trigger, card=$card, amount=$amount)"
        }
    }

    /**
     * applies a status effect to the enemy
     * @param statusEffect the status to apply
     */
    class GiveStatus(
        trigger: Trigger,
        val statusEffectCreator: StatusEffectCreator,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun copy(): Effect = GiveStatus(trigger, statusEffectCreator, triggerInHand).also {
            it.isHidden = isHidden
        }

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            triggerInformation
                .targetedEnemies
                .map {
                    controller.tryApplyStatusEffectToEnemy(statusEffectCreator(
                        controller,
                        card,
                        triggerInformation.isOnShot
                    ), it)
                }
                .collectTimeline()
                .let { include(it) }
        }

        override fun blocks(controller: GameController) = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "GiveStatus(trigger=$trigger)"
        }

    }

    /**
     * puts a number of specific cards in the players hand
     */
    class PutCardInHand(
        trigger: Trigger,
        val cardName: String,
        val amount: EffectValue,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun copy(): Effect = PutCardInHand(trigger, cardName, amount, triggerInHand).also {
            it.isHidden = isHidden
        }

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            include(controller.tryToPutCardsInHandTimeline(cardName, amount))
        }

        override fun blocks(controller: GameController) = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "PutCardInHand(trigger=$trigger, card=$card, amount=$amount)"
        }
    }

    class Protect(
        trigger: Trigger,
        val bulletSelector: BulletSelector,
        val shots: Int,
        val onlyValidWhileCardIsInGame: Boolean,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card, triggerInformation))
            action {
                get<List<Card>>("selectedCards")
                    .forEach { it.protect(
                        cardDescName,
                        shots,
                        validityChecker = if (onlyValidWhileCardIsInGame) {
                            { card.inGame }
                        } else {
                            { true }
                        }
                    )}
            }
        }

        override fun blocks(controller: GameController) = bulletSelector.blocks(controller, card)

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(): Effect = Protect(trigger, bulletSelector, shots, onlyValidWhileCardIsInGame, triggerInHand).also {
            it.isHidden = isHidden
        }

        override fun toString(): String = "Protect(trigger=$trigger)"
    }

    class Destroy(
        trigger: Trigger,
        val bulletSelector: BulletSelector,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, this@Destroy.card, triggerInformation))
            includeLater(
                {
                    get<List<Card>>("selectedCards")
                        .map { controller.destroyCardTimeline(it) }
                        .collectTimeline()
                },
                { true }
            )
        }

        override fun blocks(controller: GameController): Boolean = bulletSelector.blocks(controller, card)

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(): Effect = Destroy(trigger, bulletSelector, triggerInHand).also {
            it.isHidden = isHidden
        }
    }

    class DamageDirectly(
        trigger: Trigger,
        val damage: EffectValue,
        val isSpray: Boolean,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            val damage = damage(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            val enemies = if (isSpray) controller.enemyArea.enemies else triggerInformation.targetedEnemies
            enemies
                .map { it.damage(damage) }
                .collectTimeline()
                .let { include(it) }
        }

        override fun blocks(controller: GameController): Boolean = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(): Effect = DamageDirectly(trigger, damage, isSpray, triggerInHand).also {
            it.isHidden = isHidden
        }
    }

    class DamagePlayer(trigger: Trigger, val damage: EffectValue, override var triggerInHand: Boolean) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(controller.damagePlayerTimeline(damage(controller, card, triggerInformation)))
        }

        override fun blocks(controller: GameController): Boolean = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(): Effect = DamagePlayer(trigger, damage, triggerInHand).also {
            it.isHidden = isHidden
        }
    }

    class KillPlayer(trigger: Trigger, override var triggerInHand: Boolean) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline =
            controller.playerDeathTimeline()

        override fun blocks(controller: GameController): Boolean = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(): Effect = KillPlayer(trigger, triggerInHand).also {
            it.isHidden = isHidden
        }

    }

    class BounceBullet(
        trigger: Trigger,
        val bulletSelector: BulletSelector,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, this@BounceBullet.card, triggerInformation))
            includeLater(
                {
                    get<List<Card>>("selectedCards")
                        .map { controller.bounceBullet(it) }
                        .collectTimeline()
                },
                { true }
            )
        }

        override fun blocks(controller: GameController): Boolean = bulletSelector.blocks(controller, card)

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(): Effect = BounceBullet(trigger, bulletSelector, triggerInHand).also {
            it.isHidden = isHidden
        }
    }

    class GivePlayerStatus(
        trigger: Trigger,
        val statusEffectCreator: StatusEffectCreator,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            action {
                controller.applyStatusEffectToPlayer(statusEffectCreator(
                    controller,
                    card,
                    triggerInformation.isOnShot
                ))
            }
        }

        override fun blocks(controller: GameController): Boolean = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(): Effect = GivePlayerStatus(trigger, statusEffectCreator, triggerInHand).also {
            it.isHidden = isHidden
        }
    }

    class TurnRevolver(
        trigger: Trigger,
        val rotation: GameController.RevolverRotation,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(controller.rotateRevolver(rotation))
        }

        override fun blocks(controller: GameController): Boolean = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(): Effect = TurnRevolver(trigger, rotation, triggerInHand).also {
            it.isHidden = isHidden
        }
    }

    class DestroyTargetOrDestroySelf(
        trigger: Trigger,
        val bulletSelector: BulletSelector,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            var destroySelf = false
            val card = this@DestroyTargetOrDestroySelf.card
            action {
                destroySelf = controller
                    .revolver
                    .slots
                    .mapNotNull { it.card }
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

        override fun blocks(controller: GameController): Boolean = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = bulletSelector.useAlternateOnShotTriggerPosition()

        override fun copy(): Effect = DestroyTargetOrDestroySelf(trigger, bulletSelector, triggerInHand).also {
            it.isHidden = isHidden
        }
    }

    class DischargePoison(
        trigger: Trigger,
        private val turns: EffectValue,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
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

        override fun blocks(controller: GameController): Boolean = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(): Effect = DischargePoison(trigger, turns, triggerInHand).also {
            it.isHidden = isHidden
        }
    }

    class AddEncounterModifierWhileBulletIsInGame(
        trigger: Trigger,
        private val encounterModifierName: String,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            action {
                controller.addTemporaryEncounterModifier(
                    modifier = EncounterModifier.getFromName(encounterModifierName),
                    validityChecker = { card.inGame }
                )
            }
        }

        override fun blocks(controller: GameController): Boolean = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(): Effect =
            AddEncounterModifierWhileBulletIsInGame(trigger, encounterModifierName, triggerInHand).also {
                it.isHidden = isHidden
            }
    }

    class DrawFromBottomOfDeck(trigger: Trigger, val amount: EffectValue, override var triggerInHand: Boolean) : Effect(trigger) {

        override fun copy(): Effect = DrawFromBottomOfDeck(trigger, amount, triggerInHand).also {
            it.isHidden = isHidden
        }

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            delay(GraphicsConfig.bufferTime)
            val amount = amount(controller, card, triggerInformation) * (triggerInformation.multiplier ?: 1)
            include(controller.drawCardPopupTimeline(amount, fromBottom = true))
        }

        override fun blocks(controller: GameController) = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun toString(): String {
            return "Draw(trigger=$trigger, card=$card, amount=$amount)"
        }
    }

    class Search(
        trigger: Trigger,
        private val cardPredicate: CardPredicate,
        private val amount: Int,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(
            triggerInformation: TriggerInformation,
            controller: GameController
        ): Timeline = Timeline.timeline {
            var timeline: Timeline? = null
            action {
                timeline = controller
                    .cardHand
                    .cards
                    .filter { cardPredicate.check(it, controller) }
                    .take(amount)
                    .map { controller.putCardFromStackInHandTimeline(it) }
                    .collectTimeline()
            }
            includeLater(
                { timeline!! },
                { true }
            )
        }

        override fun blocks(controller: GameController): Boolean = false

        override fun useAlternateOnShotTriggerPosition(): Boolean = false

        override fun copy(): Effect = Search(trigger, cardPredicate, amount, triggerInHand)
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

    class ByLambda(val lambda: (triggerInformation: TriggerInformation) -> List<Card>) : BulletSelector() {

        override fun blocks(controller: GameController, self: Card): Boolean = false
        override fun useAlternateOnShotTriggerPosition(): Boolean = false
    }

    class ByPopup(val includeSelf: Boolean, val optional: Boolean) : BulletSelector() {

        override fun blocks(controller: GameController, self: Card): Boolean {
            if (optional) return false
            val bulletsInRevolver = controller
                .revolver
                .slots
                .mapNotNull { it.card }
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

/**
 * possible triggers for an effect
 */
enum class Trigger(val cascadeTriggers: List<Trigger> = listOf()) {
    ON_ENTER,
    ON_LEAVE,
    ON_SHOT,
    ON_ROUND_START,
    ON_ROUND_END,
    ON_ANY_CARD_ENTER,
    ON_DESTROY(listOf(ON_LEAVE)),
    ON_BOUNCE(listOf(ON_LEAVE)),
    ON_CARDS_DRAWN,
    ON_ONE_OR_MORE_CARDS_DRAWN,
    ON_SPECIAL_ONE_OR_MORE_CARDS_DRAWN,
    ON_SPECIAL_CARDS_DRAWN,
    ON_REVOLVER_ROTATION,
    ON_ANY_CARD_DESTROY,
    ON_RETURNED_HOME,
    ON_ROTATE_IN_5,
}

data class TriggerInformation(
    val multiplier: Int? = null,
    val controller: GameController,
    val targetedEnemies: List<Enemy> = listOf(controller.enemyArea.getTargetedEnemy()),
    val isOnShot: Boolean = false,
    val amountOfCardsDrawn: Int = 0,
    val sourceCard: Card? = null,
)
