package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fourtyfive.screen.CustomImageActor
import com.fourinachamber.fourtyfive.screen.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.Timeline
import onj.OnjObject
import kotlin.math.floor
import kotlin.properties.Delegates

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

    private lateinit var gameScreenController: GameScreenController

    /**
     * the remaining amount of revolver-turns this effect will stay active for
     */
    val remainingTurns: Int
        get() = (startTurn + turns) - gameScreenController.turnCounter

    private var startTurn: Int = 0

    lateinit var icon: CustomImageActor
        private set

    protected var isIconInitialised: Boolean = false
        private set


    /**
     * creates a copy of this status effect
     */
    abstract fun copy(): StatusEffect

    fun initIcon(gameScreenController: GameScreenController) {
        val texture = gameScreenController.curScreen!!.textures[iconTextureName]
            ?: throw RuntimeException("no texture with name $iconTextureName")
        icon = CustomImageActor(texture)
        icon.setScale(iconScale)
        icon.reportDimensionsWithScaling = true
        icon.ignoreScalingWhenDrawing = true
        isIconInitialised = true
    }

    /**
     * called after the revolver turned
     */
    open fun onRevolverTurn(gameScreenController: GameScreenController) { }

    /**
     * called after the status effect got applied
     */
    open fun start(gameScreenController: GameScreenController) {
        this.gameScreenController = gameScreenController
        startTurn = gameScreenController.turnCounter
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
    open fun executeAfterRound(gameScreenController: GameScreenController): Timeline? = null


    /**
     * returns a timeline containing the actions of this effect; null if this status effect does nothing after the
     * target got damaged
     */
    open fun executeAfterDamage(gameScreenController: GameScreenController, damage: Int): Timeline? = null


    /**
     * returns a timeline containing the actions of this effect; null if this status effect does nothing after the
     * revolver turned
     */
    open fun executeAfterRevolverTurn(gameScreenController: GameScreenController): Timeline? = null


    /**
     * the poison effect damages the target every revolver turn
     */
    class Poison(
        val damage: Int,
        turns: Int,
        target: StatusEffectTarget
    ) : StatusEffect(poisonIconName, turns, target, poisonIconScale) {

        override fun copy(): StatusEffect = Poison(damage, turns, target)

        override fun executeAfterRevolverTurn(
            gameScreenController: GameScreenController
        ): Timeline = Timeline.timeline {

            FourtyFiveLogger.debug(logTag, "executing poison effect")

            val shakeActorAction = ShakeActorAction(xShake * 0.5f, yShake, xSpeedMultiplier, ySpeedMultiplier)
            shakeActorAction.duration = shakeDuration

            action { icon.addAction(shakeActorAction) }
            delayUntil { shakeActorAction.isComplete }
            delay(bufferTime)
            action {
                icon.removeAction(shakeActorAction)
                shakeActorAction.reset()
            }
            include(target.damage(gameScreenController, damage))

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
    ) : StatusEffect(burningIconName, turns, target, burningIconScale) {

        override fun copy(): StatusEffect = Burning(turns, percent, target)

        override fun executeAfterRound(gameScreenController: GameScreenController): Timeline? = null

        override fun executeAfterDamage(
            gameScreenController: GameScreenController,
            damage: Int
        ): Timeline = Timeline.timeline {

            FourtyFiveLogger.debug(logTag, "executing burning effect")

            val additionalDamage = floor(damage * percent).toInt()
            val shakeActorAction = ShakeActorAction(xShake * 0.5f, yShake, xSpeedMultiplier, ySpeedMultiplier)
            shakeActorAction.duration = shakeDuration

            delay(bufferTime)
            action { icon.addAction(shakeActorAction) }
            delayUntil { shakeActorAction.isComplete }
            delay(bufferTime)
            action {
                icon.removeAction(shakeActorAction)
                shakeActorAction.reset()
            }
            include(target.damage(gameScreenController, additionalDamage))
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

    companion object {

        private var xShake by Delegates.notNull<Float>()
        private var yShake by Delegates.notNull<Float>()
        private var xSpeedMultiplier by Delegates.notNull<Float>()
        private var ySpeedMultiplier by Delegates.notNull<Float>()
        private var shakeDuration by Delegates.notNull<Float>()

        private lateinit var dmgFontName: String
        private lateinit var dmgFontColorNegative: Color
        private lateinit var dmgFontColorPositive: Color
        private var dmgFontScale by Delegates.notNull<Float>()
        private var dmgDuration by Delegates.notNull<Int>()
        private var dmgRaiseHeight by Delegates.notNull<Float>()
        private var dmgStartFadeoutAt by Delegates.notNull<Int>()

        private lateinit var poisonIconName: String
        private var poisonIconScale by Delegates.notNull<Float>()
        private lateinit var burningIconName: String
        private var burningIconScale by Delegates.notNull<Float>()

        private var bufferTime by Delegates.notNull<Int>()

        fun init(config: OnjObject) {
            val shakeOnj = config.get<OnjObject>("shakeAnimation")

            xShake = shakeOnj.get<Double>("xShake").toFloat()
            yShake = shakeOnj.get<Double>("yShake").toFloat()
            xSpeedMultiplier = shakeOnj.get<Double>("xSpeed").toFloat()
            ySpeedMultiplier = shakeOnj.get<Double>("ySpeed").toFloat()
            shakeDuration = shakeOnj.get<Double>("duration").toFloat()

            val dmgOnj = config.get<OnjObject>("playerLivesAnimation")

            dmgFontName = dmgOnj.get<String>("font")
            dmgFontScale = dmgOnj.get<Double>("fontScale").toFloat()
            dmgDuration = (dmgOnj.get<Double>("duration") * 1000).toInt()
            dmgRaiseHeight = dmgOnj.get<Double>("raiseHeight").toFloat()
            dmgStartFadeoutAt = (dmgOnj.get<Double>("startFadeoutAt") * 1000).toInt()
            dmgFontColorPositive = Color.valueOf(dmgOnj.get<String>("positiveFontColor"))
            dmgFontColorNegative = Color.valueOf(dmgOnj.get<String>("negativeFontColor"))

            val statusIcons = config.get<OnjObject>("statusEffectIcons")

            poisonIconName = statusIcons.get<String>("poison")
            poisonIconScale = statusIcons.get<Double>("poisonScale").toFloat()
            burningIconName = statusIcons.get<String>("burning")
            burningIconScale = statusIcons.get<Double>("burningScale").toFloat()

            bufferTime = (config.get<Double>("bufferTime") * 1000).toInt()
        }

    }

    /**
     * represents a possible target a status effect can be applied to
     */
    enum class StatusEffectTarget {

        PLAYER {
            override fun getLivesActor(gameScreenController: GameScreenController): Actor {
                return gameScreenController.playerLivesLabel!!
            }
            override fun damage(gameScreenController: GameScreenController, damage: Int): Timeline {
                return Timeline.timeline { //TODO: ??????????????
                    action { gameScreenController.damagePlayer(damage) }
                }
            }
        },

        ENEMY {
            override fun getLivesActor(gameScreenController: GameScreenController): Actor {
                return gameScreenController.enemyArea!!.enemies[0].actor.livesLabel
            }

            override fun damage(gameScreenController: GameScreenController, damage: Int): Timeline {
                return gameScreenController.enemyArea!!.enemies[0].damage(damage, gameScreenController)
            }
        }
        ;

        /**
         * returns the actor displaying the current and/or base lives of the target
         */
        abstract fun getLivesActor(gameScreenController: GameScreenController): Actor

        /**
         * returns a timeline containing the necessary actions to damage the target
         */
        abstract fun damage(gameScreenController: GameScreenController, damage: Int): Timeline
    }

}
