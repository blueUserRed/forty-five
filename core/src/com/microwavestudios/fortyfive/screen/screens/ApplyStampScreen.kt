package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardPresentation
import com.microwavestudios.fortyfive.game.card.DetailDescriptionHandler
import com.microwavestudios.fortyfive.game.card.RandomCardSelection
import com.microwavestudios.fortyfive.game.card.Stamp
import com.microwavestudios.fortyfive.game.card.StampFactory
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.BakedDropShadow
import com.microwavestudios.fortyfive.screen.DropShadow
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.CustomDirection
import com.microwavestudios.fortyfive.screen.actors.CustomScrollableBox
import com.microwavestudios.fortyfive.screen.actors.CustomWrap
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.commonComponents.BackpackCreator.backpackCollectionBackgroundGroup
import com.microwavestudios.fortyfive.screen.commonComponents.DetailWidget
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.screen.screenController.BiomeBackgroundScreenController
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.FortyFiveLogger
import com.microwavestudios.fortyfive.utils.alpha
import kotlin.random.Random
import kotlin.reflect.KClass

class ApplyStampScreen : ScreenCreator() {

    override val name: String = "applyStampScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = true
    override val background: ResourceHandle? = null

    private val context: ApplyStampScreenContext by lazy { context() }

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        name to noTransition(),
        "*" to fadeToBlackTransition(700)
    )

    override fun getRoot(): Group = newGroup {
        val stamp = if (context.stampName == null) {
            val profile = FortyFive.profileManager.currentProfile!!
            val difficulty = profile.currentMapSaver.currentMap.majorDifficulty
            val s = StampFactory.getRandomStamp(difficulty, Random)
            context.stampName = s.name
            s
        } else {
            StampFactory.createStamp(context.stampName!!)
        }

        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight
        box {
            flexDirection = FlexDirection.ROW
            horizontalAlign = CustomAlign.SPACE_AROUND
            verticalAlign = CustomAlign.CENTER
            width = worldWidth
            height = worldHeight

            stampSide(stamp)
            collectionSide(stamp)
        }
        addDefaultOverlays(worldWidth, worldHeight, EventPipeline())
    }

    private fun CustomBox.stampSide(stamp: Stamp) = box {
        height = worldHeight
        width = worldWidth * 0.4f

        verticalAlign = CustomAlign.CENTER

        box {
            backgroundHandle = "choose_card_cards_background"
            dropShadow = BakedDropShadow(
                "choose_card_cards_background",
                screen,
                scaleX = 1.3f,
                scaleY = 1.3f,
            )
            relativeWidth(100f)
            heightByAspectRatio(640.0 / 431.0)

            flexDirection = FlexDirection.COLUMN
            verticalAlign = CustomAlign.SPACE_AROUND
            horizontalAlign = CustomAlign.CENTER


            box {
                height = 60f
                relativeWidth(100f)
                flexDirection = FlexDirection.ROW

                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                horizontalAlign = CustomAlign.CENTER

                detailWidget = DetailWidget.ComplexBigDetailActor(
                    screen,
                    effects = DetailDescriptionHandler.allTextEffects,
                    text = { listOf(stamp.description) },
                    subtexts = {
                        DetailDescriptionHandler
                            .extractAllExtraDescriptions(listOf(stamp.description))
                    }
                )

                bindDetailToInputState(GameInputs.States.focused)

                image {
                    squareDim(65f)
                    backgroundHandle = stamp.icon
                }
                horizontalSpacer(40f)
                label("red wing", stamp.title, Color.Magenta, 60) {
                    touchable = Touchable.disabled
                    syncDimensions()
                }
            }

            label("red wing", "Choose card to apply stamp to", Color.FortyWhite, 35) {
                syncDimensions()
            }

            box(backgroundHints = buttonBackgroundHints()) {
                defaultButtonConfig()
                width = 200f
                height = 50f
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                onInput(GameInputs.interact) {
                    FortyFive.screenManager.screenFinished()
                }
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.CENTER
                label("red wing", "Quit without stamp", fontSize = 20) {
                    touchable = Touchable.disabled
                    syncDimensions()
                }
            }
        }

    }

    private fun CustomBox.collectionSide(stamp: Stamp) = box {
        height = worldHeight
        width = worldWidth * 0.5f
        verticalAlign = CustomAlign.END
        horizontalAlign = CustomAlign.CENTER

        val profile = FortyFive.profileManager.currentProfile
        requireNotNull(profile) { "ApplyStampScreen requires profile" }
        val collection = profile.backpack ?: profile.cardCollection
        val cardProtos = RandomCardSelection.allCardPrototypes
        val cards = collection.map { type ->
            val proto = cardProtos.find { it.name == type.name }
            requireNotNull(proto) { "unknown card $type" }
            proto.create(screen, type, CardPresentation.defaultProvider)
        }
        cards.forEach { screen.lifetime.tieDisposable(it) }

        box {
            backgroundHandle = "backpack_backpack_background"
            width = 730f
            height = 770f
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER

            verticalSpacer(25f)
            cardScrollBox(cards, stamp)
        }
    }

    private fun CustomBox.cardScrollBox(cards: List<Card>, stamp: Stamp) = box(isScrollable = true) {
        this as CustomScrollableBox
        relativeWidth(95f)
        height = 680f
        x = 80f
        scrollDirectionStart = CustomDirection.TOP
        horizontalAlign = CustomAlign.START
        wrap = CustomWrap.WRAP
        addScrollbarFromDefaults(
            CustomDirection.RIGHT,
            "backpack_scrollbar",
            "backpack_scrollbar_background",
        )
        flexDirection = FlexDirection.ROW
        paddingTop = 20f
        var x = 0
        var y = 0
        val grid = InputManager.FocusGrid()
        cards.forEach { card ->
            val canBePicked = cardCanBePicked(card, stamp)
            val actor = card.presentation.forceGetActor()
            box {
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.CENTER
                squareDim(160f)
                actor(actor) {
                    width = 140f
                    height = 140f
                    touchable = Touchable.enabled
                    keyboardFocusable = KeyboardFocusable.LEAF
                    if (!canBePicked) alpha = 0.5f
                }
            }
            actor.onInput(GameInputs.interact) {
                if (!canBePicked) {
                    FortyFive.soundPlayer.situation("not_allowed", screen)
                    return@onInput
                }
                cardSelected(card, stamp)
            }
            grid.set(x, y, actor)
            x++
            if (x > 3) {
                x = 0
                y++
            }
        }
    }

    private fun cardCanBePicked(card: Card, stamp: Stamp): Boolean =
        card.stamp == null && stamp.canBeAppliedTo(card)

    private fun cardSelected(card: Card, stamp: Stamp) {
        val profile = FortyFive.profileManager.currentProfile
        requireNotNull(profile) { "ApplyStampScreen requires profile" }
        val old = card.type
        val new = old.copy(stamp = stamp.name)
        if (profile.isRunActive) {
            profile.swapCardInBackpack(old, new)
        } else {
            profile.swapCardInCollection(old, new)
        }
        FortyFive.screenManager.screenFinished()
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        BiomeBackgroundScreenController(screen, true)
    )

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = ApplyStampScreen::class
    }
}

interface ApplyStampScreenContext {
    var stampName: String?
}
