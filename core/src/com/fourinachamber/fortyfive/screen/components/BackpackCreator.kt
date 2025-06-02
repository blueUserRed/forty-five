package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.SaveState.Deck
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.keyInput.GameInputs
import com.fourinachamber.fortyfive.keyInput.InputActor
import com.fourinachamber.fortyfive.keyInput.InputManager
import com.fourinachamber.fortyfive.keyInput.KeyboardFocusable
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.CustomDirection
import com.fourinachamber.fortyfive.screen.general.customActor.CustomScrollableBox
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWrap
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.general.customActor.PropertyAction
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.EventPipeline
import com.fourinachamber.fortyfive.utils.Timeline
import onj.value.OnjArray

object BackpackCreator {

    const val backpackElementsGroup: String = "backpack-element"
    const val backpackCardInDeckGroup: String = "backpack-card-in-deck"
    const val backpackSlotInDeckGroup: String = "backpack-slot-in-deck"
    const val backpackCardInCollectionGroup: String = "backpack-card-in-collection"
    const val backpackCollectionBackgroundGroup: String = "backpack-collection-background"

    fun ScreenCreator.getSharedBackpack(
        worldWidth: Float,
        worldHeight: Float,
        warningEvents: EventPipeline,
    ): Pair<CustomGroup, NavbarCreator.NavBarObject> {

        val cardsOnj = ConfigFileManager.getConfigFile("cards")
        val cardPrototypes = Card
            .getFrom(cardsOnj.get<OnjArray>("cards"), initializer = { screen.addDisposable(it) })
            .associate { it.name to it }

        val state = BackpackState(
            SaveState.curDeck,
            cardPrototypes,
            mutableListOf(),
            listOf(),
            EventPipeline(),
            warningEvents,
            SortingMode.DAMAGE,
            false,
            InputManager.FocusGrid(),
            InputManager.FocusGrid(),
            null, null
        )
        updateCardsInCollection(state)

        val backpack = newGroup {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
//            touchable = Touchable.disabled

            deckSide(state, this@getSharedBackpack)

            backpackSide(state, worldWidth, this@getSharedBackpack)
        }

        state.currentDeck.checkDeck()
        state.events.fire(DeckChangedEvent)
        with(screen.inputManager) {
            addDragAndDrop(backpackCardInDeckGroup, backpackCardInDeckGroup)
            addDragAndDrop(backpackCardInDeckGroup, backpackSlotInDeckGroup)
            addDragAndDrop(backpackCardInDeckGroup, backpackCardInCollectionGroup)
            addDragAndDrop(backpackCardInCollectionGroup, backpackCardInDeckGroup)
            addDragAndDrop(backpackCardInCollectionGroup, backpackSlotInDeckGroup)
            addDragAndDrop(backpackCardInDeckGroup, backpackCollectionBackgroundGroup)
        }

        val deckSide = state.deckParent!!
        val collectionSide = state.collectionParent!!

        deckSide.drawOffsetX = -800f
        collectionSide.drawOffsetX = 800f
        deckSide.isVisible = false
        collectionSide.isVisible = false
        backpack.touchable = Touchable.disabled

        val modal = InputManager.Modal(listOf(backpackElementsGroup, NavbarCreator.navbarButtonGroup), screen)
        val filter = InputManager.FocusFilter(listOf(backpackElementsGroup), screen)
        filter.start()

        val navbarObject = NavbarCreator.NavBarObject(
            "Backpack",
            { Timeline.timeline {

                action {
                    deckSide.drawOffsetX = -800f
                    collectionSide.drawOffsetX = 800f
                    deckSide.isVisible = true
                    collectionSide.isVisible = true
                    backpack.touchable = Touchable.childrenOnly
                }

                val deckAction = PropertyAction(deckSide, deckSide::drawOffsetX, 0f)
                val collectionAction = PropertyAction(deckSide, collectionSide::drawOffsetX, 0f)
                deckAction.duration = 0.2f
                collectionAction.duration = 0.2f
                deckAction.interpolation = Interpolation.exp10Out
                collectionAction.interpolation = Interpolation.exp10Out

                action {
                    deckSide.addAction(deckAction)
                    collectionSide.addAction(collectionAction)
                }
                delayUntil { deckAction.isComplete && collectionAction.isComplete }
                action {
                    filter.end()
                    modal.push()
                }
            } },
            { Timeline.timeline {

                action {
                    deckSide.drawOffsetX = 0f
                    collectionSide.drawOffsetX = 0f
                    backpack.touchable = Touchable.disabled
                    modal.finished()
                    filter.start()
                }

                val deckAction = PropertyAction(deckSide, deckSide::drawOffsetX, -800f)
                val collectionAction = PropertyAction(deckSide, collectionSide::drawOffsetX, 800f)
                deckAction.duration = 0.2f
                collectionAction.duration = 0.2f
                deckAction.interpolation = Interpolation.exp10Out
                collectionAction.interpolation = Interpolation.exp10Out

                action {
                    deckSide.addAction(deckAction)
                    collectionSide.addAction(collectionAction)
                }
                delayUntil { deckAction.isComplete && collectionAction.isComplete }

                action {
                    deckSide.isVisible = false
                    collectionSide.isVisible = false
                }
            } },
        )

        return backpack to navbarObject
    }

