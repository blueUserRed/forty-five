package com.microwavestudios.fortyfive.fullGameTests

import com.badlogic.gdx.Input
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.profile.MapSaver
import com.microwavestudios.fortyfive.screen.screens.ShopScreen
import com.microwavestudios.fortyfive.testing.FullGameTest
import com.microwavestudios.fortyfive.testing.mockservices.MockMapSaver
import com.microwavestudios.fortyfive.utils.Timeline

class ShopFullGameTest : FullGameTest() {

    override val name: String = "ShopFullGameTest"


    override fun testTimeline(): Timeline = Timeline.timeline {

        // go to shop screen
        waitForNextScreen()
        assertOnScreen("introScreen")
        waitForNextScreen()
        waitUntilScreenIsLoaded()
        assertOnScreen("titleScreen")
        brieflyPressKey(Input.Keys.TAB)
        brieflyPressKey(Input.Keys.ENTER)
        waitForNextScreen()
        waitUntilScreenIsLoaded()
        assertOnScreen("mapScreen")
        moveMouseToWindowRelative(0.9f, 0.5f)
        delay(10)
        leftClick()
        delay(700)
        moveMouseToNamedActor("mapStartButton")
        delay(10)
        leftClick()
        waitForNextScreen()
        waitUntilScreenIsLoaded()
        assertOnScreen("shopScreen")

        include(testBuyCard())
    }

    private fun testBuyCard() = Timeline.later {
        val screen = FortyFive.currentScreen!!
        val inputManager = screen.inputManager

        val cardToBuy = inputManager.getAllActorsInGroup(ShopScreen.cardGroup)!![1].actor
        cardToBuy as CardActor

        // attempt to add card to backpack with deck below min deck size ------------------------------------

        delay(100)
        moveMouseToActor(cardToBuy)
        delay(10)
        assert({ cardToBuy.isInInputState(GameInputs.States.focused) }, "card is not focused")
        assert({ cardToBuy.detailWidget != null }, "card does not display hover detail")
        holdLeftClick {
            moveMouseBy(0, 10, drag = true)
            delay(10)
            moveMouseToNamedActor(ShopScreen.toBackpackName)
            delay(10)
        }
        delay(10)
        val profile = FortyFive.profileManager.currentProfile!!
        val deck = profile.currentRunDeck!!
        assert(
            { profile.backpack!!.all { it.name != cardToBuy.card.name } },
            "Card was bought and put into collection despite deck being below min size"
        )

        // add card to deck ---------------------------------------------------------------------------------

        moveMouseToActor(cardToBuy)
        delay(100)
        holdLeftClick {
            moveMouseBy(0, 10, drag = true)
            delay(10)
            moveMouseToNamedActor(ShopScreen.toDeckName)
            delay(10)
        }
        delay(10)
        assert(
            { deck.cards.any { it.name == cardToBuy.card.name } },
            "Buying card failed"
        )
        assert(
            { profile.playerMoney == 50 },
            { "Money after buying card is ${profile.playerMoney}, expected 50" }
        )

        // attempt to buy card again ------------------------------------------------------------------------

        moveMouseToActor(cardToBuy)
        delay(100)
        holdLeftClick {
            moveMouseBy(0, 10, drag = true)
            delay(10)
            moveMouseToNamedActor(ShopScreen.toDeckName)
            delay(10)
        }
        delay(10)
        later {
            val amountOfCards = deck.cards.count { it.name == cardToBuy.card.name }
            assert(
                { amountOfCards == 1 },
                "Wrong amount of cards in deck after attempting to buy card again: $amountOfCards, expected: 1"
            )
        }
        assert(
            { profile.playerMoney == 50 },
            { "Money after trying to buy card again is ${profile.playerMoney}, expected 50" }
        )

        // attempt to buy too expensive card ----------------------------------------------------------------

        val toExpensiveCard = inputManager.getAllActorsInGroup(ShopScreen.cardGroup)!![2].actor
        toExpensiveCard as CardActor

        delay(100)
        moveMouseToActor(toExpensiveCard)
        delay(100)
        holdLeftClick {
            moveMouseBy(0, 10, drag = true)
            delay(10)
            moveMouseToNamedActor(ShopScreen.toDeckName)
            delay(10)
        }
        delay(10)
        assert(
            { profile.backpack!!.all { it.name != toExpensiveCard.card.name } },
            "Card was bought and put into collection despite card being too expensive"
        )
        assert(
            { profile.playerMoney == 50 },
            { "Money after failing to buy card is ${profile.playerMoney}, expected 50" }
        )

        // add card to backpack -----------------------------------------------------------------------------

        val cardToBuy2 = inputManager.getAllActorsInGroup(ShopScreen.cardGroup)!![3].actor
        cardToBuy2 as CardActor

        delay(100)
        moveMouseToActor(cardToBuy2)
        delay(100)
        holdLeftClick {
            moveMouseBy(0, 10, drag = true)
            delay(10)
            moveMouseToNamedActor(ShopScreen.toBackpackName)
            delay(10)
        }
        delay(10)
        assert(
            { profile.backpack!!.any { it.name == cardToBuy2.card.name } },
            "Buying card and adding it to backpack failed"
        )
        assert(
            { profile.playerMoney == 0 },
            { "Money after buying card is ${profile.playerMoney}, expected 0" }
        )
    }

    override fun cardCollection(): List<CardType> = List(Deck.minDeckSize - 1) {
        CardType.fromString("bullet")
    }

    override fun playerMoney(): Int = 100

    override fun mapSaver(): MapSaver = MockMapSaver("test_map1", 0)
}
