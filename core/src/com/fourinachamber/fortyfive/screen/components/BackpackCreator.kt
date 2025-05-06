package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.SaveState.Deck
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.keyInput.GameInputs
import com.fourinachamber.fortyfive.keyInput.InputActor
import com.fourinachamber.fortyfive.keyInput.KeyboardFocusable
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.CustomDirection
import com.fourinachamber.fortyfive.screen.general.customActor.CustomScrollableBox
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWrap
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
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

    fun ScreenCreator.getSharedBackpack(
        worldWidth: Float,
        worldHeight: Float,
    ): Pair<CustomGroup, NavbarCreator.NavBarObject> {

        val cardsOnj = ConfigFileManager.getConfigFile("cards")
        val cardPrototypes = Card.getFrom(cardsOnj.get<OnjArray>("cards"), initializer = { screen.addDisposable(it) })

        val state = BackpackState(
            SaveState.curDeck,
            cardPrototypes,
            mutableListOf(),
            listOf(),
            EventPipeline()
        )
        state.cardsInCollection = cardsToDisplayInBackpack(state)

        val backpack = newGroup {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight

            deckSide(state, this@getSharedBackpack)

            backpackSide(state, worldWidth, this@getSharedBackpack)
        }

        state.events.fire(DeckChangedEvent)
        screen.inputManager.enableDragAndDrop(backpackCardInDeckGroup, backpackCardInDeckGroup)
        screen.inputManager.enableDragAndDrop(backpackCardInDeckGroup, backpackSlotInDeckGroup)
        screen.inputManager.enableDragAndDrop(backpackCardInDeckGroup, backpackCardInCollectionGroup)
        screen.inputManager.enableDragAndDrop(backpackCardInCollectionGroup, backpackCardInDeckGroup)

        val navbarObject = NavbarCreator.NavBarObject(
            "Backpack",
            { Timeline.timeline {

            } },
            { Timeline.timeline {

            } },
        )

        return backpack to navbarObject
    }

    private fun switchToDeck(num: Int, state: BackpackState) {
//        SaveState.curDeckNbr = num
//        val deck = SaveState.curDeck
//        state.currentDeck = deck
//        state.events.fire(GiveCardsBackEvent)
//        state.events.fire(DeckChangedEvent)
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

    private fun putCardFromDeckInEmptySlot(card: Card, slot: Int) {
    }

    private fun putCardFromBackpackInDeck(card: Card, slot: Int) {
    }

    private fun swapBackpackWithDeckCard(backpackCard: Card, deckCard: Card) {
    }

    private fun CustomGroup.backpackSide(
        state: BackpackState,
        worldWidth: Float,
        creator: ScreenCreator
    ) = with(creator) {
        box {
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
                }
            }
            collection(state, creator)
        }
    }

    private fun CustomBox.collection(state: BackpackState, creator: ScreenCreator) = with(creator) {
        val cardsPerRow = 4

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
            flexDirection = FlexDirection.ROW
            paddingTop = 20f

            val cards = cardsToDisplayInBackpack(state)

            cards.forEachIndexed { i, card ->
                box {
                    width = 160f
                    height = 160f
                    cardSlot(140f, i, true, state, creator)
                }
            }
        }
    }

    private fun cardsToDisplayInBackpack(state: BackpackState): List<String> {
        val allCards = SaveState.cards
        val result = allCards.toMutableList()
        val cardsInDeck = state.currentDeck.cards
        cardsInDeck.forEach { result.remove(it) }
        return result
    }

    private fun CustomGroup.deckSide(state: BackpackState, creator: ScreenCreator) = with(creator) {
        box {
            backgroundHandle = "backpack_deck_background"
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            width = 730f
            height = 770f
            x = 40f
            y = 0f
            debug()
            verticalSpacer(25f)
            topBar(state, creator)
            verticalSpacer(10f)
            box {
                relativeWidth(100f)
                height = 680f
                state.events.watchFor<DeckChangedEvent> { t ->
                    clearChildren()
                    deck(state, creator)
                }
            }
        }
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
                    repeat(amountCards) {
                        horizontalSpacer(20f)
                        val card = state.currentDeck.cardPositions[slot]
                        cardSlot(cardSize, slot, false, state, creator)
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
        state: BackpackState,
        creator: ScreenCreator
    ) = with(creator) {

        val parent = box {
            debug()
            syncDimensions()
        }

        state.events.watchFor<SlotChangedEvent> { event ->
            if (event.backpack != isBackpack || event.slot != num) return@watchFor

            val cardName = if (event.backpack) {
                state.cardsInCollection.getOrNull(num)
            } else {
                state.currentDeck.cardPositions[num]
            }

            parent.clearChildren()

            with(parent) {
                cardActorOrEmptySlot(cardName, cardSize, isBackpack, num, state, creator)
            }
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
                debug()
                height = cardSize
                width = cardSize
                isDraggable = true
                touchable = Touchable.enabled
                isDropTarget = true
                keyboardFocusable = KeyboardFocusable.LEAF
                infoObject = CardInfoObject(isBackpack, num)
                joinGroup(backpackElementsGroup)
                joinGroup(if (isBackpack) backpackCardInCollectionGroup else backpackCardInDeckGroup )
            }
            actor
        } else box {
            debug()
            height = cardSize
            width = cardSize
            touchable = Touchable.enabled
            isDropTarget = true
            keyboardFocusable = KeyboardFocusable.LEAF
            backgroundHandle = "backpack_empty_deck_slot"
            joinGroup(backpackElementsGroup)
            joinGroup(backpackSlotInDeckGroup)

            onDrop { actor ->
                if (actor !is CardActor) return@onDrop
                val info = actor.infoObject
                if (info !is CardInfoObject) return@onDrop

                if (info.isBackpack) {
                    putCardFromBackpackInDeck(actor.card, num)
                } else {
                    putCardFromDeckInEmptySlot(actor.card, num)
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
                        backgroundHandle = "backpack_$n"
                        width = 65f
                        height = 50f
                        joinGroup(backpackElementsGroup)
                        touchable = Touchable.enabled
                        keyboardFocusable = KeyboardFocusable.LEAF
                        observeInputState(
                            GameInputs.States.focused,
                            { backgroundHandle = "backpack_${n}_hover" },
                            { backgroundHandle = "backpack_$n" }
                        )
                        onInput(GameInputs.interact) {
                            switchToDeck(n, state)
                        }
                    }
                }
            }
        }
    }

    private data class BackpackState(
        var currentDeck: Deck,
        val cardPrototypes: List<CardPrototype>,
        val createdCards: MutableList<Card>,
        var cardsInCollection: List<String>,
        val events: EventPipeline
    ) {

        fun getCardInstance(name: String, screen: OnjScreen, state: BackpackState): Card {
            val created = createdCards.find { it.name == name }
            if (created != null) {
                createdCards.remove(created)
                return created
            }
            val proto = cardPrototypes.find { it.name == name }
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
                    swapBackpackWithDeckCard(actor.card, card)
                }
            }

            state.events.watchFor<GiveCardBackEvent> { event ->
                val info = card.actor.infoObject
                if (info !is CardInfoObject) return@watchFor
                if (event.backpack != info.isBackpack || info.slot != event.slot) return@watchFor
                state.giveCardInstanceBack(card)
            }

            return card
        }

        fun giveCardInstanceBack(card: Card) {
            createdCards.add(card)
        }
    }

    private data class CardInfoObject(val isBackpack: Boolean, val slot: Int)

    private data object DeckChangedEvent

    private data class GiveCardBackEvent(val slot: Int, val backpack: Boolean)
    private data class SlotChangedEvent(val slot: Int, val backpack: Boolean)

}
