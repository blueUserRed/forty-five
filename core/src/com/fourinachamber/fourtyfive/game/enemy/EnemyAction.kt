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

abstract class EnemyAction {

    abstract val indicatorTexture: TextureRegion

    abstract val descriptionText: String

    abstract fun execute(gameScreenController: GameScreenController): Timeline

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

    override val descriptionText: String = damage.toString()

    private val xShake: Float
    private val yShake: Float
    private val xSpeedMultiplier: Float
    private val ySpeedMultiplier: Float
    private val shakeDuration: Float

    private val xCharge: Float
    private val yCharge: Float
    private val chargeDuration: Float
    private val chargeInterpolation: Interpolation

    private val dmgFont: BitmapFont
    private val dmgFontColor: Color
    private val dmgFontScale: Float
    private val dmgDuration: Int
    private val dmgRaiseHeight: Float
    private val dmgStartFadeoutAt: Int

    private val bufferTime: Int

    init {

        //TODO: find a better way to do this

        val effects = onj.get<OnjObject>("effects")

        xShake = effects.get<Double>("xShake").toFloat()
        yShake = effects.get<Double>("yShake").toFloat()
        xSpeedMultiplier = effects.get<Double>("xShakeSpeed").toFloat()
        ySpeedMultiplier = effects.get<Double>("yShakeSpeed").toFloat()
        shakeDuration = effects.get<Double>("shakeDuration").toFloat()

        xCharge = effects.get<Double>("xCharge").toFloat()
        yCharge = effects.get<Double>("yCharge").toFloat()
        chargeDuration = effects.get<Double>("chargeDuration").toFloat() / 2f // divide by two because anim is played twice
        chargeInterpolation = Utils.interpolationOrError(effects.get<String>("chargeInterpolation"))

        dmgFont = screenDataProvider.fonts[effects.get<String>("dmgFont")] ?:
            throw RuntimeException("unknown font ${effects.get<String>("dmgFont")}")
        dmgFontScale = effects.get<Double>("dmgFontScale").toFloat()
        dmgDuration = (effects.get<Double>("dmgDuration") * 1000).toInt()
        dmgRaiseHeight = effects.get<Double>("dmgRaiseHeight").toFloat()
        dmgStartFadeoutAt = (effects.get<Double>("dmgStartFadeoutAt") * 1000).toInt()
        dmgFontColor = Color.valueOf(effects.get<String>("dmgFontColor"))

        bufferTime = (effects.get<Double>("bufferTime") * 1000).toInt()
    }

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

        //TODO: improve timeline

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

        delay(bufferTime)

        action {
            remaining = gameScreenController.coverArea!!.damage(damage)
            if (remaining != damage) activeStack = gameScreenController.coverArea!!.getActive()
//            activeStack?.addAction(shakeAction)
        }

        includeLater(
            {
                getStackParticlesTimeline(activeStack!!, screenDataProvider, activeStack!!.currentHealth == 0)
            },
            { activeStack != null }
        )

//        delayUntil { shakeAction.isComplete }

        action {
//            activeStack?.removeAction(shakeAction)
//            shakeAction.reset()
        }

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

}
