package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2

abstract class Effect(val trigger: Trigger) {

    lateinit var card: Card

    abstract fun onTrigger(gameScreenController: GameScreenController): Timeline?

    fun checkTrigger(triggerToCheck: Trigger, gameScreenController: GameScreenController): Timeline? {
        if (triggerToCheck == trigger) return onTrigger(gameScreenController)
        return null
    }

    class ReserveGain(trigger: Trigger, val amount: Int) : Effect(trigger) {

        val shakeActorAction = ShakeActorAction(
            1f, 0f, 0.2f, 0f
        )

        init {
            shakeActorAction.duration = 0.8f
        }

        override fun onTrigger(gameScreenController: GameScreenController): Timeline {

            val reservesLabel = gameScreenController.reservesLabel!!
            val (x, y) = reservesLabel.localToStageCoordinates(Vector2(0f, 0f))

            val textAnimation = TextAnimation(
                x + reservesLabel.width / 2,
                y,
                amount.toString(),
                Color.GREEN,
                0.2f,
                gameScreenController.curScreen!!.fonts["vanilla_whale"]!!,
                20f,
                1500,
                gameScreenController.curScreen!!,
                2000
            )

            println("hi")

            return Timeline.timeline {
                action { card.actor.addAction(shakeActorAction) }
                delayUntil { shakeActorAction.isComplete }
                action {
                    card.actor.removeAction(shakeActorAction)
                    gameScreenController.playGameAnimation(textAnimation)
                    gameScreenController.gainReserves(amount)
                }
                delayUntil { textAnimation.isFinished() }
            }
        }
    }

}

enum class Trigger {

    ON_ENTER, ON_SHOT, ON_ROUND_START

}
