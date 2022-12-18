package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fourtyfive.screen.CustomImageActor
import com.fourinachamber.fourtyfive.screen.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.Timeline
import onj.OnjObject
import kotlin.math.floor
import kotlin.properties.Delegates

abstract class      StatusEffect(
    private val iconTextureName: String,
    _turns: Int,
    protected val target: StatusEffectTarget,
    private val iconScale: Float
) {

    var turns: Int = _turns
        protected set

    private lateinit var gameScreenController: GameScreenController

    val remainingTurns: Int
        get() = (startTurn + turns) - gameScreenController.turnCounter

    private var startTurn: Int = 0

    lateinit var icon: CustomImageActor
        private set

    protected var isIconInitialised: Boolean = false
        private set

    fun initIcon(gameScreenController: GameScreenController) {
        val texture = gameScreenController.curScreen!!.textures[iconTextureName]
            ?: throw RuntimeException("no texture with name $iconTextureName")
        icon = CustomImageActor(texture)
        icon.setScale(iconScale)
        icon.reportDimensionsWithScaling = true
        icon.ignoreScalingWhenDrawing = true
        isIconInitialised = true
    }

    open fun onRevolverTurn(gameScreenController: GameScreenController) { }

    open fun start(gameScreenController: GameScreenController) {
        this.gameScreenController = gameScreenController
        startTurn = gameScreenController.turnCounter
    }

    open fun isStillValid(): Boolean = remainingTurns > 0

    abstract fun execute(gameScreenController: GameScreenController): Timeline?

    abstract fun canStackWith(effect: StatusEffect): Boolean

    abstract fun stack(effect: StatusEffect)

    open fun executeAfterDamage(gameScreenController: GameScreenController, damage: Int): Timeline? = null


    class Poison(
        val damage: Int,
        turns: Int,
        target: StatusEffectTarget
    ) : StatusEffect(poisonIconName, turns, target, poisonIconScale) {

        override fun execute(gameScreenController: GameScreenController): Timeline = Timeline.timeline {

            val shakeActorAction = ShakeActorAction(xShake, yShake, xSpeedMultiplier, ySpeedMultiplier)
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
    }

    class Burning(
        turns: Int,
        private val percent: Float,
        target: StatusEffectTarget
    ) : StatusEffect(burningIconName, turns, target, burningIconScale) {

        override fun execute(gameScreenController: GameScreenController): Timeline? = null

        override fun executeAfterDamage(
            gameScreenController: GameScreenController,
            damage: Int
        ): Timeline = Timeline.timeline {

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
        abstract fun getLivesActor(gameScreenController: GameScreenController): Actor
        abstract fun damage(gameScreenController: GameScreenController, damage: Int): Timeline
    }

}
