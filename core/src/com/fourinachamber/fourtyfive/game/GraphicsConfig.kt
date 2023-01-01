package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.screen.gameComponents.CoverStack
import com.fourinachamber.fourtyfive.screen.general.CustomImageActor
import com.fourinachamber.fourtyfive.screen.general.CustomMoveByAction
import com.fourinachamber.fourtyfive.screen.general.CustomParticleActor
import com.fourinachamber.fourtyfive.screen.general.ShakeActorAction
import com.fourinachamber.fourtyfive.utils.ActorActionTimelineAction
import com.fourinachamber.fourtyfive.utils.GameAnimationTimelineAction
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.utils.Utils
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.value.OnjObject
import kotlin.properties.Delegates

object GraphicsConfig {

    const val graphicsConfigFile: String = "config/graphics_config.onj"
    const val graphicsConfigSchemaFile: String = "onjschemas/graphics_config.onjschema"

    fun init() {
        val config = OnjParser.parseFile(Gdx.files.internal(graphicsConfigFile).file())
        val schema = OnjSchemaParser.parseFile(Gdx.files.internal(graphicsConfigSchemaFile).file())
        schema.assertMatches(config)
        config as OnjObject
        readConstants(config)
    }

    fun damageOverlay(): Timeline.TimelineAction {
        val screen = FourtyFive.curScreen!!
        val overlayActor = CustomImageActor(screen.textureOrError(damageOverlayTexture))
        val viewport = screen.stage.viewport
        val anim = FadeInAndOutAnimation(
            0f, 0f,
            overlayActor,
            screen,
            damageOverlayDuration,
            damageOverlayFadeIn,
            damageOverlayFadeOut,
            Vector2(viewport.worldWidth, viewport.worldHeight)
        )
        return object : Timeline.TimelineAction() {

            override fun start(timeline: Timeline) {
                super.start(timeline)
                FourtyFive.currentGame!!.playGameAnimation(anim)
            }

            override fun isFinished(): Boolean = true
        }
    }

    fun chargeTimeline(actor: Actor): Timeline {
        val moveByAction = CustomMoveByAction()
        moveByAction.setAmount(xCharge, yCharge)
        moveByAction.duration = chargeDuration
        moveByAction.interpolation = chargeInterpolation
        return Timeline.timeline {

            action { actor.addAction(moveByAction) }
            delayUntil { moveByAction.isComplete }
            action {
                actor.removeAction(moveByAction)
                moveByAction.reset()
                moveByAction.amountX = -moveByAction.amountX
                moveByAction.amountY = -moveByAction.amountY
                actor.addAction(moveByAction)
            }
            delayUntil { moveByAction.isComplete }
            action { actor.removeAction(moveByAction) }
        }
    }

