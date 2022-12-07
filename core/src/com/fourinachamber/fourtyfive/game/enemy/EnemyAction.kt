package com.fourinachamber.fourtyfive.game.enemy

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fourtyfive.game.CoverStack
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.game.TextAnimation
import com.fourinachamber.fourtyfive.screen.CustomMoveByAction
import com.fourinachamber.fourtyfive.screen.CustomParticleActor
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.screen.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.Utils
import com.fourinachamber.fourtyfive.utils.plus
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import onj.OnjNamedObject
import onj.OnjObject
import kotlin.properties.Delegates

abstract class EnemyAction {

    abstract val indicatorTexture: TextureRegion
    abstract val indicatorTextureScale: Float

    abstract val descriptionText: String

    abstract fun execute(gameScreenController: GameScreenController): Timeline

    class DamagePlayer(
        val enemy: Enemy,
        onj: OnjNamedObject,
        private val screenDataProvider: ScreenDataProvider,
        override val indicatorTextureScale: Float,
        val damage: Int
    ) : EnemyAction() {

        override val indicatorTexture: TextureRegion =
            screenDataProvider.textures[onj.get<String>("indicatorTexture")]
                ?: throw RuntimeException("unknown texture: ${onj.get<String>("indicatorTexture")}")

        override val descriptionText: String = damage.toString()

        override fun execute(gameScreenController: GameScreenController): Timeline =
            gameScreenController.enemyArea!!.enemies[0].damagePlayer(damage, gameScreenController)

    }

    class AddCover(
        val enemy: Enemy,
        onj: OnjNamedObject,
        private val screenDataProvider: ScreenDataProvider,
        override val indicatorTextureScale: Float,
        val coverValue: Int
    ) : EnemyAction() {

        override val indicatorTexture: TextureRegion = screenDataProvider.textures[onj.get<String>("indicatorTexture")]
            ?: throw RuntimeException("unknown texture ${onj.get<String>("indicatorTexture")}")

        override val descriptionText: String = coverValue.toString()

        override fun execute(gameScreenController: GameScreenController): Timeline = Timeline.timeline {

            val (x, y) = enemy.actor.coverText.localToStageCoordinates(Vector2(0f, 0f))

            val textAnimation = TextAnimation(
                x, y,
                coverValue.toString(),
                resFontColor,
                resFontScale,
                gameScreenController.curScreen!!.fonts[resFontName]!!,
                resRaiseHeight,
                resStartFadeoutAt,
                gameScreenController.curScreen!!,
                resDuration
            )

            action {
                enemy.currentCover += coverValue
                gameScreenController.playGameAnimation(textAnimation)
            }
            delayUntil { textAnimation.isFinished() }

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

        private lateinit var resFontName: String
        private lateinit var resFontColor: Color
        private var resFontScale by Delegates.notNull<Float>()
        private var resDuration by Delegates.notNull<Int>()
        private var resRaiseHeight by Delegates.notNull<Float>()
        private var resStartFadeoutAt by Delegates.notNull<Int>()

        private lateinit var coverStackDestroyedParticlesName: String
        private lateinit var coverStackDamagedParticlesName: String

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

            val plOnj = config.get<OnjObject>("reservesAnimation")

            resFontName = plOnj.get<String>("font")
            resFontScale = plOnj.get<Double>("fontScale").toFloat()
            resDuration = (plOnj.get<Double>("duration") * 1000).toInt()
            resRaiseHeight = plOnj.get<Double>("raiseHeight").toFloat()
            resStartFadeoutAt = (plOnj.get<Double>("startFadeoutAt") * 1000).toInt()
            resFontColor = Color.valueOf(plOnj.get<String>("positiveFontColor"))

            val coverStackOnj = config.get<OnjObject>("coverStackParticles")

            coverStackDamagedParticlesName = coverStackOnj.get<String>("damaged")
            coverStackDestroyedParticlesName = coverStackOnj.get<String>("destroyed")

            bufferTime = (config.get<Double>("bufferTime") * 1000).toInt()
        }

    }

}
