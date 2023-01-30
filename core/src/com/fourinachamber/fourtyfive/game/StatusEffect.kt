package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.CustomImageActor
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.Timeline
import kotlin.math.floor

/**
 * a status effect that can be applied to a [target]
 */
abstract class StatusEffect(
    private val iconTextureName: String,
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
        get() = (startTurn + turns) - gameController.turnCounter

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
        val texture = ResourceManager.get<Drawable>(gameController.curScreen, iconTextureName)
        icon = CustomImageActor(texture)
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
        startTurn = gameController.turnCounter
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
        target: StatusEffectTarget
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
            FourtyFiveLogger.debug(logTag, "executing poison effect")
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
        target: StatusEffectTarget
    ) : StatusEffect(
        GraphicsConfig.iconName("burning"),
        turns,
        target,
        GraphicsConfig.iconScale("burning")
    ) {

        override fun copy(): StatusEffect = Burning(turns, percent, target)

        override fun executeAfterRound(gameController: GameController): Timeline? = null

        override fun executeAfterDamage(
            gameController: GameController,
            damage: Int
        ): Timeline = Timeline.timeline {

            FourtyFiveLogger.debug(logTag, "executing burning effect")

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

        PLAYER {
            override fun getLivesActor(): Actor {
                return FourtyFive.currentGame!!.playerLivesLabel
            }
            override fun damage(damage: Int): Timeline {
                return Timeline.timeline {
                    action { FourtyFive.currentGame!!.damagePlayer(damage) }
                }
            }
        },

        ENEMY {
            override fun getLivesActor(): Actor {
                return FourtyFive.currentGame!!.enemyArea.enemies[0].actor.livesLabel
            }

            override fun damage(damage: Int): Timeline {
                return FourtyFive.currentGame!!.enemyArea.enemies[0].damage(damage)
            }
        }
        ;

        /**
         * returns the actor displaying the current and/or base lives of the target
         */
        abstract fun getLivesActor(): Actor

        /**
         * returns a timeline containing the necessary actions to damage the target
         */
        abstract fun damage(damage: Int): Timeline
    }

}
