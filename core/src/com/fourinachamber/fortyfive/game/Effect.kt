package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.utils.*
import java.lang.Integer.min

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
    abstract fun onTrigger(): Timeline

    /**
     * checks if this effect is triggered by [triggerToCheck] and returns a timeline containing the actions of this
     * effect if it was
     */
    @MainThreadOnly
    fun checkTrigger(triggerToCheck: Trigger): Timeline? {
        if (triggerToCheck == trigger) {
            FortyFiveLogger.debug("Effect", "effect $this triggered")
            return onTrigger()
        }
        return null
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

        override fun onTrigger(): Timeline {
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

            return Timeline.timeline {
                delay(GraphicsConfig.bufferTime)
                includeActionLater(cardHighlight) { card.inGame }
                action { gameController.gainReserves(amount) }
//                includeAction(textActorAction)
            }
        }

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
        private val bulletSelector: BulletSelector? = null
    ) : Effect(trigger) {

        override fun copy(): Effect = BuffDamage(trigger, amount, bulletSelector)

        override fun onTrigger(): Timeline {
            val gameController = FortyFive.currentGame!!
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
                action {
                    for (i in 1..5) {
                        val card = gameController.revolver.getCardInSlot(i) ?: continue
                        if (!(bulletSelector?.invoke(this@BuffDamage.card, card, i) ?: true)) continue
                        card.addModifier(modifier)
                    }
                }
            }
        }

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
        private val bulletSelector: BulletSelector? = null
    ) : Effect(trigger) {

        override fun copy(): Effect = GiftDamage(trigger, amount, bulletSelector)

        override fun onTrigger(): Timeline {
            val gameController = FortyFive.currentGame!!
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
                action {
                    for (i in 1..5) {
                        val card = gameController.revolver.getCardInSlot(i) ?: continue
                        if (!(bulletSelector?.invoke(this@GiftDamage.card, card, i) ?: true)) continue
                        card.addModifier(modifier)
                    }
                }
            }
        }

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

        override fun onTrigger(): Timeline = Timeline.timeline {
            val gameController = FortyFive.currentGame!!
            val cardHighlight = GraphicsConfig.cardHighlightEffect(card)
            delay(GraphicsConfig.bufferTime)
            includeActionLater(cardHighlight) { card.inGame }
            action { gameController.specialDraw(amount) }
            delayUntil { gameController.currentState !is GameState.SpecialDraw }
        }

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

        override fun onTrigger(): Timeline = Timeline.timeline {
            action {
                val game = FortyFive.currentGame!!
                if (game.modifier?.shouldApplyStatusEffects() ?: true) {
                    game.enemyArea.getTargetedEnemy().applyEffect(statusEffect)
                }
            }
        }

        override fun toString(): String {
            return "GiveStatus(trigger=$trigger, status=$statusEffect)"
        }

    }

    /**
     * requires the player to destroy a bullet in the game. Has special behaviour when the trigger is onEnter: If no
     * destroyable card is in the game the card that has this effect can not be played
     */
    class Destroy(trigger: Trigger) : Effect(trigger) {

        override fun copy(): Effect = Destroy(trigger)

        override fun onTrigger(): Timeline = Timeline.timeline {
            val gameController = FortyFive.currentGame!!
            val cardHighlight = GraphicsConfig.cardHighlightEffect(card)
            includeLater(
                { Timeline.timeline {
                    delay(GraphicsConfig.bufferTime)
                    includeAction(cardHighlight)
                    delay(GraphicsConfig.bufferTime)
                    action { gameController.destroyCardPhase() }
                    delayUntil { gameController.currentState !is GameState.CardDestroy }
                } },
                { gameController.hasDestroyableCard() && card.inGame }
            )
        }

        override fun toString(): String {
            return "Destroy(trigger=$trigger)"
        }
    }

    /**
     * puts a number of specific cards in the players hand
     */
    class PutCardInHand(trigger: Trigger, val cardName: String, val amount: Int) : Effect(trigger) {

        override fun copy(): Effect = PutCardInHand(trigger, cardName, amount)

        override fun onTrigger(): Timeline = Timeline.timeline {
            val gameController = FortyFive.currentGame!!
            var addMax = 0
            includeAction(GraphicsConfig.cardHighlightEffect(card))
            action {
                addMax = gameController.maxCards - gameController.cardHand.cards.size
            }
            includeLater(
                // TODO: put string in some central place
                { gameController.confirmationPopup("Hand reached maximum of ${gameController.maxCards}") },
                { addMax == 0 }
            )
            action {
                repeat(min(amount, addMax)) { gameController.putCardInHand(cardName) }
            }
        }

        override fun toString(): String {
            return "PutCardInHand(trigger=$trigger, card=$card, amount=$amount)"
        }
    }

}

/**
 * used for telling an effect which bullet to apply a modifier to
 */
typealias BulletSelector = (self: Card, other: Card, slot: Int) -> Boolean

/**
 * possible triggers for an effect
 */
enum class Trigger {

    ON_ENTER, ON_SHOT, ON_ROUND_START, ON_DESTROY

}
