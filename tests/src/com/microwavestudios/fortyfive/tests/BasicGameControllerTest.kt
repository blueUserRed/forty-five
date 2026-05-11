package com.microwavestudios.fortyfive.tests

import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.testing.GameControllerTest
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager
import com.microwavestudios.fortyfive.utils.Timeline

class BasicGameControllerTest(profileManager: MockProfileManager) : GameControllerTest(profileManager) {

    override val name: String = "BasicGameControllerTest"

    override fun testTimeline(): Timeline = Timeline.timeline {
        waitForTurnBegin()
        test("play workers bullet") {
            dragCardOnSlot("workerBullet", 1)
            assert(aCurReserves() equal 3)
        }
        test("play bullet in used slot") {
            dragCardOnSlot("bigBullet", 1)
            assert(aCurReserves() equal 3)
            assert(aCardInSlot(1).aName() equal "workerBullet")
        }
        test("play more bullets") {
            dragCardOnSlot("bigBullet", 5)
            dragCardOnSlot("bullet", 4)
            assert(aCurReserves() equal 0)
        }
        test("play card with no reserves left") {
            dragCardOnSlot("bewitchedBullet", 3)
            assert(aCardInSlot(3).isNull())
        }
        holster()
        test("passing damage") {
            handleNextParry(false)
            handleNextParry(false)
            waitForTurnBegin()
            assert(aCurrentPlayerHealth() equal 91)
        }
        test("reserves on new turn") {
            assert(aCurReserves() equal 5)
        }

    }

    override fun seed(): Long = 123456789

    override fun deck(): List<CardType> = listOf(
        CardType(null, "bullet", null),
        CardType(null, "workerBullet", null),
        CardType(null, "bewitchedBullet", null),
        CardType(null, "bigBullet", null),
    )

    override fun enemies(): List<String> = listOf("Outlaw-1", "Outlaw-1")

}
