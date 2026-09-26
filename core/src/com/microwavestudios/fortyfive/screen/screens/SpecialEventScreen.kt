package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.card.DetailDescriptionHandler
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.map.events.specialevent.SpecialEvent
import com.microwavestudios.fortyfive.map.events.specialevent.SpecialEventAction
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.BakedDropShadow
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.screen.screenController.BiomeBackgroundScreenController
import com.microwavestudios.fortyfive.screen.screenController.TimelineController
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import kotlin.reflect.KClass

class SpecialEventScreen : ScreenCreator() {

    override val name: String = "specialEventScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = true
    override val background: ResourceHandle? = null

    private val context: SpecialEventScreenContext by lazy { context() }

    private val timelines: TimelineController = TimelineController()

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        name to noTransition(),
        "*" to fadeToBlackTransition(700)
    )


    private var optionChosen: Boolean = false


    override fun getRoot(): Group = newGroup {
        val event = context.specialEvent

        width = worldWidth
        height = worldHeight

        box {
            backgroundHandle = "special_event_text_background"
            dropShadow = BakedDropShadow(
                "special_event_text_background",
                screen,
                0f, 0f,
                1.34f, 1.34f
            )

            relativeWidth(60f)
            heightByAspectRatio(1.4164)

            centerX()
            centerY(offset = -50f)

            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER

            verticalSpacer(40f)

            label("red wing", event.title, Color.FortyWhite, 60) {
                relativeWidth(100f)
                height = 60f
                setAlignment(Align.center)
            }

            verticalSpacer(50f)

            advancedText("roadgeek", Color.Black, 30) {
                relativeWidth(80f)
                syncHeight()
                setRawText(event.description, DetailDescriptionHandler.allTextEffects)
            }

            verticalSpacer(60f)

            label("roadgeek", "What do you want to do:", Color.Black, 30) {
                relativeWidth(80f)
                syncHeight()
            }

            verticalSpacer(10f)

            event.options.forEach {
                eventOption(it.first, it.second)
                verticalSpacer(15f)
            }

        }

        addDefaultOverlays(worldWidth, worldHeight, EventPipeline(), hasBackpack = true)
    }

    private fun CustomBox.eventOption(text: String, actions: List<SpecialEventAction>) = box {
        relativeWidth(80f)
        syncHeight()
        focusBackgrounds("white_texture", "grey_texture")
        paddingTop = 10f
        paddingBottom = 10f
        paddingLeft = 10f

        advancedText("roadgeek", Color.Black, 24) {
            relativeWidth(100f)
            setRawText(text, DetailDescriptionHandler.allTextEffects)
            syncHeight()
            touchable = Touchable.disabled
        }

        touchable = Touchable.enabled
        keyboardFocusable = KeyboardFocusable.LEAF

        onInput(GameInputs.interact) {
            if (optionChosen) return@onInput
            optionChosen = true
            val rewardScreens = ScreenManager.ScreenChain(listOf())
            actions.forEach { timelines.appendMainTimeline(it(screen, rewardScreens)) }
            timelines.appendMainTimeline {
                action {
                    context.onComplete()
                    FortyFive.screenManager.ensureNextScreens(rewardScreens)
                    FortyFive.screenManager.screenFinished()
                }
            }
        }
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        BiomeBackgroundScreenController(screen), timelines
    )


    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = SpecialEventScreen::class
    }
}

interface SpecialEventScreenContext {

    val specialEvent: SpecialEvent

    fun onComplete()
}
