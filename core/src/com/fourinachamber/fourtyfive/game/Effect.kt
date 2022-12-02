package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.TemplateString
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import kotlinx.coroutines.awaitAll
import onj.OnjObject
import kotlin.properties.Delegates

abstract class Effect(val trigger: Trigger) {

    lateinit var card: Card

    abstract fun onTrigger(gameScreenController: GameScreenController): Timeline?

    fun checkTrigger(triggerToCheck: Trigger, gameScreenController: GameScreenController): Timeline? {
        if (triggerToCheck == trigger) return onTrigger(gameScreenController)
        return null
    }



    class ReserveGain(trigger: Trigger, val amount: Int) : Effect(trigger) {

        private val shakeActorAction = ShakeActorAction(
            xShake, yShake, xSpeedMultiplier, ySpeedMultiplier
        )

        init {
            shakeActorAction.duration = shakeDuration
        }

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
                    { shakeCardTimeline(card, shakeActorAction) },
                    { card.inGame }
                )
                action {
                    gameScreenController.playGameAnimation(textAnimation)
                    gameScreenController.gainReserves(amount)
                }
                delayUntil { textAnimation.isFinished() }
            }
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
                fontColor = Color.valueOf(plOnj.get<String>("positiveFontColor"))

                bufferTime = (config.get<Double>("bufferTime") * 1000).toInt()
            }

        }

    }

    class BuffDamage(trigger: Trigger, val amount: Int) : Effect(trigger) {

        override fun onTrigger(gameScreenController: GameScreenController): Timeline? {
            val modifier = Card.CardModifier(
                amount,
                TemplateString(
                    buffDetailTextRawString,
                    mapOf(
                        "text" to { if (amount > 0) "buff" else "debuff" },
                        "amount" to { amount },
                        "source" to { card.title }
                    )
                ),
            ) { card.inGame }

            for (i in 1..5) {
                val card = gameScreenController.revolver!!.getCardInSlot(i) ?: continue
                if (card !== this.card) card.addModifier(modifier)
            }
            return null
        }

        companion object {

            private lateinit var buffDetailTextRawString: String

            fun init(config: OnjObject) {
                val tmplOnj = config.get<OnjObject>("stringTemplates")
                buffDetailTextRawString = tmplOnj.get<String>("buffDetailText")
            }
        }

    }

    class Draw(trigger: Trigger, val amount: Int) : Effect(trigger) {

        private val shakeActorAction = ShakeActorAction(
            xShake, yShake, xSpeedMultiplier, ySpeedMultiplier
        )

        init {
            shakeActorAction.duration = shakeDuration
        }

        override fun onTrigger(gameScreenController: GameScreenController): Timeline = Timeline.timeline {
            delay(bufferTime)
            includeLater(
                { shakeCardTimeline(card, shakeActorAction) },
                { card.inGame }
            )
            action { gameScreenController.specialDraw(amount)}
            delayUntil { gameScreenController.currentPhase != GameScreenController.Gamephase.SPECIAL_DRAW }
        }
    }

    class GiveStatus(trigger: Trigger, val statusEffect: StatusEffect) : Effect(trigger) {

        override fun onTrigger(gameScreenController: GameScreenController): Timeline? {
            gameScreenController.enemyArea!!.enemies[0].applyEffect(statusEffect)
            return null
        }
    }

    class Destroy(trigger: Trigger) : Effect(trigger) {

        private val shakeActorAction = ShakeActorAction(
            xShake, yShake, xSpeedMultiplier, ySpeedMultiplier
        )

        init {
            shakeActorAction.duration = shakeDuration
        }

        override fun onTrigger(gameScreenController: GameScreenController): Timeline? {
            if (!gameScreenController.hasDestroyableCard()) return null
            return Timeline.timeline {
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
            }
        }
    }

    protected fun shakeCardTimeline(card: Card, shakeActorAction: ShakeActorAction): Timeline = Timeline.timeline {
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
        }

    }

}

enum class Trigger {

    ON_ENTER, ON_SHOT, ON_ROUND_START

}