    private fun switchToDeck(num: Int, state: BackpackState) {
        SaveState.curDeckNbr = num
        val deck = SaveState.curDeck
        deck.checkDeck()
        state.currentDeck = deck
        updateCardsInCollection(state)
        with(state.events) {
            fire(GiveCardBackEvent(-1, false))
            fire(SlotChangedEvent(-1, false))
            fire(CollectionChangedEvent)
            fire(DeckChangedEvent)
        }
    }

    private fun swapCardsInDeck(firstNum: Int, secondNum: Int, state: BackpackState) {
        state.currentDeck.swapCards(firstNum, secondNum)
        with(state.events) {
            fire(GiveCardBackEvent(firstNum, false))
            fire(GiveCardBackEvent(secondNum, false))
            fire(SlotChangedEvent(firstNum, false))
            fire(SlotChangedEvent(secondNum, false))
        }
    }

    private fun putCardFromDeckInEmptySlot(firstNum: Int, secondNum: Int, state: BackpackState) {
        state.currentDeck.swapCards(firstNum, secondNum)
        with(state.events) {
            fire(GiveCardBackEvent(firstNum, false))
            fire(SlotChangedEvent(firstNum, false))
            fire(SlotChangedEvent(secondNum, false))
        }
    }

    private fun putCardFromBackpackInDeck(card: Card, slot: Int, state: BackpackState) {
        if (!state.currentDeck.canAddCards()) {
            val warningEvent = WarningParent.ShowWarningEvent(WarningParent.Level.MID, "Deck already is at maximum size!")
            state.warningEvents.fire(warningEvent)
            return
        }
        state.currentDeck.addToDeck(slot, card.name)
        updateCardsInCollection(state)
        with(state.events) {
            fire(CollectionChangedEvent)
            fire(SlotChangedEvent(slot, false))
        }
    }

    private fun putCardFromDeckInBackpack(slot: Int, state: BackpackState) {
        if (!state.currentDeck.canRemoveCards()) {
            val warningEvent = WarningParent.ShowWarningEvent(WarningParent.Level.MID, "Deck already is at minimum size!")
            state.warningEvents.fire(warningEvent)
            return
        }
        state.currentDeck.removeFromDeck(slot)
        updateCardsInCollection(state)
        with(state.events) {
            fire(GiveCardBackEvent(slot, false))
            fire(SlotChangedEvent(slot, false))
            fire(CollectionChangedEvent)
        }
    }

