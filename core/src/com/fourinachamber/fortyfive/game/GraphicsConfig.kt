package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
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
        val overlayActor = CustomImageActor(damageOverlayTexture, screen)
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

            override fun isFinished(timeline: Timeline): Boolean = anim.isFinished()
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

    fun iconName(name: String): String = iconConfig[name]!!.first

    fun iconScale(name: String): Float = iconConfig[name]!!.second

    fun encounterModifierDisplayName(modifier: EncounterModifier): String {
        val name = (modifier::class.simpleName ?: "").lowerCaseFirstChar()
        val config = encounterModifierConfig.getOr<OnjObject?>(name, null)
            ?: throw RuntimeException("unknown encounter modifier: $name")
        return config.get<String>("displayName")
    }

    fun encounterModifierIcon(modifier: EncounterModifier): String {
        val name = (modifier::class.simpleName ?: "").lowerCaseFirstChar()
        val config = encounterModifierConfig.getOr<OnjObject?>(name, null)
            ?: throw RuntimeException("unknown encounter modifier: $name")
        return config.get<String>("icon")
    }

    fun encounterModifierDescription(modifier: EncounterModifier): String {
        val name = (modifier::class.simpleName ?: "").lowerCaseFirstChar()
        val config = encounterModifierConfig.getOr<OnjObject?>(name, null)
            ?: throw RuntimeException("unknown encounter modifier: $name")
        return config.get<String>("description")
    }

    @MainThreadOnly
    fun cardFont(screen: OnjScreen): PixmapFont = ResourceManager.get(screen, cardFont)

    fun cardFontScale(): Float = cardFontScale

    fun cardFontColor(isDark: Boolean, situation: String): Color {
        if (situation !in arrayOf("normal", "increase", "decrease")) {
            throw RuntimeException("unknown situation for card font color: $situation")
        }
        return if (isDark) cardFontColors["dark-$situation"]!! else cardFontColors["light-$situation"]!!
    }

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

        iconConfig = config
            .get<OnjObject>("icons")
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

        val chargeOnj = config.get<OnjObject>("enemyChargeAnimation")

        xCharge = chargeOnj.get<Double>("xCharge").toFloat()
        yCharge = chargeOnj.get<Double>("yCharge").toFloat()
        chargeDuration = chargeOnj.get<Double>("duration").toFloat() / 2f // divide by two because anim is played twice
        chargeInterpolation = Utils.interpolationOrError(chargeOnj.get<String>("interpolation"))

        val cardOnj = config.get<OnjObject>("cardText")
        cardFont = cardOnj.get<String>("font")
        cardFontScale = cardOnj.get<Double>("fontScale").toFloat()
        val cardFontColors = mutableMapOf<String, Color>()
        cardOnj.get<OnjObject>("colorsForDarkCard").value.forEach { (key, value) ->
            cardFontColors["dark-$key"] = value.value as Color
        }
        cardOnj.get<OnjObject>("colorsForLightCard").value.forEach { (key, value) ->
            cardFontColors["light-$key"] = value.value as Color
        }
        this.cardFontColors = cardFontColors

        val keySelect = config.get<OnjObject>("keySelect")
        keySelectDrawable = keySelect.get<OnjString>("drawable").value

        val onShootPostProcessor = config.get<OnjObject>("shootPostProcessor")
        shootPostProcessor = onShootPostProcessor.get<String>("name")
        shootPostProcessorDuration = (onShootPostProcessor.get<Double>("duration") * 100).toInt()

        encounterModifierConfig = config.get<OnjObject>("encounterModifiers")
    }

    private lateinit var shootPostProcessor: String
    private var shootPostProcessorDuration: Int by Delegates.notNull()

    private lateinit var keySelectDrawable: String

    private var cardFont by Delegates.notNull<String>()
    private var cardFontScale by Delegates.notNull<Float>()
    private lateinit var cardFontColors: Map<String, Color>

    private lateinit var iconConfig: Map<String, Pair<String, Float>>

    var bufferTime by Delegates.notNull<Int>()
        private set

    private lateinit var damageOverlayTexture: String
    private var damageOverlayDuration by Delegates.notNull<Int>()
    private var damageOverlayFadeIn by Delegates.notNull<Int>()
    private var damageOverlayFadeOut by Delegates.notNull<Int>()

    private var xCharge by Delegates.notNull<Float>()
    private var yCharge by Delegates.notNull<Float>()
    private var chargeDuration by Delegates.notNull<Float>()
    private lateinit var chargeInterpolation: Interpolation

    private lateinit var encounterModifierConfig: OnjObject

}
