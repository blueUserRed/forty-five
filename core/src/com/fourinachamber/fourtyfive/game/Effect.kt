package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.game.enemy.DamagePlayerEnemyAction
import com.fourinachamber.fourtyfive.screen.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
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
                action { card.actor.addAction(shakeActorAction) }
                delayUntil { shakeActorAction.isComplete }
                action {
                    shakeActorAction.reset()
                    card.actor.removeAction(shakeActorAction)
                    gameScreenController.playGameAnimation(textAnimation)
                    gameScreenController.gainReserves(amount)
                }
                delayUntil { textAnimation.isFinished() }
            }
        }

        companion object {

            private var xShake by Delegates.notNull<Float>()
            private var yShake by Delegates.notNull<Float>()
            private var xSpeedMultiplier by Delegates.notNull<Float>()
            private var ySpeedMultiplier by Delegates.notNull<Float>()
            private var shakeDuration by Delegates.notNull<Float>()

            private lateinit var fontName: String
            private lateinit var fontColor: Color
            private var fontScale by Delegates.notNull<Float>()
            private var duration by Delegates.notNull<Int>()
            private var raiseHeight by Delegates.notNull<Float>()
            private var startFadeoutAt by Delegates.notNull<Int>()

            private var bufferTime by Delegates.notNull<Int>()

            fun init(config: OnjObject) {

                val shakeOnj = config.get<OnjObject>("shakeAnimation")

                xShake = shakeOnj.get<Double>("xShake").toFloat()
                yShake = shakeOnj.get<Double>("yShake").toFloat()
                xSpeedMultiplier = shakeOnj.get<Double>("xSpeed").toFloat()
                ySpeedMultiplier = shakeOnj.get<Double>("ySpeed").toFloat()
                shakeDuration = shakeOnj.get<Double>("duration").toFloat()

                val dmgOnj = config.get<OnjObject>("playerLivesAnimation")

                fontName = dmgOnj.get<String>("font")
                fontScale = dmgOnj.get<Double>("fontScale").toFloat()
                duration = (dmgOnj.get<Double>("duration") * 1000).toInt()
                raiseHeight = dmgOnj.get<Double>("raiseHeight").toFloat()
                startFadeoutAt = (dmgOnj.get<Double>("startFadeoutAt") * 1000).toInt()
                fontColor = Color.valueOf(dmgOnj.get<String>("positiveFontColor"))

                bufferTime = (config.get<Double>("bufferTime") * 1000).toInt()

            }

        }

    }

    companion object {

        fun init(config: OnjObject) {
            ReserveGain.init(config)
        }

    }

}

enum class Trigger {

    ON_ENTER, ON_SHOT, ON_ROUND_START

}
