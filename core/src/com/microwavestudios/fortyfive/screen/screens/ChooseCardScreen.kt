package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.animation.AnimState
import com.microwavestudios.fortyfive.game.SaveState
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.card.CardPrototype
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.map.MapManager
import com.microwavestudios.fortyfive.game.card.RandomCardSelection
import com.microwavestudios.fortyfive.screen.SquareDropShadow
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.commonComponents.BackpackCreator
import com.microwavestudios.fortyfive.screen.screenController.BiomeBackgroundScreenController
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.alpha
import kotlin.math.abs
import kotlin.random.Random
import kotlin.reflect.KClass

class ChooseCardScreen : ScreenCreator() {

    override val name: String = "chooseCardScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = true

    override val background: String? = null

    override val transitionAwayTimes: Map<String, Int> = mapOf()

    private val context: ChooseCardScreenContext by lazy { context() }

    private val events: EventPipeline = EventPipeline()

    private val dropTargetGroup = "shop-screen-drop-target"

    private val dropTargetFilter: InputManager.FocusFilter by lazy {
        InputManager.FocusFilter(listOf(dropTargetGroup), screen)
    }

    override fun getRoot(): Group = newGroup {
        width = worldWidth
        height = worldHeight
        x = 0f
        y = 0f

        screen.inputManager.addDragAndDrop(CardActor.cardGroup, dropTargetGroup)
        dropTargetFilter.start()

        box {
            backgroundHandle = "choose_card_cards_background"
            width = worldWidth * 0.5f
            height = worldHeight * 0.5f
            centerX()
            onLayoutAndNow { y = parent.height / 2 - height / 2 + 80f }
            flexDirection = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.SPACE_AROUND

            label("red_wing", "", color = Color.FortyWhite) {
                syncDimensions()
                events.watchFor<CardsChangedEvent> { (cards) ->
                    setText(if (cards.size == 1) "You get a card!" else "Choose a card!")
                }
                setFontScale(1.3f)
            }
            box {
                name("chooseCardCardParent")
                height = 200f
                relativeWidth(80f)
                flexDirection = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.SPACE_AROUND
                debug()
                events.watchFor<CardsChangedEvent> { (cards) ->
                    clearChildren()
                    val data = getDataForCards(cards.size)
                    var i = 0
                    allActors(cards.map { it.actor }) {
                        width = 160f
                        height = 160f
                        val (rotation, yPos) = data[i]
                        this.rotation = rotation.toFloat()
                        isDraggable = true
                        logicalOffsetY = yPos * 10f
                        touchable = Touchable.enabled
                        keyboardFocusable = KeyboardFocusable.LEAF
                        val dropShadow = SquareDropShadow(Color.GOLD, scale = 1.2f, blurFactor = 0.8f, showDropShadow = false)
                        this.dropShadow = dropShadow
                        observeInputState(
                            GameInputs.States.inDrag,
                            { dropTargetFilter.end() },
                            { dropTargetFilter.start() },
                        )
                        observeInputState(
                            InputManager.BaseStates.keyboardDrag,
                            { dropShadow.showDropShadow = true },
                            { dropShadow.showDropShadow = false },
                        )
                        i++
                    }
                }
            }
            label("red_wing", "Drag to add to your deck or backpack", color = Color.FortyWhite) {
                setFontScale(0.7f)
                syncDimensions()
            }
            if (context.enableRerolls) box(backgroundHints = buttonBackgroundHints()) {
                horizontalAlign = CustomAlign.CENTER
                verticalAlign = CustomAlign.CENTER
                height = 60f
                width = 140f
                logicalOffsetY = -30f
                defaultButtonBackgrounds()
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                val label = label("roadgeek", "", color = Color.FortyWhite) {
                    setText("reroll: ${context.currentRerollPrice}\$")
                    syncDimensions()
                }
                onInput(GameInputs.interact) {
                    reroll()
                    label.setText("reroll: ${context.currentRerollPrice}\$")
                }
            }
        }

        dropTargets()
        addDefaultOverlays(worldWidth, worldHeight, events, hasTutorial = false)
        initCards()
    }

    private fun reroll() {
        val price = context.currentRerollPrice
        if (SaveState.playerMoney < price) {
            FortyFive.soundPlayer.situation("not_allowed", screen)
            return
        }
        SaveState.payMoney(price)
        context.amountOfRerolls++
        context.seed = Random(context.seed).nextLong()
        initCards()
    }

