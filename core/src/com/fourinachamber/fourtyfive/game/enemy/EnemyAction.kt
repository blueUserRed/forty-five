package com.fourinachamber.fourtyfive.game.enemy

import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.actions.MoveByAction
import com.badlogic.gdx.scenes.scene2d.ui.ParticleEffectActor
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.game.CoverStack
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.screen.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.Utils
import onj.OnjNamedObject
import onj.OnjObject

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

    private val bufferTime: Int

    init {
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

        bufferTime = (effects.get<Double>("bufferTime") * 1000).toInt()
    }

    override fun execute(gameScreenController: GameScreenController): Timeline = Timeline.timeline {
        val shakeAction = ShakeActorAction(xShake, yShake, xSpeedMultiplier, ySpeedMultiplier)
        shakeAction.duration = shakeDuration

        val moveByAction = MoveByAction()
        moveByAction.setAmount(xCharge, yCharge)
        moveByAction.duration = chargeDuration
        moveByAction.interpolation = chargeInterpolation

        var activeStack: CoverStack? = null
        var remaining = 0
        var wasDamageAbsorbed = false

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
            wasDamageAbsorbed = remaining != damage
            if (wasDamageAbsorbed) activeStack = gameScreenController.coverArea!!.getActive()
            activeStack?.let {
                it.addAction(shakeAction)
                spawnParticlesForStack(it, gameScreenController.curScreen!!)
            }
        }
        delayUntil { !wasDamageAbsorbed || shakeAction.isComplete }
        delay(bufferTime)
        action {
            if (wasDamageAbsorbed) activeStack?.removeAction(shakeAction)
            shakeAction.reset()
            gameScreenController.damagePlayer(remaining)
            if (remaining != 0) gameScreenController.playerLivesLabel!!.addAction(shakeAction)
        }
        delayUntil { remaining == 0 || shakeAction.isComplete }
        action {
            if (remaining != 0) gameScreenController.playerLivesLabel!!.removeAction(shakeAction)
        }
    }

    private fun spawnParticlesForStack(coverStack: CoverStack, screenDataProvider: ScreenDataProvider) {
        val particleActor = ParticleEffectActor(coverStackDamagedParticles, true)
        particleActor.isAutoRemove = true
        val width = coverStackDamagedParticles.emitters[0].spawnWidth.highMax
        particleActor.setPosition(
            coverStack.x + coverStack.width / 2 - width / 2,
            coverStack.y
        )
        screenDataProvider.addActorToRoot(particleActor)
        particleActor.start()
    }

}
