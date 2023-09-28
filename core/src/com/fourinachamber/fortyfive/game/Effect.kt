package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.utils.*

/**
 * represents an effect a card can have
 * @param trigger tells the effect when to activate
 */
abstract class Effect(val trigger: Trigger) {

    lateinit var card: Card

    /**
     * called when the effect triggers
     * @return a timeline containing the actions of this effect
     */
    @MainThreadOnly
    abstract fun onTrigger(triggerInformation: TriggerInformation): Timeline

    abstract fun blocks(controller: GameController): Boolean

    /**
     * checks if this effect is triggered by [triggerToCheck] and returns a timeline containing the actions of this
     * effect if it was
     */
    @MainThreadOnly
    fun checkTrigger(triggerToCheck: Trigger, triggerInformation: TriggerInformation): Timeline? {
        if (triggerToCheck == trigger) {
            FortyFiveLogger.debug("Effect", "effect $this triggered")
            return onTrigger(triggerInformation)
        }
        return null
    }

    protected fun getSelectedBullets(
        bulletSelector: BulletSelector,
        controller: GameController,
        self: Card
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
    class ReserveGain(trigger: Trigger, val amount: Int) : Effect(trigger) {

        override fun copy(): Effect = ReserveGain(trigger, amount)

        override fun onTrigger(triggerInformation: TriggerInformation): Timeline {
            val gameController = FortyFive.currentGame!!
//            val reservesLabel = gameController.reservesLabel

            val cardHighlight = GraphicsConfig.cardHighlightEffect(card)
//            val textActorAction = GraphicsConfig.numberChangeAnimation(
//                reservesLabel.localToStageCoordinates(Vector2(0f, 0f)),
//                amount.toString(),
//                true,
//                true,
//                FortyFive.currentGame!!.curScreen
//            )
            val amount = amount * (triggerInformation.multiplier ?: 1)
            return Timeline.timeline {
                delay(GraphicsConfig.bufferTime)
                includeActionLater(cardHighlight) { card.inGame }
                action { gameController.gainReserves(amount) }
//                includeAction(textActorAction)
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
        val amount: Int,
        private val bulletSelector: BulletSelector
    ) : Effect(trigger) {

        override fun copy(): Effect = BuffDamage(trigger, amount, bulletSelector)

        override fun onTrigger(triggerInformation: TriggerInformation): Timeline {
            val gameController = FortyFive.currentGame!!
            val amount = amount * (triggerInformation.multiplier ?: 1)
            val modifier = Card.CardModifier(
                amount,
                TemplateString(
                    GraphicsConfig.rawTemplateString("buffDetailText"),
                    mapOf(
                        "text" to if (amount > 0) "buff" else "debuff",
                        "amount" to amount,
                        "source" to card.title
                    )
                ),
            ) { card.inGame }

            return Timeline.timeline {
                include(getSelectedBullets(bulletSelector, gameController, this@BuffDamage.card))
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
        val amount: Int,
        private val bulletSelector: BulletSelector
    ) : Effect(trigger) {

        override fun copy(): Effect = GiftDamage(trigger, amount, bulletSelector)

        override fun onTrigger(triggerInformation: TriggerInformation): Timeline {
            val gameController = FortyFive.currentGame!!
            val amount = amount * (triggerInformation.multiplier ?: 1)
            val modifier = Card.CardModifier(
                amount,
                TemplateString(
                    GraphicsConfig.rawTemplateString("giftDetailText"),
                    mapOf(
                        "text" to if (amount > 0) "buff" else "debuff",
                        "amount" to amount,
                        "source" to card.title
                    )
                ),
            ) { true }

            return Timeline.timeline {
                include(getSelectedBullets(bulletSelector, gameController, this@GiftDamage.card))
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
    class Draw(trigger: Trigger, val amount: Int) : Effect(trigger) {

        override fun copy(): Effect = Draw(trigger, amount)

        override fun onTrigger(triggerInformation: TriggerInformation): Timeline = Timeline.timeline {
            val gameController = FortyFive.currentGame!!
            val cardHighlight = GraphicsConfig.cardHighlightEffect(card)
            delay(GraphicsConfig.bufferTime)
            includeActionLater(cardHighlight) { card.inGame }
            val amount = amount * (triggerInformation.multiplier ?: 1)
            include(gameController.drawCardPopupTimeline(amount))
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
    class GiveStatus(trigger: Trigger, val statusEffect: StatusEffect) : Effect(trigger) {

        override fun copy(): Effect = GiveStatus(trigger, statusEffect.copy())

        override fun onTrigger(triggerInformation: TriggerInformation): Timeline = Timeline.timeline {
            action {
                val game = FortyFive.currentGame!!
                val enemy = game.enemyArea.getTargetedEnemy()
                if (game.modifier?.shouldApplyStatusEffects() ?: true) {
                    enemy.applyEffect(statusEffect)
                }
                statusEffect.applyAnim(enemy)?.let { game.dispatchAnimTimeline(it) }
            }
        }

        override fun blocks(controller: GameController) = false

        override fun toString(): String {
            return "GiveStatus(trigger=$trigger, status=$statusEffect)"
        }

    }

    /**
     * puts a number of specific cards in the players hand
     */
    class PutCardInHand(trigger: Trigger, val cardName: String, val amount: Int) : Effect(trigger) {

        override fun copy(): Effect = PutCardInHand(trigger, cardName, amount)

        override fun onTrigger(triggerInformation: TriggerInformation): Timeline = Timeline.timeline {
            val gameController = FortyFive.currentGame!!
            includeAction(GraphicsConfig.cardHighlightEffect(card))
            val amount = amount * (triggerInformation.multiplier ?: 1)
            include(gameController.tryToPutCardsInHand(cardName, amount))
        }

        override fun blocks(controller: GameController) = false

        override fun toString(): String {
            return "PutCardInHand(trigger=$trigger, card=$card, amount=$amount)"
        }
    }

    class Protect(trigger: Trigger, val bulletSelector: BulletSelector) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation): Timeline = Timeline.timeline {
            val controller = FortyFive.currentGame!!
            val modifier = Card.CardModifier(
                0,
                TemplateString(
                    GraphicsConfig.rawTemplateString("protectDetailText"),
                    mapOf("source" to card.title)
                ),
                true
            ) { card.inGame }

            include(getSelectedBullets(bulletSelector, controller, this@Protect.card))
            action {
                get<List<Card>>("selectedCards")
                    .forEach { it.addModifier(modifier) }
            }
        }

        override fun blocks(controller: GameController) = bulletSelector.blocks(controller, card)

        override fun copy(): Effect = Protect(trigger, bulletSelector)

        override fun toString(): String = "Protect(trigger=$trigger)"
    }

    class Destroy(trigger: Trigger, val bulletSelector: BulletSelector) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation): Timeline = Timeline.timeline {
            val controller = FortyFive.currentGame!!
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

        override fun copy(): Effect = Destroy(trigger, bulletSelector)
    }

    class DamageDirectly(trigger: Trigger, val damage: Int) : Effect(trigger) {

        override fun onTrigger(triggerInformation: TriggerInformation): Timeline = Timeline.timeline {
            val controller = FortyFive.currentGame!!
            val damage = damage * (triggerInformation.multiplier ?: 1)
            include(controller.enemyArea.enemies[0].damage(damage))
        }

        override fun blocks(controller: GameController): Boolean = false

        override fun copy(): Effect = DamageDirectly(trigger, damage)
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

/**
 * possible triggers for an effect
 */
enum class Trigger {

    ON_ENTER, ON_SHOT, ON_ROUND_START, ON_DESTROY, ON_CARDS_DRAWN

}

data class TriggerInformation(
    val multiplier: Int? = null
)
