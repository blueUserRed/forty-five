package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.animation.AnimState
import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.game.Talisman
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.card.CardPresentation
import com.microwavestudios.fortyfive.game.card.CardPrototype
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.game.card.RandomCardSelection
import com.microwavestudios.fortyfive.profile.IProfile
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.screen.BakedDropShadow
import com.microwavestudios.fortyfive.screen.SquareDropShadow
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.commonComponents.BackpackCreator
import com.microwavestudios.fortyfive.screen.screenController.BiomeBackgroundScreenController
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.setText
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Colors
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.alpha
import kotlin.math.abs
import kotlin.random.Random
import kotlin.reflect.KClass

class GetTalismanScreen : ScreenCreator() {

    override val name: String = "getTalismanScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = true

    override val background: String? = null

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        name to noTransition(),
        "*" to fadeToBlackTransition(300)
    )

    private val context: GetTalismanScreenContext by lazy { context() }

    private val events: EventPipeline = EventPipeline()

    private val profile: IProfile = FortyFive.profileManager.currentProfile!!


    override fun getRoot(): Group = newGroup {
        if (context.talisman in profile.talismans) {
            FortyFive.logger.warn(this@GetTalismanScreen.name, "player already has talisman: ${context.talisman}!")
            screen.afterMs(0) { FortyFive.screenManager.screenFinished() }
            return@newGroup
        }

        width = worldWidth
        height = worldHeight
        x = 0f
        y = 0f

        box {
            backgroundHandle = "choose_card_cards_background"
            dropShadow = BakedDropShadow(
                "choose_card_cards_background", screen,
                scaleX = 1.3f, scaleY = 1.3f
            )
            width = worldWidth * 0.5f
            height = worldHeight * 0.5f
            centerX()
            centerY()
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.SPACE_AROUND

            verticalSpacer(20f)

            label("red wing", "You get a Talisman:", Colors.FortyWhite, 38) {
                syncDimensions()
            }

            label("red wing", context.talisman.title, Colors.Orange, 70) {
                syncDimensions()
            }

            image {
                squareDim(130f)
                backgroundHandle = context.talisman.iconHandle
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                detailWidget = context.talisman.buildHoverDetail(screen)
                bindDetailToInputState(GameInputs.States.focused)
            }

            box {
                height = 60f
                relativeWidth(80f)
                flexDirection = FlexDirection.ROW
                horizontalAlign = CustomAlign.CENTER
                verticalAlign = CustomAlign.CENTER


                box(backgroundHints = buttonBackgroundHints()) {
                    horizontalAlign = CustomAlign.CENTER
                    verticalAlign = CustomAlign.CENTER
                    height = 60f
                    width = 200f
                    logicalOffsetY = -30f
                    defaultButtonConfig()
                    touchable = Touchable.enabled
                    keyboardFocusable = KeyboardFocusable.LEAF
                    label("roadgeek", "Get Talisman!", Colors.FortyWhite, 24) {
                        touchable = Touchable.disabled
                        syncDimensions()
                    }
                    onInput(GameInputs.interact) {
                        profile.getTalismanForRun(context.talisman)
                        context.completed()
                        FortyFive.screenManager.screenFinished()
                    }
                }
                horizontalSpacer(20f)
                box(backgroundHints = buttonBackgroundHints()) {
                    horizontalAlign = CustomAlign.CENTER
                    verticalAlign = CustomAlign.CENTER
                    height = 60f
                    width = 200f
                    logicalOffsetY = -30f
                    defaultButtonConfig()
                    touchable = Touchable.enabled
                    keyboardFocusable = KeyboardFocusable.LEAF
                    label("roadgeek", "Continue without", Colors.FortyWhite, 24) {
                        touchable = Touchable.disabled
                        syncDimensions()
                    }
                    onInput(GameInputs.interact) {
                        context.completed()
                        FortyFive.screenManager.screenFinished()
                    }
                }
            }
        }

        addDefaultOverlays(worldWidth, worldHeight, events, hasTutorial = false, hasBackpack = true, hasTitleScreen = false)
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        BiomeBackgroundScreenController(screen)
    )

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = GetTalismanScreen::class
    }
}

interface GetTalismanScreenContext {
    val talisman: Talisman

    fun completed()
}
