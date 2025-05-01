package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.SaveState.Deck
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.keyInput.GameInputs
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
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.Timeline
import onj.value.OnjArray
import java.lang.RuntimeException

object BackpackCreator {

    const val backpackElementsGroup: String = "backpack-element"

    fun ScreenCreator.getSharedBackpack(
        worldWidth: Float,
        worldHeight: Float,
    ): Pair<CustomGroup, NavbarCreator.NavBarObject> {

        val cardsOnj = ConfigFileManager.getConfigFile("cards")
        val cardPrototypes = Card.getFrom(cardsOnj.get<OnjArray>("cards"), initializer = { screen.addDisposable(it) })

        val state = BackpackState(
            SaveState.curDeck,
            cardPrototypes,
            mutableMapOf(),
            EventPipeline()
        )

        val backpack = newGroup {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight

            deckSide(state, this@getSharedBackpack)

            box {
                backgroundHandle = "backpack_backpack_background"
                width = 730f
                height = 770f
                x = worldWidth - width - 30f
                y = 0f
            }

        }

        state.events.fire(DeckChangedEvent)

        val navbarObject = NavbarCreator.NavBarObject(
            "Backpack",
            { Timeline.timeline {

            } },
            { Timeline.timeline {

            } },
        )

        return backpack to navbarObject
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
            verticalSpacer(18f)
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
        box(isScrollable = true) {
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
                        slot++
                        val card = state.currentDeck.cardPositions[slot]
                        cardSlot(card, cardSize, state, creator)
                    }
                }
                verticalSpacer(20f)
            }
        }
    }

    private fun CustomBox.cardSlot(card: String?, cardSize: Float, state: BackpackState, creator: ScreenCreator) = with(creator) {
        val slot = if (card != null) box {
            val texturePromise = texturePromiseForCard(card, screen, state)
            texturePromise?.then { texture ->
                manualBackground = TextureRegionDrawable(texture)
            }
            height = cardSize
            width = cardSize
        } else box {
            height = cardSize
            width = cardSize
            backgroundHandle = "backpack_empty_deck_slot"
        }
    }

    private fun texturePromiseForCard(name: String, screen: OnjScreen, state: BackpackState): Promise<Texture>? {
        val created = state.createdCards[name]
        val card = if (created == null) {
            val prototype = state.cardPrototypes.find { it.name == name }
                ?: throw RuntimeException("unknown card in backpack $name")
            val new = prototype.create(screen)
            state.createdCards[name] = new
            new
        } else {
            created
        }
        return card.actor.currentTexturePromise()
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
                    }
                }
            }
        }
    }

    private data class BackpackState(
        var currentDeck: Deck,
        val cardPrototypes: List<CardPrototype>,
        val createdCards: MutableMap<String, Card>,
        val events: EventPipeline
    )

    private data object DeckChangedEvent

}