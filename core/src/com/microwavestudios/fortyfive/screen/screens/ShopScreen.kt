package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.config.Npc
import com.microwavestudios.fortyfive.config.displayName
import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.card.RandomCardSelection
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.map.ShopMapEvent
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.*
import com.microwavestudios.fortyfive.screen.commonComponents.BackpackCreator
import com.microwavestudios.fortyfive.screen.screenController.BiomeBackgroundScreenController
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.*
import kotlin.random.Random
import kotlin.reflect.KClass

class ShopScreen : ScreenCreator() {

    override val name: String = "shopScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    private val context: ShopMapEvent by lazy { context() }

    private val npc: Npc by lazy {
        ConfigFileManager.npcConfig.npcs[context.person] ?: throw RuntimeException("unknown npc: ${context.person}")
    }

    private val random = Random

    private val events: EventPipeline = EventPipeline()

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        name to noTransition(),
        "*" to geometricFadeTransition()
    )

    private val dropTargetFilter: InputManager.FocusFilter by lazy {
        InputManager.FocusFilter(
            listOf(shopDropTargetGroup),
            screen
        )
    }

    private val cardFocusGrid = InputManager.FocusGrid()

    private var cardsLifetime: EndableLifetime = EndableLifetime()

    private val cardStateChangedCallbacks: MutableList<() -> Unit> = mutableListOf()

    private lateinit var currentDeck: Deck

    override fun getScreenControllers(): List<ScreenController> = listOf(
        BiomeBackgroundScreenController(screen, true)
    )

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        dropTargetFilter.start()
        screen.inputManager.addDragAndDrop("buyable", shopDropTargetGroup)

        val profile = FortyFive.profileManager.currentProfile!!
        val inRun = profile.isRunActive
        currentDeck = if (inRun) profile.currentRunDeck!! else profile.currentCollectionDeck

        events.watchFor<BackpackCreator.DeckChangedEvent> {
            currentDeck = if (inRun) profile.currentRunDeck!! else profile.currentCollectionDeck
            events.fire(RecheckAddToDeck)
        }
        events.watchFor<BackpackCreator.CardsChangedEvent> { events.fire(RecheckAddToDeck) }

        image {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            backgroundHandle = "transparent_black_texture"
        }

        dropTarget(worldHeight * 0.5F, "shop_add_to_deck", true)
        dropTarget(worldHeight * 0.06F, "shop_add_to_backpack", false)

        box {
            width = worldWidth.percent(63)
            height = worldHeight.percent(89)
            x = worldWidth.percent(36)
            y = 1f
            backgroundHandle = "shop_background"
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.SPACE_AROUND
            wrap = CustomWrap.NONE
            paddingTop = worldWidth.percent(3)
            paddingLeft = 25f
            paddingRight = 10f

            val childrenSize = 88.5f

            textsAtTheTop(childrenSize)

            cardContainer(childrenSize)

            box(backgroundHints = buttonBackgroundHints()) {
                horizontalAlign = CustomAlign.CENTER
                verticalAlign = CustomAlign.CENTER
                height = 60f
                width = 140f
                defaultButtonConfig()
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                logicalOffsetY = 55f
                val label = label("roadgeek", "", Color.FortyWhite, 24) {
                    setText("reroll: ${context.currentRerollPrice}\$")
                    touchable = Touchable.disabled
                    syncDimensions()
                }
                onInput(GameInputs.interact) {
                    reroll()
                    label.setText("reroll: ${context.currentRerollPrice}\$")
                }
            }

            label(
                "red wing",
                "drag to the merchant to confirm your purchase and add it to your backpack",
                Color.FortyWhite,
                (32 * 0.7).toInt()
            ) {
                logicalOffsetY = 55f
                syncDimensions()
            }
        }

        personImage()

        addDefaultOverlays(worldWidth, worldHeight, events, hasBackpack = inRun, hasCollection = !inRun)

        val cards = context.currentCards ?: run {
            val cards = generateRandomCards()
            context.currentCards = cards
            cards
        }
        updateCards(cards)
    }

    private fun reroll() {
        val profile = FortyFive.profileManager.currentProfile!!
        val price = context.currentRerollPrice
        if (profile.playerMoney < price) {
            FortyFive.soundPlayer.situation("not_allowed", screen)
            return
        }
        profile.payMoney(price)
        context.amountOfRerolls++
        val newCards = generateRandomCards()
        context.boughtIndices.clear()
        context.currentCards = newCards
        updateCards(newCards)
    }

    private fun generateRandomCards(): List<String> {
        val amount = context.amountCards.random(random)
        val profile = FortyFive.profileManager.currentProfile!!
        val cards = RandomCardSelection.getRandomCards(
            listOf(),
            amount,
            profile.currentMapSaver.currentMap.biome,
            profile.currentMapSaver.currentMap.majorDifficulty,
            random,
            unique = true
        ).map { it.name }
        return cards
    }

    private fun updateCards(cards: List<String>) {
        val protos = RandomCardSelection.allCardPrototypes
        val newLifetime = EndableLifetime()
        val guardedLifetime = newLifetime.shorter(screen.lifetime)
        val createdCards = cards.map { name ->
            val proto = protos.find { it.name == name } ?: throw RuntimeException("unknown card: $name")
            val card = proto.create(screen)
            guardedLifetime.tieDisposable(card)
            card
        }
        // Not guarded on purpose, because the lifetime is used for actor cleanup, which isn't required if the screen
        // is disposed anyway
        events.fire(CardsChangedEvent(createdCards, newLifetime))
        cardsLifetime.die()
        cardsLifetime = newLifetime
        updateCardStates()
    }

    private fun updateCardStates() {
        cardStateChangedCallbacks.forEach { it() }
    }

    private fun Group.personImage() = image {
        touchable = Touchable.disabled

        val npc = npc
        backgroundHandle = npc.texture
        width = npc.drawWidth
        height = npc.drawHeight
        drawOffsetX = npc.offsetX
        drawOffsetY = npc.offsetY
    }

    private fun Group.cardContainer(childrenSize: Float) = box(isScrollable = true) {
        this as CustomScrollableBox
        relativeWidth(childrenSize)
        relativeHeight(59f)
        backgroundHandle = "shop_items_background"
        minVerticalDistBetweenElements = 15F
        minHorizontalDistBetweenElements = 15F
        scrollDistancePerScroll = 50F
        paddingLeft = 30f
        paddingTop = 15f
        paddingBottom = 30f

        events.watchFor<CardsChangedEvent> { (cards, lifetime) ->
            clearChildren()
            cardFocusGrid.clear()
            cards.forEachIndexed { index, card -> card(card, index, lifetime) }
            var x = 0
            var y = 0
            walk().forEach { child ->
                if (child !is CardActor) return@forEach
                cardFocusGrid.set(x, y, child)
                x++
                if (x >= 4) {
                    x = 0
                    y++
                }
            }
        }

        addScrollbarFromDefaults(
            CustomDirection.RIGHT,
            "backpack_scrollbar",
            "backpack_scrollbar_background"
        )
    }

    private fun CustomBox.card(card: Card, index: Int, lifetime: Lifetime) = box {
        width = 130f
        height = 190f
        flexDirection = FlexDirection.COLUMN
        val actor = actor (card.actor) {
            width = 130f
            height = 130f
            touchable = Touchable.enabled
            keyboardFocusable = KeyboardFocusable.LEAF
        }
        val label = label("red wing", "", fontSize = 32) {
            width = 130f
            height = 60f
            setAlignment(Align.center)
        }
        val stateChangeCallback: () -> Unit = {
            val profile = FortyFive.profileManager.currentProfile!!
            val buyable = profile.playerMoney >= card.price
            val bought = index in context.boughtIndices
            actor.leaveGroup("buyable")
            actor.infoObject = null
            when {
                bought -> {
                    actor.isDraggable = false
                    label.setText("sold out")
                    label.alpha = 0.5f
                    actor.isGrayScale = true
                }
                buyable -> {
                    actor.isDraggable = true
                    label.setText("\$${card.price}")
                    label.alpha = 1f
                    actor.isGrayScale = false
                    actor.joinGroup("buyable")
                    actor.infoObject = CardDragAndDropInfo(card, card.price, index)
                }
                else -> {
                    actor.isDraggable = false
                    label.setText("\$${card.price}")
                    label.alpha = 0.5f
                    actor.isGrayScale = false
                }
            }
        }
        cardStateChangedCallbacks.add(stateChangeCallback)
        lifetime.onEnd { cardStateChangedCallbacks.remove(stateChangeCallback) }
    }

    private fun Group.textsAtTheTop(childrenSize: Float) = box {
        relativeWidth(childrenSize)
        syncHeight()
        box {
            height = 90F
            flexDirection = FlexDirection.ROW
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            verticalAlign = CustomAlign.CENTER
            relativeWidth(100F)
            box { //name and icon
                flexDirection = FlexDirection.ROW
                relativeHeight(100F)

                image {
                    backgroundHandle = "map_node_shop"
                    relativeHeight(100F)
                    onLayoutAndNow { width = height }
                }
                label(
                    "red wing",
                    npc.displayName,
                    Color.FortyWhite,
                    64,
                    isTemplate = true
                ) {
                    setAlignment(Align.left)
                    syncDimensions()
                }
            }

            box(backgroundHints = arrayOf("shop_back_button_hover", "shop_back_button")) {// leave
                name("shop_back_button_name")
                backgroundHandle = "shop_back_button"
                width = 200F
                relativeHeight(70F)
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                observeInputState(
                    GameInputs.States.focused,
                    { backgroundHandle = "shop_back_button_hover" },
                    { backgroundHandle = "shop_back_button" }
                )
                onInput(GameInputs.interact) {
                    FortyFive.screenManager.screenFinished()
                }
            }

        }

        image { //line between
            backgroundHandle = "forty_white_rounded"
            relativeWidth(100f)
            height = 2f
            marginTop = 8f
            marginBottom = 12f
        }

        advancedText(
            "roadgeek",
            defaultColor = Color.FortyWhite,
            defaultFontSize = 19,
        ) {//subtext
            relativeWidth(100f)
            syncHeight()
            fitContentHeight = true
        }
    }


    private fun Group.dropTarget(yStart: Float, textureName: String, addToDeck: Boolean) = image {
        relativeHeight(40F)
        relativeWidth(30F)
        y = yStart
        x = -width
        backgroundHandle = textureName
        fixedZIndex = 200
        val distanceNotSelected = -20F
        touchable = Touchable.disabled
        keyboardFocusable = KeyboardFocusable.LEAF

        isDropTarget = true

        if (addToDeck) {
            events.watchFor<RecheckAddToDeck> {
                leaveGroup(shopDropTargetGroup)
                if (currentDeck.canAddCards()) joinGroup(shopDropTargetGroup)
            }
        } else {
            joinGroup(shopDropTargetGroup)
        }

        observeInputState(
            GameInputs.States.awaitingDrop,
            {
                dropTargetFilter.end()
                touchable = Touchable.enabled
            },
            {
                dropTargetFilter.start()
                touchable = Touchable.disabled
            },
        )

        onDrop { actor ->
            val info = actor.infoObject as? CardDragAndDropInfo ?: return@onDrop
            val profile = FortyFive.profileManager.currentProfile!!
            if (profile.playerMoney < info.price) return@onDrop
            FortyFive.soundPlayer.situation("card_bought", screen)
            profile.payMoney(info.price)
            val deck = currentDeck
            if (profile.isRunActive) profile.addCardToBackpack(info.card.name)
            else profile.addCardToCollection(info.card.name)
            if (addToDeck && deck.canAddCards()) {
                deck.addToDeck(deck.nextFreeSlot(), info.card.name)
            }
            context.boughtIndices.add(info.index)
            updateCardStates()
            events.fire(BackpackCreator.CardsModifiedEvent)
        }

        fun updateState() {
            when {
                isInInputState(GameInputs.States.awaitingDropFocused) -> addAction(getAction(0f, y))
                isInInputState(GameInputs.States.awaitingDrop) -> addAction(getAction(distanceNotSelected, y))
                else -> addAction(getAction(-width, y))
            }
        }

        observeInputState(GameInputs.States.awaitingDrop, ::updateState, ::updateState)
        observeInputState(GameInputs.States.awaitingDropFocused, ::updateState, ::updateState)
    }

    private fun getAction(to: Float, y: Float) = MoveToAction().also {
        it.x = to
        it.y = y
        it.duration = 0.2f
        it.interpolation = Interpolation.pow2Out
    }

    companion object : ScreenManager.ScreenCreatorCompanion {
        const val shopDropTargetGroup = "shop-drop-target"

        override val creatorClass: KClass<out ScreenCreator> = ShopScreen::class
    }


    private data class CardsChangedEvent(val cards: List<Card>, val lifetime: Lifetime)
    private data object RecheckAddToDeck

    private data class CardDragAndDropInfo(val card: Card, val price: Int, val index: Int)

}
