package com.fourinachamber.fourtyfive.game.enemy

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.MoveByAction
import com.badlogic.gdx.scenes.scene2d.ui.ParticleEffectActor
import com.fourinachamber.fourtyfive.game.CoverStack
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.game.TextAnimation
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.screen.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.Utils
import com.fourinachamber.fourtyfive.utils.plus
import onj.OnjNamedObject
import onj.OnjObject
import onj.OnjValue
import kotlin.properties.Delegates

abstract class EnemyAction {

    abstract val indicatorTexture: TextureRegion

    abstract val descriptionText: String

    abstract fun execute(gameScreenController: GameScreenController): Timeline

    companion object {

        fun init(config: OnjObject) {
            DamagePlayerEnemyAction.init(config)
        }
    }

}

class DamagePlayerEnemyAction(
    val enemy: Enemy,
    onj: OnjNamedObject,
    private val screenDataProvider: ScreenDataProvider,
    val damage: Int
) : EnemyAction() {

    override val indicatorTexture: TextureRegion =
        screenDataProvider.textures[onj.get<String>("indicatorTexture")]
        ?: throw RuntimeException("unknown texture: ${onj.get<String>("indicatorTexture")}")

    private val coverStackDamagedParticles: ParticleEffect =
        screenDataProvider.particles[onj.get<String>("coverStackDamagedParticles")]
        ?: throw RuntimeException("unknown particle: ${onj.get<String>("coverStackDamagedParticles")}")

    private val coverStackDestroyedParticles: ParticleEffect =
        screenDataProvider.particles[onj.get<String>("coverStackDestroyedParticles")]
        ?: throw RuntimeException("unknown particle: ${onj.get<String>("coverStackDestroyedParticles")}")

    private val dmgFont: BitmapFont = screenDataProvider.fonts[dmgFontName] ?:
        throw RuntimeException("unknown font: $dmgFontName")

    override val descriptionText: String = damage.toString()

    override fun execute(gameScreenController: GameScreenController): Timeline = Timeline.timeline {
        val shakeAction = ShakeActorAction(xShake, yShake, xSpeedMultiplier, ySpeedMultiplier)
        shakeAction.duration = shakeDuration

        val moveByAction = MoveByAction()
        moveByAction.setAmount(xCharge, yCharge)
        moveByAction.duration = chargeDuration
        moveByAction.interpolation = chargeInterpolation

        val playerLivesLabel = gameScreenController.playerLivesLabel!!
        var playerLivesPos = playerLivesLabel.localToStageCoordinates(Vector2(0f, 0f))
        playerLivesPos += Vector2(playerLivesLabel.width / 2f, 0f)

        val textAnimation = TextAnimation(
            playerLivesPos.x,
            playerLivesPos.y,
            "If you see this something went wrong",
            dmgFontColor,
            dmgFontScale,
            dmgFont,
            dmgRaiseHeight,
            dmgStartFadeoutAt,
            gameScreenController.curScreen!!,
            dmgDuration
        )

        var activeStack: CoverStack? = null
        var remaining = 0

        action { enemy.actor.addAction(moveByAction) }

        delayUntil { moveByAction.isComplete }

        action {
            enemy.actor.removeAction(moveByAction)
            moveByAction.reset()
            moveByAction.amountX = -moveByAction.amountX
            moveByAction.amountY = -moveByAction.amountY
            enemy.actor.addAction(moveByAction)
        }

        delayUntil { moveByAction.isComplete }

        action { enemy.actor.removeAction(moveByAction) }

        action {
            remaining = gameScreenController.coverArea!!.damage(damage)
            if (remaining != damage) activeStack = gameScreenController.coverArea!!.getActive()
        }

        includeLater(
            {
                getStackParticlesTimeline(activeStack!!, screenDataProvider, activeStack!!.currentHealth == 0)
            },
            { activeStack != null }
        )

        delay(bufferTime)

        includeLater(
            { getPlayerDamagedTimeline(remaining, shakeAction, gameScreenController, textAnimation) },
            { remaining != 0 }
        )
    }

    private fun getPlayerDamagedTimeline(
        damage: Int,
        shakeAction: ShakeActorAction,
        gameScreenController: GameScreenController,
        textAnimation: TextAnimation
    ): Timeline {

        val playerLivesLabel = gameScreenController.playerLivesLabel!!

        return Timeline.timeline {

            action {
                gameScreenController.damagePlayer(damage)
                playerLivesLabel.addAction(shakeAction)
                textAnimation.text = "-$damage"
                gameScreenController.playGameAnimation(textAnimation)
            }

            delayUntil { textAnimation.isFinished() }

        }
    }

    private fun getStackParticlesTimeline(
        coverStack: CoverStack,
        screenDataProvider: ScreenDataProvider,
        wasDestroyed: Boolean
    ): Timeline {

        var particle: ParticleEffect? = null

        return Timeline.timeline {

            delay(bufferTime)

            action {
                particle = if (wasDestroyed) coverStackDestroyedParticles else coverStackDamagedParticles

                val particleActor = ParticleEffectActor(particle, true)
                particleActor.isAutoRemove = true

                if (wasDestroyed) {
                    particleActor.setPosition(
                        coverStack.x + coverStack.width / 2,
                        coverStack.y + coverStack.height / 2
                    )
                } else {
                    val width = particle!!.emitters[0].spawnWidth.highMax
                    particleActor.setPosition(
                        coverStack.x + coverStack.width / 2 - width / 2,
                        coverStack.y
                    )
                }

                screenDataProvider.addActorToRoot(particleActor)
                particleActor.start()
            }

            delayUntil { particle?.isComplete ?: true }

        }
    }

    companion object {

        private var xShake by Delegates.notNull<Float>()
        private var yShake by Delegates.notNull<Float>()
        private var xSpeedMultiplier by Delegates.notNull<Float>()
        private var ySpeedMultiplier by Delegates.notNull<Float>()
        private var shakeDuration by Delegates.notNull<Float>()

        private var xCharge by Delegates.notNull<Float>()
        private var yCharge by Delegates.notNull<Float>()
        private var chargeDuration by Delegates.notNull<Float>()
        private lateinit var chargeInterpolation: Interpolation

        private lateinit var dmgFontName: String
        private lateinit var dmgFontColor: Color
        private var dmgFontScale by Delegates.notNull<Float>()
        private var dmgDuration by Delegates.notNull<Int>()
        private var dmgRaiseHeight by Delegates.notNull<Float>()
        private var dmgStartFadeoutAt by Delegates.notNull<Int>()

        private var bufferTime by Delegates.notNull<Int>()

        fun init(config: OnjObject) {

            val shakeOnj = config.get<OnjObject>("shakeAnimation")

            xShake = shakeOnj.get<Double>("xShake").toFloat()
            yShake = shakeOnj.get<Double>("yShake").toFloat()
            xSpeedMultiplier = shakeOnj.get<Double>("xSpeed").toFloat()
            ySpeedMultiplier = shakeOnj.get<Double>("ySpeed").toFloat()
            shakeDuration = shakeOnj.get<Double>("duration").toFloat()

            val chargeOnj = config.get<OnjObject>("enemyChargeAnimation")

            xCharge = chargeOnj.get<Double>("xCharge").toFloat()
            yCharge = chargeOnj.get<Double>("yCharge").toFloat()
            chargeDuration = chargeOnj.get<Double>("duration").toFloat() / 2f // divide by two because anim is played twice
            chargeInterpolation = Utils.interpolationOrError(chargeOnj.get<String>("interpolation"))

            val dmgOnj = config.get<OnjObject>("playerLivesAnimation")

            dmgFontName = dmgOnj.get<String>("font")
            dmgFontScale = dmgOnj.get<Double>("fontScale").toFloat()
            dmgDuration = (dmgOnj.get<Double>("duration") * 1000).toInt()
            dmgRaiseHeight = dmgOnj.get<Double>("raiseHeight").toFloat()
            dmgStartFadeoutAt = (dmgOnj.get<Double>("startFadeoutAt") * 1000).toInt()
            dmgFontColor = Color.valueOf(dmgOnj.get<String>("negativeFontColor"))

            bufferTime = (config.get<Double>("bufferTime") * 1000).toInt()
        }

    }

}
