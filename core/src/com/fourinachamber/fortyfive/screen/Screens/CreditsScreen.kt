package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.TransitionType
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.AdvancedTextParser
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.collections.map

class CreditsScreen : ScreenCreator() {

    override val name: String = "creditsScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    val backButtonFocusGroup = "back_group"

    override val background: String = "black_texture"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 800
    )

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf(
        FocusableParent(
            listOf(
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf(backButtonFocusGroup)
                ),
            ),
            startGroups = listOf(backButtonFocusGroup),
        )
    )

    override fun getInputMaps(): List<KeyInputMap> = listOf(
        KeyInputMap.createFromKotlin(listOf(), screen)
    )

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
        box {
            debug = true
            relativeWidth(80f)
            y = worldHeight * 0.8f
            horizontalAlign = CustomAlign.CENTER
            onLayoutAndNow { x = worldWidth * 0.1f }
            minVerticalDistBetweenElements = 200f
            addTexts(file.get<OnjArray>("texts"), defaults, effects)
        }
    }

    private fun CustomBox.addTexts(
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
                        flexDirection = FlexDirection.ROW
                        relativeWidth(it.get<Double>("relativWidth").toFloat())
                        horizontalAlign = CustomAlign.SPACE_BETWEEN
                        verticalAlign = CustomAlign.CENTER
                        box {
                            syncHeight()
                            width = parent.width / 2f
                            fitContentInFlexDirection = true
                            addTexts(it.get<OnjArray>("left"), defaults, effects)
                        }
                        box {
                            syncHeight()
                            width = parent.width / 2f
                            fitContentInFlexDirection = true
                            addTexts(it.get<OnjArray>("right"), defaults, effects)
                        }
                        syncHeight()
                    }
                }

                "Text" -> {
                    advancedText(defaults) {
                        width = parent.width
                        this.fitContentHeight = true
                        setRawText(it.get<String>("rawText"), effects)
                    }
                }
            }
        }
    }


}