    private fun CustomGroup.dropTargets() = box {
        val scale = 0.8f

        relativeWidth(60f)
        height = 350f * scale
        centerX()
        y = 0f
        flexDirection = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.FlexDirection.ROW
        horizontalAlign = CustomAlign.SPACE_AROUND

        box {
            backgroundHandle = "choose_card_add_to_deck"
            width = 462f * scale
            height = 350f * scale
            touchable = Touchable.enabled
            keyboardFocusable = KeyboardFocusable.LEAF
            isDropTarget = true
            joinGroup(dropTargetGroup)
            joinGroup("choose_card_add_to_deck")
            val filter = InputManager.FocusFilter(listOf("choose_card_add_to_deck"), screen)

            val animation = propertyAnimation(
                this::logicalOffsetY,
                AnimState("closed", -30f),
                AnimState("open", 0f),
                defaultTime = 100,
                defaultInterpolation = Interpolation.pow2,
                initialState = "closed",
                invalidateParent = true,
            )

            var isDisabled = !SaveState.curDeck.canAddCards()
            if (isDisabled) {
                animation.state("closed")
                alpha = 0.5f
                filter.start()
            } else {
                alpha = 1f
                filter.end()
            }
            events.watchFor<BackpackCreator.DeckChangedEvent> {
                isDisabled = !SaveState.curDeck.canAddCards()
                if (isDisabled) {
                    animation.state("closed")
                    alpha = 0.5f
                    filter.start()
                } else {
                    alpha = 1f
                    filter.end()
                }
            }

            onDrop { actor ->
                if (isDisabled) return@onDrop
                if (actor !is CardActor) return@onDrop
                actor.isVisible = false
                getCard(actor.card.name, true)
            }

            observeInputState(
                GameInputs.States.awaitingDropFocused,
                { if (!isDisabled) animation.state("open") },
                { animation.state("closed") },
            )
        }

        box {
            backgroundHandle = "choose_card_add_to_backpack"
            width = 462f * scale
            height = 350f * scale
            touchable = Touchable.enabled
            keyboardFocusable = KeyboardFocusable.LEAF
            isDropTarget = true
            joinGroup(dropTargetGroup)
            joinGroup("choose_card_add_to_backpack")
            val filter = InputManager.FocusFilter(listOf("choose_card_add_to_backpack"), screen)

            val animation = propertyAnimation(
                this::logicalOffsetY,
                AnimState("closed", -30f),
                AnimState("open", 0f),
                defaultTime = 100,
                defaultInterpolation = Interpolation.pow2,
                initialState = "closed",
                invalidateParent = true,
            )

            var isDisabled = !SaveState.curDeck.hasEnoughCards()
            if (isDisabled) {
                animation.state("closed")
                alpha = 0.5f
                filter.start()
            } else {
                alpha = 1f
                filter.end()
            }
            events.watchFor<BackpackCreator.DeckChangedEvent> {
                isDisabled = !SaveState.curDeck.hasEnoughCards()
                if (isDisabled) {
                    animation.state("closed")
                    alpha = 0.5f
                    filter.start()
                } else {
                    alpha = 1f
                    filter.end()
                }
            }

            onDrop { actor ->
                if (isDisabled) return@onDrop
                if (actor !is CardActor) return@onDrop
                actor.isVisible = false
                getCard(actor.card.name, false)
            }

            observeInputState(
                GameInputs.States.awaitingDropFocused,
                { if (!isDisabled) animation.state("open") },
                { animation.state("closed") },
            )
        }
    }

    private fun getCard(card: String, addToDeck: Boolean) {
        FortyFive.logger.debug(name, "Chose card: $card")
        SaveState.buyCard(card)
        if (addToDeck) SaveState.curDeck.addToDeck(SaveState.curDeck.nextFreeSlot(), card)
        context.completed()
        SaveState.write()
        FortyFive.screenManager.screenFinished()
    }

    private fun initCards() {
        val cards = getCardProtos().map {
            val card = it.create(screen)
            screen.lifetime.tieDisposable(card)
            card
        }
        val event = CardsChangedEvent(cards)
        events.fire(event)
    }

    private fun getCardProtos(): List<CardPrototype> {
        val forcedCards = context.forceCards
        return if (forcedCards != null) {
            val allProtos = RandomCardSelection.allCardPrototypes
            forcedCards.map { name ->
                allProtos.find { it.name == name } ?: throw RuntimeException("unknown card: $name")
            }
        } else {
            val biome = FortyFive.profileManager.currentProfile?.currentMapSaver?.currentMap?.biome
                ?: return listOf()
            RandomCardSelection.getRandomCards(
                screen,
                context.types,
                context.nbrOfCards,
                Random(context.seed),
                biome,
                "chooseCard",
                unique = true
            )
        }
    }

    private fun getDataForCards(size: Int): List<Pair<Double, Float>> {
        val pos = getXPositionsBasedOnSize(size)
        val res = mutableListOf<Pair<Double, Float>>()
        for (i in pos) res.add(-7 * i to -abs(2 * i.toFloat()))
        return res
    }

    private fun getXPositionsBasedOnSize(size: Int): DoubleArray {
        if (size <= 0) return DoubleArray(0)
        val points = DoubleArray(size)
        val mid = size / 2
        val gap = 2.0 / size

        for (i in 0 until mid) {
            points[i] = -1 + i * gap
            points[size - 1 - i] = 1 - i * gap
        }
        if (size % 2 == 1) {
            points[mid] = 0.0
        } else {
            points[mid] = 0.0
            points[mid - 1] = points[mid]
        }
        return points
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        BiomeBackgroundScreenController(screen, true)
    )

    private data class CardsChangedEvent(val newCards: List<Card>)

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = ChooseCardScreen::class
    }
}

interface ChooseCardScreenContext {

    var seed: Long
    val nbrOfCards: Int
    val types: List<String>

    val enableRerolls: Boolean
    var amountOfRerolls: Int
    val rerollPriceIncrease: Int
    val rerollBasePrice: Int

    val currentRerollPrice: Int
        get() = rerollBasePrice + rerollPriceIncrease * amountOfRerolls

    val forceCards: List<String>?
        get() = null

    fun completed()
}
