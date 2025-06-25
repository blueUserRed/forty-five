package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.AdvancedTextParser
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.collections.map
import kotlin.math.min
import kotlin.reflect.KClass

class CreditsScreen : ScreenCreator() {

    override val name: String = "creditsScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    val backButtonFocusGroup = "back_group"

    override val background: String = "black_texture"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 0
    )

    private val scrollSpeed = 5f

    override fun getScreenControllers(): List<ScreenController> = listOf(
//        TitleScreenController(screen)
    )

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight


        val file = ConfigFileManager.getConfigFile("creditsText")
        val onjDef = file.get<OnjObject>("defaults")

        val effects = file.get<OnjArray>("effects").value.map {
            AdvancedTextParser.AdvancedTextEffect.getFromOnj(
                it as OnjNamedObject
            )
        }
        val defaults = Triple(
            onjDef.get<String>("font"),
            onjDef.get<Color>("color"),
            onjDef.get<Double>("fontScale").toFloat(),
        )
        val elem = object : _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomBox(screen) {
            var timeSinceFirstStart = 0L
            override fun act(delta: Float) {
                if (timeSinceFirstStart == 0L) {
                    timeSinceFirstStart = System.currentTimeMillis()
                    drawOffsetY = height - worldHeight * 0.9f
                }
                if (timeSinceFirstStart + 500L < System.currentTimeMillis()) {
                    this.drawOffsetY = min(drawOffsetY + scrollSpeed, height - worldHeight * 1.15f)
                }
                super.act(delta)
            }
        }
        actor(elem) {
            relativeWidth(80f)
            fitContentInFlexDirection = true
            horizontalAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.CENTER
            onLayoutAndNow {
                x = worldWidth * 0.1f
                y = worldHeight * 1.6f - height
            }
            minVerticalDistBetweenElements = 300f
            addTexts(file.get<OnjArray>("texts"), defaults, effects)
        }



        label("red_wing", "Back") {
            x = 20f
            y = 20f
            setAlignment(Align.center)
            width = 100f
            onLayoutAndNow {
                height = prefHeight*1.2f
            }
        }
    }

    private fun _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomBox.addTexts(
        parts: OnjArray,
        defaults: Triple<String, Color, Float>,
        effects: List<AdvancedTextParser.AdvancedTextEffect>
    ) {
        parts.value.map { it as OnjNamedObject }.forEach {
            when (it.name) {
                "Image" -> {
                    image {
                        backgroundHandle = it.get<String>("path")
                        relativeWidth(it.get<Double>("relativWidth").toFloat())
                        onLayout {
                            loadedDrawable?.let {
                                height = width * it.minHeight / it.minWidth
                            }
                        }
                        drawOffsetX = it.getOr<Double>("offsetX", 0.0).toFloat()
                    }
                }

                "Split" -> {
                    box {
                        flexDirection = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.FlexDirection.ROW
                        relativeWidth(it.get<Double>("relativWidth").toFloat())
                        horizontalAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.SPACE_BETWEEN
                        verticalAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.CENTER
                        marginBottom = 100f
                        box {
                            syncHeight()
                            width = parent.width / 2f
                            fitContentInFlexDirection = true
                            horizontalAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.CENTER
                            minVerticalDistBetweenElements = 4f
                            addTexts(it.get<OnjArray>("left"), defaults, effects)
                        }
                        box {
                            syncHeight()
                            width = parent.width / 2f
                            fitContentInFlexDirection = true
                            horizontalAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.CENTER
                            minVerticalDistBetweenElements=4f
                            addTexts(it.get<OnjArray>("right"), defaults, effects)
                        }
                        syncHeight()
                    }
                }

                "Text" -> {
                    advancedText(defaults) {
                        width = parent.width
                        this.fitContentHeight = true
                        this.horizontalTextAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.CENTER
                        setRawText(it.get<String>("rawText"), effects)
                    }
                }
            }
        }
    }

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = CreditsScreen::class
    }

}