    private fun sortingModeChanged(state: BackpackState) {
        updateCardsInCollection(state)
        state.events.fire(CollectionChangedEvent)
    }

    private fun swapBackpackWithDeckCard(backpackCard: Card, deckSlot: Int, state: BackpackState) {
        state.currentDeck.removeFromDeck(deckSlot)
        state.currentDeck.addToDeck(deckSlot, backpackCard.name)
        updateCardsInCollection(state)
        with(state.events) {
            fire(GiveCardBackEvent(deckSlot, false))
            fire(SlotChangedEvent(deckSlot, false))
            fire(CollectionChangedEvent)
        }
    }

    private fun CustomGroup.backpackSide(
        state: BackpackState,
        worldWidth: Float,
        creator: ScreenCreator
    ) = with(creator) {
        val parent = box {
            backgroundHandle = "backpack_backpack_background"
            width = 730f
            height = 770f
            x = worldWidth - width - 40f
            y = 0f
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER

            verticalSpacer(25f)

            box {
                flexDirection = FlexDirection.ROW
                horizontalAlign = CustomAlign.SPACE_AROUND
                verticalAlign = CustomAlign.CENTER
                relativeWidth(100f)
                syncHeight()

                label("red_wing", "Backpack", Color.White) {
                    width = 200f
                    syncHeight()
                }

                box {
                    backgroundHandle = "backpack_sort_background"
                    width = 300f
                    height = 50f
                    horizontalAlign = CustomAlign.SPACE_AROUND
                    verticalAlign = CustomAlign.CENTER
                    flexDirection = FlexDirection.ROW
                    paddingLeft = 10f
                    paddingRight = 10f

                    label("red_wing", "Sort by: ", Color.FortyWhite) {
                        syncHeight()
                        relativeWidth(22f)
                    }

                    label("red_wing", state.sortingMode.displayName, Color.Red) {
                        syncHeight()
                        relativeWidth(40f)
                        val modes = SortingMode.entries
                        var currentMode = 0
                        touchable = Touchable.enabled
                        keyboardFocusable = KeyboardFocusable.LEAF
                        onInput(GameInputs.interact) {
                            currentMode++
                            currentMode %= modes.size
                            val newMode = modes[currentMode]
                            state.sortingMode = newMode
                            setText(newMode.displayName)
                            sortingModeChanged(state)
                        }
                    }

                    box {
                        width = 1f
                        relativeHeight(80f)
                        backgroundHandle = "white_texture"
                    }

                    box(backgroundHints = arrayOf("backpack_direction_arrow", "backpack_direction_hover")) {
                        var isReverse = false
                        relativeWidth(10f)
                        onLayoutAndNow { height = width }
                        backgroundHandle = "backpack_direction_arrow"
                        touchable = Touchable.enabled
                        keyboardFocusable = KeyboardFocusable.LEAF
                        joinGroup(backpackElementsGroup)
                        badTexture("backpack sorting arrow", missingFocusTexture = true)
//                        observeInputState(
//                            GameInputs.States.focused,
//                            { backgroundHandle = "backpack_direction_hover" },
//                            { backgroundHandle = "backpack_direction_arrow" }
//                        )
                        onLayoutAndNow {
                            originX = width / 2f
                            originY = height / 2f
                        }
                        isTransform = true
                        onInput(GameInputs.interact) {
                            isReverse = !isReverse
                            rotation = if (isReverse) 180f else 0f
                            state.isSortingReverse = isReverse
                            sortingModeChanged(state)
                        }
                    }
                }
            }
            collection(state, creator)
        }
        state.collectionParent = parent
    }

