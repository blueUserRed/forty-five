package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.Timeline
import kotlin.math.floor

/**
 * a status effect that can be applied to a [target]
 */
abstract class StatusEffect(
    private val iconHandle: ResourceHandle,
    _turns: Int,
    protected val target: StatusEffectTarget,
    private val iconScale: Float
) {

    /**
     * the total amount of revolver-turns this effect will stay active for
     */
    var turns: Int = _turns
        protected set

    private lateinit var gameController: GameController

    /**
     * the remaining amount of revolver-turns this effect will stay active for
     */
    val remainingTurns: Int
        get() = (startTurn + turns) - gameController.revolverRotationCounter

    private var startTurn: Int = 0

    lateinit var icon: CustomImageActor
        private set

    protected var isIconInitialised: Boolean = false
        private set


    /**
     * creates a copy of this status effect
     */
    abstract fun copy(): StatusEffect

    fun initIcon(gameController: GameController) {
        icon = CustomImageActor(iconHandle, gameController.curScreen)
        icon.setScale(iconScale)
        icon.reportDimensionsWithScaling = true
        icon.ignoreScalingWhenDrawing = true
        isIconInitialised = true
    }

    /**
     * called after the revolver turned
     */
    open fun onRevolverTurn(gameController: GameController) { }

    /**
     * called after the status effect got applied
     */
    open fun start(gameController: GameController) {
        this.gameController = gameController
        startTurn = gameController.revolverRotationCounter
    }

    /**
     * checks whether this status effect is still valid or should be removed
     */
    open fun isStillValid(): Boolean = remainingTurns > 0

    /**
     * checks if [effect] can be stacked with this
     */
    abstract fun canStackWith(effect: StatusEffect): Boolean

    /**
     * stacks an effect onto this one. check using [canStackWith] first
     */
    abstract fun stack(effect: StatusEffect)

    /**
     * returns a timeline containing the actions of this effect; null if this status effect does nothing after a round
     * finished
     */
    open fun executeAfterRound(gameController: GameController): Timeline? = null


    /**
     * returns a timeline containing the actions of this effect; null if this status effect does nothing after the
     * target got damaged
     */
    open fun executeAfterDamage(gameController: GameController, damage: Int): Timeline? = null


    /**
     * returns a timeline containing the actions of this effect; null if this status effect does nothing after the
     * revolver turned
     */
    open fun executeAfterRevolverTurn(gameController: GameController): Timeline? = null


    /**
     * the poison effect damages the target every revolver turn
     */
    class Poison(
        val damage: Int,
        turns: Int,
        target: StatusEffectTarget,
    ) : StatusEffect(
        GraphicsConfig.iconName("poison"),
        turns,
        target,
        GraphicsConfig.iconScale("poison")
    ) {

        override fun copy(): StatusEffect = Poison(damage, turns, target)

        override fun executeAfterRevolverTurn(
            gameController: GameController
        ): Timeline = Timeline.timeline {
            FortyFiveLogger.debug(logTag, "executing poison effect")
            val shakeActorAction = GraphicsConfig.shakeActorAnimation(icon, true)

            includeAction(shakeActorAction)
            delay(GraphicsConfig.bufferTime)
            include(target.damage(damage))
        }

        override fun canStackWith(effect: StatusEffect): Boolean {
            return effect is Poison && effect.damage == damage
        }

        override fun stack(effect: StatusEffect) {
            effect as Poison
            turns += effect.turns
        }

        override fun toString(): String {
            return "Poison(turns=$turns, damage=$damage)"
        }

        companion object {
            const val logTag = "StatusEffect-Poison"
        }
    }

    /**
     * the burning status increases the damage the target takes by a percentage
     */
    class Burning(
        turns: Int,
        private val percent: Float,
        target: StatusEffectTarget,
    ) : StatusEffect(
        GraphicsConfig.iconName("burning"),
        turns,
        target,
        GraphicsConfig.iconScale("burning"),
    ) {

        override fun copy(): StatusEffect = Burning(turns, percent, target)

        override fun executeAfterRound(gameController: GameController): Timeline? = null

        override fun executeAfterDamage(
            gameController: GameController,
            damage: Int
        ): Timeline = Timeline.timeline {

            FortyFiveLogger.debug(logTag, "executing burning effect")

            val additionalDamage = floor(damage * percent).toInt()
            val shakeActorAction = GraphicsConfig.shakeActorAnimation(icon, true)

            delay(GraphicsConfig.bufferTime)
            includeAction(shakeActorAction)
            delay(GraphicsConfig.bufferTime)
            include(target.damage(additionalDamage))
        }

        override fun canStackWith(effect: StatusEffect): Boolean {
            return effect is Burning && effect.percent == percent
        }

        override fun stack(effect: StatusEffect) {
            effect as Burning
            turns += effect.turns
        }

        override fun toString(): String {
            return "Burning(turns=$turns, percent=$percent)"
        }

        companion object {
            const val logTag = "StatusEffect-Burning"
        }
    }


    /**
     * represents a possible target a status effect can be applied to
     */
    enum class StatusEffectTarget {

        @Suppress("unused") // will be needed in the future
        PLAYER {
//            override fun getLivesActor(): Actor {
//                return FortyFive.currentGame!!.playerLivesLabel
//            }
            override fun damage(damage: Int): Timeline {
                return Timeline.timeline {
                    include(FortyFive.currentGame!!.damagePlayer(damage))
                }
            }
        },

        ENEMY {
//            override fun getLivesActor(): Actor {
//                return FortyFive.currentGame!!.enemyArea.getTargetedEnemy().actor.livesLabel
//            }

            override fun damage(damage: Int): Timeline {
                return FortyFive.currentGame!!.enemyArea.getTargetedEnemy().damage(damage)
            }
        }
        ;

//        /**
//         * returns the actor displaying the current and/or base lives of the target
//         */
//        abstract fun getLivesActor(): Actor

        /**
         * returns a timeline containing the necessary actions to damage the target
         */
        abstract fun damage(damage: Int): Timeline
    }

}
