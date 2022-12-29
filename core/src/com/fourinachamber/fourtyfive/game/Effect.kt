package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.*
import onj.value.OnjObject
import java.lang.Integer.min
import kotlin.properties.Delegates

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
    abstract fun onTrigger(gameScreenController: GameScreenController): Timeline

    /**
     * checks if this effect is triggered by [triggerToCheck] and returns a timeline containing the actions of this
     * effect if it was
     */
    fun checkTrigger(triggerToCheck: Trigger, gameScreenController: GameScreenController): Timeline? {
        if (triggerToCheck == trigger) {
            FourtyFiveLogger.debug("Effect", "effect $this triggered")
            return onTrigger(gameScreenController)
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

        private val shakeActorAction = ShakeActorAction(
            xShake, yShake, xSpeedMultiplier, ySpeedMultiplier
        )

        init {
            shakeActorAction.duration = shakeDuration
        }

        override fun copy(): Effect = ReserveGain(trigger, amount)

        override fun onTrigger(gameScreenController: GameScreenController): Timeline {
            val reservesLabel = gameScreenController.reservesLabel!!
            val (x, y) = reservesLabel.localToStageCoordinates(Vector2(0f, 0f))

            val textAnimation = TextAnimation(
                x + reservesLabel.width / 2,
                y,
                amount.toString(),
                fontColor,
                fontScale,
                gameScreenController.curScreen!!.fonts[fontName]!!,
                raiseHeight,
                startFadeoutAt,
                gameScreenController.curScreen!!,
                duration
            )

            return Timeline.timeline {
                delay(bufferTime)
                includeLater(
                    { shakeCardTimeline(shakeActorAction) },
                    { card.inGame }
                )
                action {
                    gameScreenController.playGameAnimation(textAnimation)
                    gameScreenController.gainReserves(amount)
                }
                delayUntil { textAnimation.isFinished() }
            }
        }

        override fun toString(): String {
            return "ReserveGain(trigger=$trigger, amount=$amount)"
        }

        companion object {

            private lateinit var fontName: String
            private lateinit var fontColor: Color
            private var fontScale by Delegates.notNull<Float>()
            private var duration by Delegates.notNull<Int>()
            private var raiseHeight by Delegates.notNull<Float>()
            private var startFadeoutAt by Delegates.notNull<Int>()

            private var bufferTime by Delegates.notNull<Int>()

            fun init(config: OnjObject) {

                val plOnj = config.get<OnjObject>("reservesAnimation")

                fontName = plOnj.get<String>("font")
                fontScale = plOnj.get<Double>("fontScale").toFloat()
                duration = (plOnj.get<Double>("duration") * 1000).toInt()
                raiseHeight = plOnj.get<Double>("raiseHeight").toFloat()
                startFadeoutAt = (plOnj.get<Double>("startFadeoutAt") * 1000).toInt()
                fontColor = plOnj.get<Color>("positiveFontColor")

                bufferTime = (config.get<Double>("bufferTime") * 1000).toInt()
            }

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

        override fun onTrigger(gameScreenController: GameScreenController): Timeline {
            val modifier = Card.CardModifier(
                amount,
                TemplateString(
                    buffDetailTextRawString,
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
                        val card = gameScreenController.revolver!!.getCardInSlot(i) ?: continue
                        if (!(bulletSelector?.invoke(this@BuffDamage.card, card, i) ?: true)) continue
                        card.addModifier(modifier)
                    }
                }
            }
        }

        override fun toString(): String {
            return "BuffDmg(trigger=$trigger, amount=$amount)"
        }

        companion object {

            private lateinit var buffDetailTextRawString: String

            fun init(config: OnjObject) {
                val tmplOnj = config.get<OnjObject>("stringTemplates")
                buffDetailTextRawString = tmplOnj.get<String>("buffDetailText")
            }
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

        override fun onTrigger(gameScreenController: GameScreenController): Timeline {
            val modifier = Card.CardModifier(
                amount,
                TemplateString(
                    giftDetailTextRawString,
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
                        val card = gameScreenController.revolver!!.getCardInSlot(i) ?: continue
                        if (!(bulletSelector?.invoke(this@GiftDamage.card, card, i) ?: true)) continue
                        card.addModifier(modifier)
                    }
                }
            }
        }

        override fun toString(): String {
            return "GiftDamage(trigger=$trigger, amount=$amount)"
        }

        companion object {

            private lateinit var giftDetailTextRawString: String

            fun init(config: OnjObject) {
                val tmplOnj = config.get<OnjObject>("stringTemplates")
                giftDetailTextRawString = tmplOnj.get<String>("giftDetailText")
            }
        }

    }

    /**
     * lets the player draw cards
     * @param amount the amount of cards to draw
     */
    class Draw(trigger: Trigger, val amount: Int) : Effect(trigger) {

        private val shakeActorAction = ShakeActorAction(
            xShake, yShake, xSpeedMultiplier, ySpeedMultiplier
        )

        init {
            shakeActorAction.duration = shakeDuration
        }

        override fun copy(): Effect = Draw(trigger, amount)

        override fun onTrigger(gameScreenController: GameScreenController): Timeline = Timeline.timeline {
            delay(bufferTime)
            includeLater(
                { shakeCardTimeline(shakeActorAction) },
                { card.inGame }
            )
            action { gameScreenController.specialDraw(amount) }
            delayUntil { gameScreenController.currentPhase != GameScreenController.Gamephase.SPECIAL_DRAW }
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

        override fun onTrigger(gameScreenController: GameScreenController): Timeline = Timeline.timeline {
            action {
                gameScreenController.enemyArea!!.enemies[0].applyEffect(statusEffect)
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

        private val shakeActorAction = ShakeActorAction(
            xShake, yShake, xSpeedMultiplier, ySpeedMultiplier
        )

        init {
            shakeActorAction.duration = shakeDuration
        }

        override fun copy(): Effect = Destroy(trigger)

        override fun onTrigger(gameScreenController: GameScreenController): Timeline = Timeline.timeline {
            includeLater(
                { Timeline.timeline {
                    delay(bufferTime)
                    action { card.actor.addAction(shakeActorAction) }
                    delayUntil { shakeActorAction.isComplete }
                    action {
                        card.actor.removeAction(shakeActorAction)
                        shakeActorAction.reset()
                    }
                    delay(bufferTime)
                    action { gameScreenController.destroyCardPhase() }
                    delayUntil { gameScreenController.currentPhase != GameScreenController.Gamephase.CARD_DESTROY }
                } },
                { gameScreenController.hasDestroyableCard() }
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

        override fun onTrigger(gameScreenController: GameScreenController): Timeline = Timeline.timeline {
            action {
                val addMax = gameScreenController.maxCards - gameScreenController.cardHand!!.cards.size
                repeat(min(amount, addMax)) { gameScreenController.putCardInHand(cardName) }
            }
        }

        override fun toString(): String {
            return "PutCardInHand(trigger=$trigger, card=$card, amount=$amount)"
        }
    }

    protected fun shakeCardTimeline(shakeActorAction: ShakeActorAction): Timeline = Timeline.timeline {
        action { card.actor.addAction(shakeActorAction) }
        delayUntil { shakeActorAction.isComplete }
        action {
            card.actor.removeAction(shakeActorAction)
            shakeActorAction.reset()
        }
        delay(bufferTime)
    }

    companion object {

        private var xShake by Delegates.notNull<Float>()
        private var yShake by Delegates.notNull<Float>()
        private var xSpeedMultiplier by Delegates.notNull<Float>()
        private var ySpeedMultiplier by Delegates.notNull<Float>()
        private var shakeDuration by Delegates.notNull<Float>()

        private var bufferTime by Delegates.notNull<Int>()

        fun init(config: OnjObject) {

            val shakeOnj = config.get<OnjObject>("shakeAnimation")

            xShake = shakeOnj.get<Double>("xShake").toFloat()
            yShake = shakeOnj.get<Double>("yShake").toFloat()
            xSpeedMultiplier = shakeOnj.get<Double>("xSpeed").toFloat()
            ySpeedMultiplier = shakeOnj.get<Double>("ySpeed").toFloat()
            shakeDuration = shakeOnj.get<Double>("duration").toFloat()

            bufferTime = (config.get<Double>("bufferTime") * 1000).toInt()

            ReserveGain.init(config)
            BuffDamage.init(config)
            GiftDamage.init(config)
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