    private fun CustomBox.collection(state: BackpackState, creator: ScreenCreator) = with(creator) {
        box(isScrollable = true) scrollableBox@{
            this as CustomScrollableBox
            relativeWidth(88f)
            height = 680f
            x = 20f
            scrollDirectionStart = CustomDirection.TOP
            horizontalAlign = CustomAlign.START
            wrap = CustomWrap.WRAP
            addScrollbarFromDefaults(
                CustomDirection.RIGHT,
                "backpack_scrollbar",
                "backpack_scrollbar_background",
            )
            joinGroup(backpackCollectionBackgroundGroup)
            isDropTarget = true
            flexDirection = FlexDirection.ROW
            paddingTop = 20f

            var slotsCreated = 0

            onDrop { actor ->
                if (actor !is CardActor) return@onDrop
                val info = actor.infoObject
                if (info !is CardInfoObject) return@onDrop

                if (info.isBackpack) return@onDrop
                putCardFromDeckInBackpack(info.slot, state)
            }

            state.events.watchFor<CollectionChangedEvent> {
                val cards = state.cardsInCollection

                var row = 0
                var column = 0
                cards.forEachIndexed { i, _ ->
                    if (i >= slotsCreated) {
                        box {
                            width = 160f
                            height = 160f
                            cardSlot(140f, i, true, row, column, state, creator)
                        }
                        slotsCreated++
                    } else {
                        state.events.fire(GiveCardBackEvent(i, true))
                        state.events.fire(SlotChangedEvent(i, true))
                    }
                    column++
                    if (column > 3) {
                        column = 0
                        row++
                    }
                }
                val size = cards.size
                val unaccountedSlots = slotsCreated - size + 1
                if (unaccountedSlots < 0) return@watchFor
                repeat(unaccountedSlots) { i ->
                    state.events.fire(GiveCardBackEvent(size + i, true))
                    state.events.fire(SlotChangedEvent(size + i, true))
                }
            }
            state.events.fire(CollectionChangedEvent)
        }
    }

    private fun updateCardsInCollection(state: BackpackState) {
        val allCards = SaveState.cards
        val result = allCards.toMutableList()
        val cardsInDeck = state.currentDeck.cards
        cardsInDeck.forEach { result.remove(it) }

        val protos = state.cardPrototypes
        when (state.sortingMode) {
            SortingMode.COST -> result.sortByDescending {
                (protos[it] ?: throw RuntimeException("unknown card in backpack: $it")).baseCost
            }
            SortingMode.DAMAGE -> result.sortByDescending {
                (protos[it] ?: throw RuntimeException("unknown card in backpack: $it")).baseDamage
            }
            SortingMode.NAME -> result.sortBy { it }
        }
        if (state.isSortingReverse) result.reverse()

        state.cardsInCollection = result
    }

    private fun CustomGroup.deckSide(state: BackpackState, creator: ScreenCreator) = with(creator) {
        val parent = box {
            backgroundHandle = "backpack_deck_background"
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            width = 730f
            height = 770f
            x = 40f
            y = 0f
            verticalSpacer(25f)
            topBar(state, creator)
            verticalSpacer(10f)
            box {
                relativeWidth(100f)
                height = 680f
                state.events.watchFor<DeckChangedEvent> {
                    clearChildren()
                    deck(state, creator)
                }
            }
        }
        state.deckParent = parent
    }

    private fun CustomBox.deck(state: BackpackState, creator: ScreenCreator) = with(creator) {
        val cardsPerRow = 4
        val cardSize = 140f
        val cardSlotsPerDeck = SaveState.Deck.numberOfSlots
        var rowsNeeded = cardSlotsPerDeck / cardsPerRow
        val lastRow = cardSlotsPerDeck % cardsPerRow
        if (lastRow != 0) rowsNeeded++
        box(isScrollable = true) scrollableBox@{
            this as CustomScrollableBox
            scrollDirectionStart = CustomDirection.TOP
            horizontalAlign = CustomAlign.CENTER
            wrap = CustomWrap.NONE
            addScrollbarFromDefaults(
                CustomDirection.RIGHT,
                "backpack_scrollbar",
                "backpack_scrollbar_background",
            )
            flexDirection = FlexDirection.COLUMN
            relativeWidth(100f)
            relativeHeight(100f)
            var slot = 0
            repeat(rowsNeeded) { row ->
                box {
                    flexDirection = FlexDirection.ROW
                    verticalAlign = CustomAlign.CENTER
                    relativeWidth(90f)
                    height = cardSize
                    val amountCards = if (row + 1 == rowsNeeded && lastRow != 0) lastRow else cardsPerRow
                    repeat(amountCards) { column ->
                        horizontalSpacer(20f)
                        cardSlot(cardSize, slot, false, row, column, state, creator)
                        slot++
                    }
                }
                verticalSpacer(20f)
            }
        }
    }

