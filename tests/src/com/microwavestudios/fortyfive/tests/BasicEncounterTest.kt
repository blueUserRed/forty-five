package com.microwavestudios.fortyfive.tests

import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.testing.GameControllerTest
import com.microwavestudios.fortyfive.utils.Timeline

class BasicEncounterTest : GameControllerTest() {

    override val name: String = "BasicEncounterTest"

    override fun testTimeline(): Timeline = Timeline.timeline {
        var healthCrossCheck = 100
        var enemy0HealthCrossCheck = enemy(0).health
        var enemy1HealthCrossCheck = enemy(1).health
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
            handleNextParry(false, { healthCrossCheck -= it.damage })
            handleNextParry(false, { healthCrossCheck -= it.damage })
            waitForTurnBegin()
            assert(aCurrentPlayerHealth() equal deferred { healthCrossCheck })
        }
        test("reserves on new turn") {
            assert(aCurReserves() equal 5)
        }
        test("shot") {
            shoot()
            action { enemy0HealthCrossCheck -= 16 }
            assert(aCardInSlot(5).aName() equal "bullet")
            assert(aCardInSlot(2).aName() equal "workerBullet")
            assert(aCardInSlot(4).isNull())
            assert(aCardInSlot(1).isNull())
            assert(aEnemy(0).aCurrentHealth() equal deferred { enemy0HealthCrossCheck })
        }
        test("enemy switch and shot") {
            switchEnemy(enemy(1))
            shoot()
            action { enemy1HealthCrossCheck -= 6 }
            assert(aCardInSlot(3).aName() equal "workerBullet")
            assert(aEnemy(1).aCurrentHealth() equal deferred { enemy1HealthCrossCheck })
        }
        test("bewitched bullet") {
            dragCardOnSlot("bewitchedBullet", 5)
            shoot()
            action { enemy1HealthCrossCheck -= 7 }
            assert(aCardInSlot(2).aName() equal "workerBullet")
            assert(aEnemy(1).aCurrentHealth() equal deferred { enemy1HealthCrossCheck })
        }
        dragCardOnSlot("bullet", 5)
        dragCardOnSlot("bullet", 4)
        holster()
        waitForTurnBegin()
        holster()
        test("parry") {
            handleNextParry(true)
            handleNextParry(true)
            waitForTurnBegin()
            assert(aCurrentPlayerHealth() equal deferred { healthCrossCheck })
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
