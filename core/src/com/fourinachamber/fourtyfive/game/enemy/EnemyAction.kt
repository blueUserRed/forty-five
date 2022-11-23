package com.fourinachamber.fourtyfive.game.enemy

import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.ParticleEffectActor
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.game.CoverStack
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.screen.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.Utils

abstract class EnemyAction {

    abstract val indicatorTexture: TextureRegion

    abstract val descriptionText: String

    abstract fun execute(gameScreenController: GameScreenController): Timeline

}

class DamagePlayerEnemyAction(
    val damage: Int,
    override val indicatorTexture: TextureRegion,
    private val coverStackDamagedParticles: ParticleEffect
) : EnemyAction() {

    override val descriptionText: String = damage.toString()

    override fun execute(gameScreenController: GameScreenController): Timeline = Timeline.timeline {
        // TODO: put these numbers in onj file somewhere
        val shakeAction = ShakeActorAction(1.2f, 0f, 0.3f, 0f)
        shakeAction.duration = 1.5f
        var activeStack: CoverStack? = null
        var remaining = 0
        var wasDamageAbsorbed = false
        delay(200)
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
        delay(200)
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