    private fun CustomBox.cardSlot(
        cardSize: Float,
        num: Int,
        isBackpack: Boolean,
        row: Int,
        column: Int,
        state: BackpackState,
        creator: ScreenCreator
    ) = with(creator) {

        val parent = box {
            if (!isBackpack) {
                backgroundHandle = "backpack_empty_deck_slot"
            }
            syncDimensions()
        }

        state.events.watchFor<SlotChangedEvent> { event ->
            if (event.backpack != isBackpack || (event.slot != num && event.slot != -1)) return@watchFor

            val cardName = if (event.backpack) {
                state.cardsInCollection.getOrNull(num)
            } else {
                state.currentDeck.cardPositions[num]
            }

            val grid = if (isBackpack) state.backpackFocusGrid else state.deckFocusGrid

            parent.clearChildren()
            grid.remove(column, row)

            val (_, actor) = with(parent) {
                cardActorOrEmptySlot(cardName, cardSize, isBackpack, num, state, creator)
            }

            grid.set(column, row, actor)
        }

        state.events.fire(SlotChangedEvent(num, isBackpack))
    }

    private fun CustomBox.cardActorOrEmptySlot(
        cardName: String?,
        cardSize: Float,
        isBackpack: Boolean,
        num: Int,
        state: BackpackState,
        creator: ScreenCreator
    ): Pair<Card?, InputActor> = with(creator) {
        var card: Card? = null
        val actor: InputActor = if (cardName != null)  {
            card = state.getCardInstance(cardName, screen, state)
            val actor = actor(card.actor) {
                height = cardSize
                width = cardSize
                isDraggable = true
                touchable = Touchable.enabled
                isDropTarget = true
                keyboardFocusable = KeyboardFocusable.LEAF
                infoObject = CardInfoObject(isBackpack, num)
                leaveAllGroups()
                joinGroup(backpackElementsGroup)
                joinGroup(if (isBackpack) backpackCardInCollectionGroup else backpackCardInDeckGroup )
            }
            actor
        } else box {
            height = cardSize
            width = cardSize
            touchable = Touchable.enabled
            isDropTarget = true
            keyboardFocusable = KeyboardFocusable.LEAF
            joinGroup(backpackElementsGroup)
            joinGroup(backpackSlotInDeckGroup)

            onDrop { actor ->
                if (actor !is CardActor) return@onDrop
                val info = actor.infoObject
                if (info !is CardInfoObject) return@onDrop

                if (isBackpack) {
                    if (!info.isBackpack) putCardFromDeckInBackpack(info.slot, state)
                    return@onDrop
                }

                if (info.isBackpack) {
                    putCardFromBackpackInDeck(actor.card, num, state)
                } else {
                    putCardFromDeckInEmptySlot(info.slot, num, state)
                }
            }
        }
        card to actor
    }

