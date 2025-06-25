package com.microwavestudios.fortyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.rendering.BetterShader
import com.microwavestudios.fortyfive.rendering.RenderPipeline
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.actors.CustomImageActor
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.properties.Delegates

// TODO: further cleanup needed

object GraphicsConfig {

    fun init() {
        val config = ConfigFileManager.getConfigFile("graphicsConfig")
        this.config = config
        readConstants(config)
    }

    fun damageOverlay(screen: OnjScreen, controller: GameController): Timeline.TimelineAction {
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
                controller.playGameAnimation(anim)
            }

            override fun isFinished(timeline: Timeline): Boolean = anim.isFinished()
        }
    }

    fun orbAnimation(
        source: Vector2,
        target: Vector2,
        isReserves: Boolean,
        renderPipeline: RenderPipeline,
        duration: Int = 300,
    ) = RenderPipeline.OrbAnimation(
        orbTexture = if (isReserves) "reserves_orb" else "card_orb",
        width = 10f,
        height = 10f,
        duration = duration,
        segments = 20,
        renderPipeline = renderPipeline,
        position = RenderPipeline.OrbAnimation.curvedPath(
            source,
            target,
            curveOffsetMultiplier = (-1.5f..1.5f).random()
        )
    )

    fun cashOrbAnimation(
        start: Vector2,
        end: Vector2,
        renderPipeline: RenderPipeline
    ) = RenderPipeline.OrbAnimation(
        orbTexture = "cash_symbol",
        width = 30f,
        height = 30f,
        duration = 600,
        segments = 20,
        renderPipeline = renderPipeline,
        position = RenderPipeline.OrbAnimation.curvedPath(start, end)
    )

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

    fun cardFont(borrower: ResourceBorrower, screen: OnjScreen): Promise<PixmapFont> =
        FortyFive.resourceManager.request(borrower, screen.lifetime, cardFont)

    fun cardFontScale(): Float = cardFontScale

    fun cardFontColor(isDark: Boolean, situation: String): Color {
        if (situation !in arrayOf("normal", "increase", "decrease")) {
            throw RuntimeException("unknown situation for card font color: $situation")
        }
        return if (isDark) cardFontColors["dark-$situation"]!! else cardFontColors["light-$situation"]!!
    }

    fun shootShader(borrower: ResourceBorrower, lifetime: Lifetime): Promise<BetterShader> =
        FortyFive.resourceManager.request(borrower, lifetime, shootPostProcessor)

    fun shootPostProcessingDuration(): Int = shootPostProcessorDuration

    fun encounterBackgroundFor(biome: String): ResourceHandle = config
        .get<OnjArray>("encounterBackgrounds")
        .value
        .map { it as OnjObject }
        .find { it.get<String>("biome") == biome }
        ?.get<String>("background")
        ?: throw RuntimeException("no background for biome $biome")

    fun secondaryBackgroundFor(biome: String): ResourceHandle = config
        .get<OnjArray>("encounterBackgrounds")
        .value
        .map { it as OnjObject }
        .find { it.get<String>("biome") == biome }
        ?.get<String>("secondaryBackground")
        ?: throw RuntimeException("no secondary background for biome $biome")

    fun revolverSlotIcon(slot: Int): ResourceHandle = slotIcons[slot - 1]

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Beware of ugly code below
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun readConstants(config: OnjObject) {

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

        val onShootPostProcessor = config.get<OnjObject>("shootPostProcessor")
        shootPostProcessor = onShootPostProcessor.get<String>("name")
        shootPostProcessorDuration = (onShootPostProcessor.get<Double>("duration") * 100).toInt()

        val slotIconConfig = config.get<OnjObject>("revolverSlotIcons")
        slotIcons = Array(5) {
            slotIconConfig.get<String>((it + 1).toString())
        }

        encounterModifierConfig = config.get<OnjObject>("encounterModifiers")
    }

    private lateinit var shootPostProcessor: String
    private var shootPostProcessorDuration: Int by Delegates.notNull()
//
//    private lateinit var keySelectDrawable: String
//
    private var cardFont by Delegates.notNull<String>()
    private var cardFontScale by Delegates.notNull<Float>()
    private lateinit var cardFontColors: Map<String, Color>
//    private lateinit var cardSavedSymbol: String
//    private lateinit var cardNotSavedSymbol: String
//
    private lateinit var iconConfig: Map<String, Pair<String, Float>>
//
//    var bufferTime by Delegates.notNull<Int>()
//        private set
//
    private lateinit var damageOverlayTexture: String
    private var damageOverlayDuration by Delegates.notNull<Int>()
    private var damageOverlayFadeIn by Delegates.notNull<Int>()
    private var damageOverlayFadeOut by Delegates.notNull<Int>()
//
//    private var xCharge by Delegates.notNull<Float>()
//    private var yCharge by Delegates.notNull<Float>()
//    private var chargeDuration by Delegates.notNull<Float>()
//    private lateinit var chargeInterpolation: Interpolation
//
    private lateinit var slotIcons: Array<ResourceHandle>
//
    private lateinit var encounterModifierConfig: OnjObject
//
    private lateinit var config: OnjObject

}