    fun coverStackParticles(destroyed: Boolean, coverStack: CoverStack): Timeline.TimelineAction {
        return object : Timeline.TimelineAction() {

            var particle: ParticleEffect? = null

            override fun start(timeline: Timeline) {
                super.start(timeline)
                particle = FourtyFive.curScreen!!.particleOrError(
                    if (destroyed) coverStackDestroyedParticles else coverStackDamagedParticles
                )

                val particleActor = CustomParticleActor(particle!!)
                particleActor.isAutoRemove = true
                particleActor.fixedZIndex = Int.MAX_VALUE

                if (destroyed) {
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

                FourtyFive.curScreen!!.addActorToRoot(particleActor)
                particleActor.start()
            }

            override fun isFinished(): Boolean = particle?.isComplete ?: true

        }
    }

    fun rawTemplateString(name: String): String = rawTemplateStrings[name]!!

    fun iconName(name: String): String = iconConfig[name]!!.first

    fun iconScale(name: String): Float = iconConfig[name]!!.second

    fun shakeActorAnimation(actor: Actor, reduce: Boolean): ActorActionTimelineAction {
        val action = ShakeActorAction(
            if (reduce) xShake * 0.5f else xShake,
            if (reduce) yShake * 0.5f else yShake,
            xShakeSpeedMultiplier,
            yShakeSpeedMultiplier
        )
        action.duration = shakeDuration
        return ActorActionTimelineAction(action, actor)
    }

    fun bannerAnimation(isPlayer: Boolean): GameAnimationTimelineAction {
        val onjScreen = FourtyFive.curScreen!!
        val anim = BannerAnimation(
            onjScreen.textureOrError(if (isPlayer) playerTurnBannerName else enemyTurnBannerName),
            onjScreen,
            bannerAnimDuration,
            bannerScaleAnimDuration,
            bannerBeginScale,
            bannerEndScale
        )
        return GameAnimationTimelineAction(anim)
    }

    fun insultFadeAnimation(pos: Vector2, insult: String): GameAnimationTimelineAction {
        val curScreen = FourtyFive.curScreen!!
        val anim = FadeInAndOutTextAnimation(
            pos.x, pos.y,
            insult,
            fadeFontColor,
            fadeFontScale,
            curScreen.fontOrError(fadeFontName),
            curScreen,
            fadeDuration,
            fadeIn,
            fadeOut
        )
        return GameAnimationTimelineAction(anim)
    }

    fun numberChangeAnimation(
        pos: Vector2,
        text: String,
        raise: Boolean,
        positive: Boolean
    ): GameAnimationTimelineAction {
        val curScreen = FourtyFive.curScreen!!
        val anim = TextAnimation(
            pos.x, pos.y,
            text,
            if (positive) numChangePositiveFontColor else numChangeNegativeFontColor,
            numChangeFontScale,
            curScreen.fontOrError(numChangeFontName),
            if (raise) numChangeRaiseDistance else numChangeSinkDistance,
            numChangeStartFadeoutAt,
            curScreen,
            numChangeDuration
        )
        return GameAnimationTimelineAction(anim)
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Beware of ugly code below
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun readConstants(config: OnjObject) {
        bufferTime = (config.get<Double>("bufferTime") * 1000).toInt()

        rawTemplateStrings = config
            .get<OnjObject>("stringTemplates")
            .value
            .mapValues { it.value.value as String }

        iconConfig = config
            .get<OnjObject>("statusEffectIcons")
            .value
            .mapValues {
                val obj = it.value as OnjObject
                obj.get<String>("icon") to obj.get<Double>("scale").toFloat()
            }

        val damageOverlay = config.get<OnjObject>("damageOverlay")

        damageOverlayTexture = damageOverlay.get<String>("overlay")
        damageOverlayDuration = (damageOverlay.get<Double>("duration") * 1000).toInt()
        damageOverlayFadeIn = (damageOverlay.get<Double>("fadeIn") * 1000).toInt()
        damageOverlayFadeOut = (damageOverlay.get<Double>("fadeOut") * 1000).toInt()

        val coverStackParticles = config.get<OnjObject>("coverStackParticles")

        coverStackDamagedParticles = coverStackParticles.get<String>("damaged")
        coverStackDestroyedParticles = coverStackParticles.get<String>("destroyed")

        val bannerOnj = config.get<OnjObject>("bannerAnimation")

        bannerAnimDuration = (bannerOnj.get<Double>("duration") * 1000).toInt()
        bannerScaleAnimDuration = (bannerOnj.get<Double>("scaleAnimDuration") * 1000).toInt()
        bannerBeginScale = bannerOnj.get<Double>("beginScale").toFloat()
        bannerEndScale = bannerOnj.get<Double>("endScale").toFloat()

        playerTurnBannerName = bannerOnj.get<String>("playerTurnBanner")
        enemyTurnBannerName = bannerOnj.get<String>("enemyTurnBanner")

        val shakeOnj = config.get<OnjObject>("shakeAnimation")

        xShake = shakeOnj.get<Double>("xShake").toFloat()
        yShake = shakeOnj.get<Double>("yShake").toFloat()
        xShakeSpeedMultiplier = shakeOnj.get<Double>("xSpeed").toFloat()
        yShakeSpeedMultiplier = shakeOnj.get<Double>("ySpeed").toFloat()
        shakeDuration = shakeOnj.get<Double>("duration").toFloat()

        val quickChargeOnj = config.get<OnjObject>("enemyQuickChargeAnimation")

        xQuickCharge = quickChargeOnj.get<Double>("xCharge").toFloat()
        yQuickCharge = quickChargeOnj.get<Double>("yCharge").toFloat()
        quickChargeDuration = quickChargeOnj.get<Double>("duration").toFloat() / 2f // divide by two because anim is played twice
        quickChargeInterpolation = Utils.interpolationOrError(quickChargeOnj.get<String>("interpolation"))

        val chargeOnj = config.get<OnjObject>("enemyChargeAnimation")

        xCharge = chargeOnj.get<Double>("xCharge").toFloat()
        yCharge = chargeOnj.get<Double>("yCharge").toFloat()
        chargeDuration = chargeOnj.get<Double>("duration").toFloat() / 2f // divide by two because anim is played twice
        chargeInterpolation = Utils.interpolationOrError(chargeOnj.get<String>("interpolation"))

        val numChange = config.get<OnjObject>("numberChangeAnimation")

        numChangeFontName = numChange.get<String>("font")
        numChangeFontScale = numChange.get<Double>("fontScale").toFloat()
        numChangeDuration = (numChange.get<Double>("duration") * 1000).toInt()
        numChangeRaiseDistance = numChange.get<Double>("raiseDistance").toFloat()
        numChangeSinkDistance = numChange.get<Double>("sinkDistance").toFloat()
        numChangeStartFadeoutAt = (numChange.get<Double>("startFadeoutAt") * 1000).toInt()
        numChangeNegativeFontColor = numChange.get<Color>("negativeFontColor")
        numChangePositiveFontColor = numChange.get<Color>("positiveFontColor")

        val coverStackOnj = config.get<OnjObject>("coverStackParticles")

        coverStackDamagedParticlesName = coverStackOnj.get<String>("damaged")
        coverStackDestroyedParticlesName = coverStackOnj.get<String>("destroyed")

        val fadeOnj = config.get<OnjObject>("insultFadeAnimation")

        fadeFontColor = fadeOnj.get<Color>("fadeFontColor")
        fadeFontName = fadeOnj.get<String>("fadeFontName")
        fadeFontScale = fadeOnj.get<Double>("fadeFontScale"). toFloat()
        fadeDuration = (fadeOnj.get<Double>("fadeDuration") * 1000).toInt()
        fadeIn = (fadeOnj.get<Double>("fadeIn") * 1000).toInt()
        fadeOut = (fadeOnj.get<Double>("fadeOut") * 1000).toInt()
    }

    private lateinit var rawTemplateStrings: Map<String, String>
    private lateinit var iconConfig: Map<String, Pair<String, Float>>

    var bufferTime by Delegates.notNull<Int>()
        private set

    private lateinit var damageOverlayTexture: String
    private var damageOverlayDuration by Delegates.notNull<Int>()
    private var damageOverlayFadeIn by Delegates.notNull<Int>()
    private var damageOverlayFadeOut by Delegates.notNull<Int>()

    private lateinit var coverStackDamagedParticles: String
    private lateinit var coverStackDestroyedParticles: String

    private var bannerAnimDuration by Delegates.notNull<Int>()
    private var bannerScaleAnimDuration by Delegates.notNull<Int>()
    private var bannerBeginScale by Delegates.notNull<Float>()
    private var bannerEndScale by Delegates.notNull<Float>()

    private var xShake by Delegates.notNull<Float>()
    private var yShake by Delegates.notNull<Float>()
    private var xShakeSpeedMultiplier by Delegates.notNull<Float>()
    private var yShakeSpeedMultiplier by Delegates.notNull<Float>()
    private var shakeDuration by Delegates.notNull<Float>()

    private var xQuickCharge by Delegates.notNull<Float>()
    private var yQuickCharge by Delegates.notNull<Float>()
    private var quickChargeDuration by Delegates.notNull<Float>()
    private lateinit var quickChargeInterpolation: Interpolation

    private lateinit var playerTurnBannerName: String
    private lateinit var enemyTurnBannerName: String

    private var xCharge by Delegates.notNull<Float>()
    private var yCharge by Delegates.notNull<Float>()
    private var chargeDuration by Delegates.notNull<Float>()
    private lateinit var chargeInterpolation: Interpolation

    private lateinit var numChangeFontName: String
    private lateinit var numChangeNegativeFontColor: Color
    private lateinit var numChangePositiveFontColor: Color
    private var numChangeFontScale by Delegates.notNull<Float>()
    private var numChangeDuration by Delegates.notNull<Int>()
    private var numChangeRaiseDistance by Delegates.notNull<Float>()
    private var numChangeSinkDistance by Delegates.notNull<Float>()
    private var numChangeStartFadeoutAt by Delegates.notNull<Int>()

    private lateinit var coverStackDestroyedParticlesName: String
    private lateinit var coverStackDamagedParticlesName: String

    private lateinit var fadeFontName: String
    private lateinit var fadeFontColor: Color
    private var fadeFontScale by Delegates.notNull<Float>()
    private var fadeDuration by Delegates.notNull<Int>()
    private var fadeIn by Delegates.notNull<Int>()
    private var fadeOut by Delegates.notNull<Int>()

}
