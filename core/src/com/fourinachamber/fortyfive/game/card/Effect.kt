package com.fourinachamber.fortyfive.game.card

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.game.StatusEffectCreator
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.utils.*

/**
 * represents an effect a card can have
 * @param trigger tells the effect when to activate
 */
abstract class Effect(val trigger: Trigger) {

    lateinit var card: Card

    abstract var triggerInHand: Boolean

    protected val cardDescName: String
        get() = "[${card.title}]"

    /**
     * called when the effect triggers
     * @return a timeline containing the actions of this effect
     */
    @MainThreadOnly
    abstract fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline

    abstract fun blocks(controller: GameController): Boolean

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
    ): Timeline = Timeline.timeline {

        when (bulletSelector) {

            is BulletSelector.ByLambda -> action {
                val cards = controller
                    .revolver
                    .slots
                    .mapIndexed { index, revolverSlot -> index to revolverSlot }
                    .filter { it.second.card != null }
                    .filter { (index, slot) -> bulletSelector.lambda(self, slot.card!!, index, controller) }
                    .map { it.second.card!! }
                store("selectedCards", cards)
            }

            is BulletSelector.ByPopup -> {
                include(controller.cardSelectionPopupTimeline(
                    "Select target:",
                    if (bulletSelector.includeSelf) null else self
                ))
                action {
                    store("selectedCards", listOf(get<Card>("selectedCard")))
                }
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

        override fun copy(): Effect = ReserveGain(trigger, amount, triggerInHand)

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val amount = amount(controller) * (triggerInformation.multiplier ?: 1)
            return Timeline.timeline {
                action { controller.gainReserves(amount) }
            }
        }

        override fun blocks(controller: GameController) = false

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

        override fun copy(): Effect = BuffDamage(trigger, amount, bulletSelector, triggerInHand, activeChecker)

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val amount = amount(controller) * (triggerInformation.multiplier ?: 1)
            val modifier = Card.CardModifier(
                damage = amount,
                source = cardDescName,
                validityChecker = { card.inGame },
                activeChecker = activeChecker
            )

            return Timeline.timeline {
                include(getSelectedBullets(bulletSelector, controller, this@BuffDamage.card))
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

        override fun copy(): Effect = GiftDamage(trigger, amount, bulletSelector, triggerInHand)

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline {
            val amount = amount(controller) * (triggerInformation.multiplier ?: 1)
            val modifier = Card.CardModifier(
                damage = amount,
                source = cardDescName
            )
            return Timeline.timeline {
                include(getSelectedBullets(bulletSelector, controller, this@GiftDamage.card))
                action {
                    get<List<Card>>("selectedCards")
                        .forEach { it.addModifier(modifier) }
                }
            }
        }

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

        override fun copy(): Effect = Draw(trigger, amount, triggerInHand)

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            delay(GraphicsConfig.bufferTime)
            val amount = amount(controller) * (triggerInformation.multiplier ?: 1)
            include(controller.drawCardPopupTimeline(amount))
        }

        override fun blocks(controller: GameController) = false

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

        override fun copy(): Effect = GiveStatus(trigger, statusEffectCreator, triggerInHand)

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            triggerInformation
                .targetedEnemies
                .map { controller.tryApplyStatusEffectToEnemy(statusEffectCreator(), it) }
                .collectTimeline()
                .let { include(it) }
        }

        override fun blocks(controller: GameController) = false

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

        override fun copy(): Effect = PutCardInHand(trigger, cardName, amount, triggerInHand)

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            includeAction(GraphicsConfig.cardHighlightEffect(card))
            val amount = amount(controller) * (triggerInformation.multiplier ?: 1)
            include(controller.tryToPutCardsInHandTimeline(cardName, amount))
        }

        override fun blocks(controller: GameController) = false

        override fun toString(): String {
            return "PutCardInHand(trigger=$trigger, card=$card, amount=$amount)"
        }
    }

    class Protect(
        trigger: Trigger,
        val bulletSelector: BulletSelector,
        val shots: Int,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, card))
            action {
                get<List<Card>>("selectedCards")
                    .forEach { it.protect(
                        cardDescName,
                        shots,
                        validityChecker = { card.inGame }
                    )}
            }
        }

        override fun blocks(controller: GameController) = bulletSelector.blocks(controller, card)

        override fun copy(): Effect = Protect(trigger, bulletSelector, shots, triggerInHand)

        override fun toString(): String = "Protect(trigger=$trigger)"
    }

    class Destroy(
        trigger: Trigger,
        val bulletSelector: BulletSelector,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, this@Destroy.card))
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

        override fun copy(): Effect = Destroy(trigger, bulletSelector, triggerInHand)
    }

    class DamageDirectly(trigger: Trigger, val damage: EffectValue, override var triggerInHand: Boolean) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            val damage = damage(controller) * (triggerInformation.multiplier ?: 1)
            triggerInformation
                .targetedEnemies
                .map { it.damage(damage) }
                .collectTimeline()
                .let { include(it) }
        }

        override fun blocks(controller: GameController): Boolean = false

        override fun copy(): Effect = DamageDirectly(trigger, damage, triggerInHand)
    }

    class DamagePlayer(trigger: Trigger, val damage: EffectValue, override var triggerInHand: Boolean) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(controller.damagePlayerTimeline(damage(controller)))
        }

        override fun blocks(controller: GameController): Boolean = false

        override fun copy(): Effect = DamagePlayer(trigger, damage, triggerInHand)
    }

    class KillPlayer(trigger: Trigger, override var triggerInHand: Boolean) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline =
            controller.playerDeathTimeline()

        override fun blocks(controller: GameController): Boolean = false

        override fun copy(): Effect = KillPlayer(trigger, triggerInHand)

    }

    class BounceBullet(
        trigger: Trigger,
        val bulletSelector: BulletSelector,
        override var triggerInHand: Boolean
    ) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation, controller: GameController): Timeline = Timeline.timeline {
            include(getSelectedBullets(bulletSelector, controller, this@BounceBullet.card))
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

        override fun copy(): Effect = BounceBullet(trigger, bulletSelector, triggerInHand)
    }

}

/**
 * used for telling an effect which bullet to apply a modifier to
 */
sealed class BulletSelector {

    class ByLambda(
        val lambda: (self: Card, other: Card, slot: Int, controller: GameController) -> Boolean
    ) : BulletSelector() {

        override fun blocks(controller: GameController, self: Card): Boolean = false
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
    }

    abstract fun blocks(controller: GameController, self: Card): Boolean

}

typealias EffectValue = (controller: GameController) -> Int

/**
 * possible triggers for an effect
 */
enum class Trigger {

    ON_ENTER, ON_SHOT, ON_ROUND_START, ON_ROUND_END, ON_DESTROY, ON_CARDS_DRAWN, ON_SPECIAL_CARDS_DRAWN,
    ON_REVOLVER_ROTATION

}

data class TriggerInformation(
    val multiplier: Int? = null,
    val targetedEnemies: List<Enemy>
)

fun GameController.TriggerInformation(multiplier: Int? = null): TriggerInformation {
    return TriggerInformation(
        multiplier,
        listOf(this.enemyArea.getTargetedEnemy())
    )
}
