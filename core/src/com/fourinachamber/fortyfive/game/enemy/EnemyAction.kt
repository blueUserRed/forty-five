package com.fourinachamber.fortyfive.game.enemy

import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import com.fourinachamber.fortyfive.utils.Timeline
import onj.value.OnjNamedObject

/**
 * represents an action that the enemy can execute
 */
abstract class EnemyAction {

    /**
     * the Drawable that is drawn above the enemies head to indicate which action will be executed
     */
    abstract val indicatorDrawableHandle: ResourceHandle

    abstract val indicatorScale: Float

    abstract val descriptionText: String

    /**
     * returns a timeline that executes the action
     */
    @MainThreadOnly
    abstract fun execute(): Timeline?

    /**
     * action that damages the player
     */
    class DamagePlayer @MainThreadOnly constructor(
        val enemy: Enemy,
        onj: OnjNamedObject,
        override val indicatorScale: Float,
        val damage: Int
    ) : EnemyAction() {

        override val indicatorDrawableHandle = onj.get<String>("indicatorTexture")

        override val descriptionText: String = damage.toString()

        override fun execute(): Timeline = enemy.damagePlayer(damage, FortyFive.currentGame!!)

        override fun toString(): String {
            return "DamagePlayer(damage=$damage)"
        }
    }

    /**
     * actions that adds cover to the enemy
     */
    class AddCover @MainThreadOnly constructor(
        val enemy: Enemy,
        onj: OnjNamedObject,
        private val onjScreen: OnjScreen,
        override val indicatorScale: Float,
        val coverValue: Int
    ) : EnemyAction() {

        override val indicatorDrawableHandle = onj.get<String>("indicatorTexture")
        override val descriptionText: String = coverValue.toString()

        override fun execute(): Timeline = Timeline.timeline {
            val textAnimation = GraphicsConfig.numberChangeAnimation(
                enemy.actor.coverText.localToStageCoordinates(Vector2(0f, 0f)),
                coverValue.toString(),
                true,
                true,
                onjScreen
            )

            action { enemy.currentCover += coverValue }
            includeAction(textAnimation)
            delay(GraphicsConfig.bufferTime)
        }


        override fun toString(): String {
            return "AddCover(cover=$coverValue)"
        }

    }

    /**
     * the player insults the player and does nothing else
     */
    class DoNothing @MainThreadOnly constructor(
        val insult: String,
        val enemy: Enemy,
        onj: OnjNamedObject,
        private val onjScreen: OnjScreen,
        override val indicatorScale: Float
        ) : EnemyAction() {

        override val indicatorDrawableHandle = onj.get<String>("indicatorTexture")
        override val descriptionText: String  = ""

        override fun execute(): Timeline = Timeline.timeline {
            val fadeAnimation = GraphicsConfig.insultFadeAnimation(
                enemy.actor.localToStageCoordinates(Vector2(0f, 0f)),
                insult,
                onjScreen
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
