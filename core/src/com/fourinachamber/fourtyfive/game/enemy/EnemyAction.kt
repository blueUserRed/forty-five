package com.fourinachamber.fourtyfive.game.enemy

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.*
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.utils.Timeline
import onj.value.OnjNamedObject

abstract class EnemyAction {

    abstract val indicatorDrawable: Drawable
    abstract val indicatorScale: Float

    abstract val descriptionText: String

    abstract fun execute(): Timeline?

    class DamagePlayer(
        val enemy: Enemy,
        onj: OnjNamedObject,
        onjScreen: OnjScreen,
        override val indicatorScale: Float,
        val damage: Int
    ) : EnemyAction() {

        override val indicatorDrawable: Drawable = onjScreen.drawableOrError(onj.get<String>("indicatorTexture"))

        override val descriptionText: String = damage.toString()

        override fun execute(): Timeline = FourtyFive.currentGame!!.enemyArea.enemies[0].damagePlayer(damage)

        override fun toString(): String {
            return "DamagePlayer(damage=$damage)"
        }
    }

    class AddCover(
        val enemy: Enemy,
        onj: OnjNamedObject,
        onjScreen: OnjScreen,
        override val indicatorScale: Float,
        val coverValue: Int
    ) : EnemyAction() {

        override val indicatorDrawable: Drawable = onjScreen.drawableOrError(onj.get<String>("indicatorTexture"))
        override val descriptionText: String = coverValue.toString()

        override fun execute(): Timeline = Timeline.timeline {
            val textAnimation = GraphicsConfig.numberChangeAnimation(
                enemy.actor.coverText.localToStageCoordinates(Vector2(0f, 0f)),
                coverValue.toString(),
                true,
                true
            )

            action { enemy.currentCover += coverValue }
            includeAction(textAnimation)
            delay(GraphicsConfig.bufferTime)
        }


        override fun toString(): String {
            return "AddCover(cover=$coverValue)"
        }

    }

    class DoNothing(
        val insult: String,
        val enemy: Enemy,
        onj: OnjNamedObject,
        onjScreen: OnjScreen,
        override val indicatorScale: Float
        ) : EnemyAction() {

        override val indicatorDrawable: Drawable = onjScreen.drawableOrError(onj.get<String>("indicatorTexture"))
        override val descriptionText: String  = ""

        override fun execute(): Timeline = Timeline.timeline {
            val fadeAnimation = GraphicsConfig.insultFadeAnimation(
                enemy.actor.localToStageCoordinates(Vector2(0f, 0f)),
                insult
            )
            delayUntil { fadeAnimation.isFinished() }
            includeAction(fadeAnimation)
            delay(GraphicsConfig.bufferTime)
        }


        override fun toString(): String {
            return "DoNothing()"
        }

    }

}
