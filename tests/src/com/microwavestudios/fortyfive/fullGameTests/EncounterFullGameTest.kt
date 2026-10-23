package com.microwavestudios.fortyfive.fullGameTests

import com.badlogic.gdx.Input
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.profile.MapSaver
import com.microwavestudios.fortyfive.testing.FullGameTest
import com.microwavestudios.fortyfive.testing.mockservices.MockMapSaver
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.Timeline.TimelineBuilderDSL
import com.microwavestudios.fortyfive.utils.Utils

class EncounterFullGameTest : FullGameTest() {

    override val name: String = "EncounterFullGameTest"

    override fun testTimeline(): Timeline = Timeline.timeline {
        openEncounterScreen()
        playEncounter()
    }

    private fun TimelineBuilderDSL.playEncounter() = later {
        val controller = FortyFive.currentScreen!!.findController<GameControllerImpl>()!!
        val cardHand = controller.cardHand
        val afterlife = controller.afterlife
        val revolver = controller.revolver
        val slot5 = revolver.slots[Utils.convertSlotRepresentation(5) - 1]
        val slot4 = revolver.slots[Utils.convertSlotRepresentation(4) - 1]
        val slot3 = revolver.slots[Utils.convertSlotRepresentation(3) - 1]
        val slot1 = revolver.slots[Utils.convertSlotRepresentation(1) - 1]

        delay(10)
        waitUntilUiIsFree(controller)
        later {
            val deadBullet = cardHand.allCards().first()
            assert(
                { deadBullet.name == "deadBullet" },
                { "First bullet in hand is ${deadBullet.name}, expected deadBullet" }
            )
            pressKey(Input.Keys.C) // select zombie bullet via keyboard shortcut
            delay(10)
            assert(
                { deadBullet.presentation.forceGetActor().detailWidget != null },
                "HoverDetail not shown after selecting with keyboard in hand"
            )
            pressKey(Input.Keys.ENTER)
            delay(10)
            pressKey(Input.Keys.NUM_2)
            delay(10)
            pressKey(Input.Keys.ENTER)
            delay(10)
            waitUntilUiIsFree(controller)
            assert({ afterlife.isOpen }, "Expected Afterlife to be open after playing deadBullet")
            assert(
                { afterlife.cards.first() == deadBullet },
                { "Expected deadBullet to be first in afterlife, actually is ${afterlife.cards.first()}" }
            )
            holdKey(Input.Keys.SHIFT_LEFT) {
                delay(10)
                pressKey(Input.Keys.A)
                delay(10)
            }
            assert(
                { deadBullet.presentation.forceGetActor().detailWidget != null },
                "HoverDetail not shown after selecting with keyboard in afterlife"
            )
        }
        later {
            val bullet = cardHand.allCards().last()
            assert(
                { bullet.name == "bullet" },
                { "Expected last card in hand to be bullet, is: ${bullet.name}" }
            )
            moveMouseToActor(bullet.presentation.forceGetActor())
            delay(10)
            holdLeftClick {
                delay(10)
                moveMouseBy(0, 10, drag = true)
                delay(10)
                moveMouseToActor(slot5.forceGetActor().actor, drag = true)
                delay(10)
            }
            delay(10)
            waitUntilUiIsFree(controller)
            assert(
                { slot5.card == bullet },
                { "Expected card in slot 5 to be bullet after dragging it there, is: ${slot5.card}" }
            )
        }
        later {
            val bullet = cardHand.allCards().last()
            assert(
                { bullet.name == "bullet" },
                { "Expected last card in hand to be bullet, is: ${bullet.name}" }
            )
            delay(10)
            moveMouseToActor(bullet.presentation.forceGetActor())
            delay(10)
            holdLeftClick {
                delay(10)
                moveMouseBy(0, 10, drag = true)
                delay(10)
                moveMouseToActor(slot4.forceGetActor().actor, drag = true)
                delay(10)
            }
            delay(10)
            waitUntilUiIsFree(controller)
            assert(
                { slot4.card == bullet },
                { "Expected card in slot 4 to be bullet after dragging it there, is: ${slot4.card}" }
            )
        }
        later {
            val protectiveBullet = cardHand.allCards()[1]
            moveMouseToActor(protectiveBullet.presentation.forceGetActor())
            delay(10)
            holdLeftClick {
                delay(10)
                moveMouseBy(0, 10, drag = true)
                delay(10)
                moveMouseToActor(slot3.forceGetActor().actor)
                delay(10)
            }
            delay(700)
        }
        later {
            moveMouseToActor(slot5.forceGetActor().actor)
            delay(10)
            leftClick()
            delay(10)
            waitUntilUiIsFree(controller)
        }
        later {
            moveMouseToNamedActor("holster-button")
            delay(10)
            leftClick()
            delay(10)
            waitUntilUiIsFree(controller)
            leftClick()
            delay(2_000)
            moveMouseToNamedActor("parry-button")
            delay(10)
            leftClick()
            delay(1_400)
            moveMouseToNamedActor("pass-button")
            delay(10)
            leftClick()
            waitUntilUiIsFree(controller)
            assert(
                { slot1.card?.name == "bullet" },
                { "Card in slot 1 expected to be bullet, is: ${slot1.card}" }
            )
            assert(
                { controller.curPlayerLives == 94 },
                { "Expected player health to be 94, is: ${controller.curPlayerLives}" }
            )
        }
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

    private fun TimelineBuilderDSL.waitUntilUiIsFree(controller: GameControllerImpl) {
        delayUntil { !controller.isUIFrozen }
    }

    override fun cardCollection(): List<CardType> = listOf(
        CardType.fromString("deadBullet"),
        CardType.fromString("incendiaryBullet"),
        CardType.fromString("protectiveBullet"),
        CardType.fromString("bullet"),
        CardType.fromString("bullet"),
        CardType.fromString("bullet"),
        CardType.fromString("bullet"),
        CardType.fromString("bullet"),
        CardType.fromString("bullet"),
        CardType.fromString("bullet"),
        CardType.fromString("bullet"),
        CardType.fromString("bullet"),
        CardType.fromString("bullet"),
        CardType.fromString("bullet"),
    )

    override fun skipIntroScreen(): Boolean = true

    override fun playerMoney(): Int = 100

    override fun mapSaver(): MapSaver = MockMapSaver("test_map1", 0)

}
