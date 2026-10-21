package com.microwavestudios.fortyfive.fullGameTests

import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.profile.MapSaver
import com.microwavestudios.fortyfive.testing.FullGameTest
import com.microwavestudios.fortyfive.testing.mockservices.MockMapSaver
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.Timeline.TimelineBuilderDSL

class EncounterFullGameTest : FullGameTest() {

    override val name: String = "EncounterFullGameTest"

    override fun testTimeline(): Timeline = Timeline.timeline {
        openEncounterScreen()
        delayUntil { false }
    }

    private fun TimelineBuilderDSL.openEncounterScreen() {
        waitForNextScreen()
        waitUntilScreenIsLoaded()
        assertOnScreen("titleScreen")
        moveMouseToNamedActor("title-screen-option-Start")
        delay(10)
        leftClick()
        waitForNextScreen()
        waitUntilScreenIsLoaded()
        assertOnScreen("mapScreen")
        moveMouseToWindowRelative(0.1f, 0.5f)
        delay(10)
        leftClick()
        delay(10)
        leftClick()
        delay(10)
        moveMouseToWindowRelative(0.5f, 0.1f)
        delay(100)
        leftClick()
        delay(400)
        moveMouseToNamedActor("mapStartButton")
        delay(10)
        leftClick()
        waitForNextScreen()
        waitUntilScreenIsLoaded()
        assertOnScreen("gameScreen")
    }

    override fun cardCollection(): List<CardType> = List(Deck.minDeckSize) {
        CardType.fromString("bullet")
    }

    override fun skipIntroScreen(): Boolean = true

    override fun playerMoney(): Int = 100

    override fun mapSaver(): MapSaver = MockMapSaver("test_map1", 0)

}
