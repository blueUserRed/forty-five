package com.microwavestudios.fortyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.rendering.RenderPipeline
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.RenderableScreen
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

    fun damageOverlay(screen: RenderableScreen, controller: GameController): Timeline.TimelineAction {
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

    fun cashOrbAnimation(
        start: Vector2,
        end: () -> Vector2,
        renderPipeline: RenderPipeline
    ) = RenderPipeline.OrbAnimation(
        orbTexture = "cash_symbol",
        width = 30f,
        height = 30f,
        segments = 20,
        renderPipeline = renderPipeline,
        initialPosition = start,
        target = end,
        acceleration = 200f,
        speedCap = 2500f,
        initialVelocity = Vector2(0, 0),
        velocityRampStart = 500,
        velocityRamp = 1.2f
    )

    fun iconName(name: String): String = iconConfig[name]!!

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

    fun cardFont(borrower: ResourceBorrower, screen: RenderableScreen): Promise<PixmapFont> =
        FortyFive.resourceManager.request(borrower, screen.lifetime, cardFont)

    fun cardFontScale(): Float = cardFontScale

    fun cardFontColor(isDark: Boolean, situation: String): Color {
        if (situation !in arrayOf("normal", "increase", "decrease")) {
            throw RuntimeException("unknown situation for card font color: $situation")
        }
        return if (isDark) cardFontColors["dark-$situation"]!! else cardFontColors["light-$situation"]!!
    }

    fun encounterBackgroundsFor(biome: String): List<Pair<ResourceHandle, Boolean>> = config
        .get<OnjArray>("biomeBackgrounds")
        .value
        .map { it as OnjObject }
        .find { it.get<String>("biome") == biome }
        ?.get<OnjArray>("backgrounds")
        ?.value
        ?.map {
            it as OnjObject
            it.get<String>("background") to it.get<Boolean>("isDark")
        }
        ?: throw RuntimeException("no background for biome: '$biome'")

    fun revolverSlotIcon(slot: Int): ResourceHandle = slotIcons[slot - 1]

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Beware of ugly code below
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun readConstants(config: OnjObject) {

        iconConfig = config
            .get<OnjObject>("icons")
            .value
            .mapValues { it.value.value as String }

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

        val slotIconConfig = config.get<OnjObject>("revolverSlotIcons")
        slotIcons = Array(5) {
            slotIconConfig.get<String>((it + 1).toString())
        }

        encounterModifierConfig = config.get<OnjObject>("encounterModifiers")
    }

    private var cardFont by Delegates.notNull<String>()
    private var cardFontScale by Delegates.notNull<Float>()
    private lateinit var cardFontColors: Map<String, Color>

    private lateinit var iconConfig: Map<String, String>

    private lateinit var damageOverlayTexture: String
    private var damageOverlayDuration by Delegates.notNull<Int>()
    private var damageOverlayFadeIn by Delegates.notNull<Int>()
    private var damageOverlayFadeOut by Delegates.notNull<Int>()

    private lateinit var slotIcons: Array<ResourceHandle>

    private lateinit var encounterModifierConfig: OnjObject

    private lateinit var config: OnjObject

}
