package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.value.OnjObject
import onj.value.OnjString
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

    @MainThreadOnly
    fun damageOverlay(screen: OnjScreen): Timeline.TimelineAction {
        val overlayActor = CustomImageActor( damageOverlayTexture, screen)
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
                FortyFive.currentGame!!.playGameAnimation(anim)
            }

            override fun isFinished(timeline: Timeline): Boolean = true
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

    fun rawTemplateString(name: String): String = rawTemplateStrings[name]!!

    fun iconName(name: String): String = iconConfig[name]!!.first

    fun iconScale(name: String): Float = iconConfig[name]!!.second

    // TODO: can probably be moved to Card
    fun cardHighlightEffect(card: Card): Timeline.TimelineAction = when (card.highlightType) {

        Card.HighlightType.STANDARD -> card.actor.growAnimation(false).asAction()
        Card.HighlightType.GLOW -> card.actor.growAnimation(true).asAction()

    }

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

    @MainThreadOnly
    fun insultFadeAnimation(pos: Vector2, insult: String, screen: OnjScreen): GameAnimationTimelineAction {
        val anim = FadeInAndOutTextAnimation(
            screen,
            pos.x, pos.y,
            insult,
            fadeFontColor,
            fadeFontScale,
            ResourceManager.get(screen, fadeFontName),
            screen,
            fadeDuration,
            fadeIn,
            fadeOut
        )
        return GameAnimationTimelineAction(anim)
    }

    @MainThreadOnly
    fun numberChangeAnimation(
        pos: Vector2,
        text: String,
        raise: Boolean,
        positive: Boolean,
        screen: OnjScreen
    ): GameAnimationTimelineAction {
        val anim = TextAnimation(
            screen,
            pos.x, pos.y,
            text,
            if (positive) numChangePositiveFontColor else numChangeNegativeFontColor,
            numChangeFontScale,
            ResourceManager.get(screen, numChangeFontName),
            if (raise) numChangeRaiseDistance else numChangeSinkDistance,
            numChangeStartFadeoutAt,
            screen,
            numChangeDuration
        )
        return GameAnimationTimelineAction(anim)
    }

    @MainThreadOnly
    fun cardDetailFont(screen: OnjScreen): BitmapFont = ResourceManager.get(screen, cardDetailFont)
    fun cardDetailFontScale(): Float = cardDetailFontScale
    fun cardDetailFontColor(): Color = cardDetailFontColor

    @MainThreadOnly
    fun cardDetailSeparator(screen: OnjScreen): Drawable = ResourceManager.get(screen, cardDetailSeparator)
    fun cardDetailSpacing(): Float = cardDetailSpacing

    @MainThreadOnly
    fun cardDetailBackground(): ResourceHandle = cardDetailBackground

    @MainThreadOnly
    fun keySelectDrawable(screen: OnjScreen): Drawable = ResourceManager.get(screen, keySelectDrawable)

    @MainThreadOnly
    fun shootShader(screen: OnjScreen): BetterShader = ResourceManager.get(screen, shootPostProcessor)
    fun shootPostProcessingDuration(): Int = shootPostProcessorDuration

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

        val fadeOnj = config.get<OnjObject>("insultFadeAnimation")

        fadeFontColor = fadeOnj.get<Color>("fadeFontColor")
        fadeFontName = fadeOnj.get<String>("fadeFontName")
        fadeFontScale = fadeOnj.get<Double>("fadeFontScale"). toFloat()
        fadeDuration = (fadeOnj.get<Double>("fadeDuration") * 1000).toInt()
        fadeIn = (fadeOnj.get<Double>("fadeIn") * 1000).toInt()
        fadeOut = (fadeOnj.get<Double>("fadeOut") * 1000).toInt()

        val cardDetailOnj = config.get<OnjObject>("cardDetailText")
        cardDetailFont = cardDetailOnj.get<String>("font")
        cardDetailFontScale = cardDetailOnj.get<Double>("fontScale").toFloat()
        cardDetailFontColor = cardDetailOnj.get<Color>("fontColor")
        cardDetailBackground = cardDetailOnj.get<String>("background")
        cardDetailSeparator = cardDetailOnj.get<String>("separator")
        cardDetailSpacing = cardDetailOnj.get<Double>("spacing").toFloat()

        val keySelect = config.get<OnjObject>("keySelect")
        keySelectDrawable = keySelect.get<OnjString>("drawable").value

        val onShootPostProcessor = config.get<OnjObject>("shootPostProcessor")
        shootPostProcessor = onShootPostProcessor.get<String>("name")
        shootPostProcessorDuration = (onShootPostProcessor.get<Double>("duration") * 100).toInt()
    }

    private lateinit var shootPostProcessor: String
    private var shootPostProcessorDuration: Int by Delegates.notNull()

    private lateinit var keySelectDrawable: String

    private var cardDetailFont by Delegates.notNull<String>()
    private var cardDetailFontScale by Delegates.notNull<Float>()
    private var cardDetailFontColor by Delegates.notNull<Color>()
    private var cardDetailSpacing by Delegates.notNull<Float>()
    private lateinit var cardDetailBackground: String
    private lateinit var cardDetailSeparator: String

    private lateinit var rawTemplateStrings: Map<String, String>
    private lateinit var iconConfig: Map<String, Pair<String, Float>>

    var bufferTime by Delegates.notNull<Int>()
        private set

    private lateinit var damageOverlayTexture: String
    private var damageOverlayDuration by Delegates.notNull<Int>()
    private var damageOverlayFadeIn by Delegates.notNull<Int>()
    private var damageOverlayFadeOut by Delegates.notNull<Int>()

    private var xShake by Delegates.notNull<Float>()
    private var yShake by Delegates.notNull<Float>()
    private var xShakeSpeedMultiplier by Delegates.notNull<Float>()
    private var yShakeSpeedMultiplier by Delegates.notNull<Float>()
    private var shakeDuration by Delegates.notNull<Float>()

    private var xQuickCharge by Delegates.notNull<Float>()
    private var yQuickCharge by Delegates.notNull<Float>()
    private var quickChargeDuration by Delegates.notNull<Float>()
    private lateinit var quickChargeInterpolation: Interpolation

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

    private lateinit var fadeFontName: String
    private lateinit var fadeFontColor: Color
    private var fadeFontScale by Delegates.notNull<Float>()
    private var fadeDuration by Delegates.notNull<Int>()
    private var fadeIn by Delegates.notNull<Int>()
    private var fadeOut by Delegates.notNull<Int>()

}