    private fun CustomBox.topBar(state: BackpackState, creator: ScreenCreator) = with(creator) {
        box {
            flexDirection = FlexDirection.ROW
            horizontalAlign = CustomAlign.SPACE_AROUND
            verticalAlign = CustomAlign.CENTER
            relativeWidth(100f)
            syncHeight()

            inputField("red_wing", Color.Black, backgroundHints = arrayOf("black_texture")) {
                maxLength = 20
                touchable = Touchable.enabled
                joinGroup(backpackElementsGroup)
                keyboardFocusable = KeyboardFocusable.LEAF
                setText("Hello World")
                width = 200f
                height = 40f
                isDisabled = true
                observeInputState(
                    GameInputs.States.focused,
                    {
                        isDisabled = false
                        backgroundHandle = "black_texture"
                    },
                    {
                        isDisabled = true
                        backgroundHandle = null
                    }
                )
            }

            box {
                flexDirection = FlexDirection.ROW
                height = 50f
                width = 5f * 65f
                repeat(5) { num ->
                    val n = num + 1
                    box(backgroundHints = arrayOf("backpack_$n", "backpack_${n}_hover")) {
                        width = 65f
                        height = 50f
                        joinGroup(backpackElementsGroup)
                        touchable = Touchable.enabled
                        keyboardFocusable = KeyboardFocusable.LEAF
                        backgroundHandle = if (state.currentDeck.id == n) {
                            "backpack_${n}_hover"
                        } else {
                            "backpack_$n"
                        }
                        state.events.watchFor<DeckChangedEvent> {
                            backgroundHandle = if (state.currentDeck.id == n) {
                                "backpack_${n}_hover"
                            } else {
                                "backpack_$n"
                            }
                        }
                        onInput(GameInputs.interact) {
                            if (state.currentDeck.id == n) return@onInput
                            switchToDeck(n, state)
                        }
                    }
                }
            }
        }
    }

    private data class BackpackState(
        var currentDeck: Deck,
        val cardPrototypes: Map<String, CardPrototype>,
        val createdCards: MutableList<Card>,
        var cardsInCollection: List<String>,
        val events: EventPipeline,
        val warningEvents: EventPipeline,
        var sortingMode: SortingMode,
        var isSortingReverse: Boolean,
        var deckFocusGrid: InputManager.FocusGrid,
        var backpackFocusGrid: InputManager.FocusGrid,
        var deckParent: CustomBox?,
        var collectionParent: CustomBox?
    ) {

        fun getCardInstance(name: String, screen: OnjScreen, state: BackpackState): Card {
            val created = createdCards.find { it.name == name }
            if (created != null) {
                createdCards.remove(created)
                return created
            }
            val proto = cardPrototypes[name]
                ?: throw RuntimeException("unknown card $name in Backpack")
            val card = proto.create(screen)

            card.actor.onDrop { actor ->
                if (actor !is CardActor) return@onDrop

                val otherInfo = actor.infoObject
                val thisInfo = card.actor.infoObject
                if (thisInfo !is CardInfoObject) return@onDrop
                if (otherInfo !is CardInfoObject) return@onDrop

                if (thisInfo.isBackpack) return@onDrop

                if (!otherInfo.isBackpack) {
                    swapCardsInDeck(thisInfo.slot, otherInfo.slot, state)
                } else {
                    swapBackpackWithDeckCard(actor.card, thisInfo.slot, state)
                }
            }

            state.events.watchFor<GiveCardBackEvent> { event ->
                val info = card.actor.infoObject
                if (info !is CardInfoObject) return@watchFor
                if (event.backpack != info.isBackpack || (info.slot != event.slot && info.slot != -1)) return@watchFor
                card.actor.infoObject = null
                state.giveCardInstanceBack(card)
            }

            return card
        }

        fun giveCardInstanceBack(card: Card) {
            createdCards.add(card)
        }
    }

    enum class SortingMode(val displayName: String) {
        COST("cost"),
        NAME("name"),
        DAMAGE("damage"),
    }

    private data class CardInfoObject(val isBackpack: Boolean, val slot: Int)

    private data object DeckChangedEvent
    private data object CollectionChangedEvent

    private data class GiveCardBackEvent(val slot: Int, val backpack: Boolean)
    private data class SlotChangedEvent(val slot: Int, val backpack: Boolean)

}
