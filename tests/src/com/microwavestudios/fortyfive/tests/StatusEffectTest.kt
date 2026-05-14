package com.microwavestudios.fortyfive.tests

import com.microwavestudios.fortyfive.game.Burning
import com.microwavestudios.fortyfive.game.Frozen
import com.microwavestudios.fortyfive.game.Poison
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.testing.GameControllerTest
import com.microwavestudios.fortyfive.utils.Timeline

class StatusEffectTest : GameControllerTest() {

    override val name: String = "StatusEffectTest"

    override fun testTimeline(): Timeline = Timeline.timeline {
        var enemyHealthCrossCheck = enemy(0).health
        waitForTurnBegin()
        test("ice bullet") {
            dragCardOnSlot("incendiaryBullet", 5)
            dragCardOnSlot("iceBullet", 3)
            assert(aFindPlayerStatusEffect<Frozen>().isNotNull())
            shoot()
            assert(aFindPlayerStatusEffect<Frozen>().isNotNull())
            action { enemyHealthCrossCheck -= 7 }
            assert(aCardInSlot(5).isNull())
            assert(aCardInSlot(3).aName() equal "iceBullet")
            assert(aEnemy(0).aFindStatusEffect<Burning>().isNotNull())
        }
        test("poison bullet") {
            dragCardOnSlot("poisonBullet", 5)
            shoot()
            assert(aFindPlayerStatusEffect<Frozen>().isNull())
            action { enemyHealthCrossCheck -= 4 + 2 }
            assert(aEnemy(0).aCurrentHealth() equal deferred { enemyHealthCrossCheck })
            assert(aEnemy(0).aFindStatusEffect<Poison>().isNotNull())
        }
        holster()
        waitForTurnBegin()
        test("poison effect") {
            action { enemyHealthCrossCheck -= 6 }
            assert(aEnemy(0).aCurrentHealth() equal deferred { enemyHealthCrossCheck })
        }
        test("incendiary effect") {
            dragCardOnSlot("bullet", 5)
            shoot()
            action { enemyHealthCrossCheck -= 6 + 3 }
            dragCardOnSlot("bullet", 5)
            shoot()
            action { enemyHealthCrossCheck -= 4 + 2 }
            shoot()
            action { enemyHealthCrossCheck -= 6 + 3 }
            dragCardOnSlot("bullet", 5)
            assert(aEnemy(0).aFindStatusEffect<Burning>().isNotNull())
            shoot()
            action { enemyHealthCrossCheck -= 6 + 3 }
            waitForFreeUi()
            assert(aEnemy(0).aFindStatusEffect<Burning>().isNull())
            assert(aEnemy(0).aCurrentHealth() equal deferred { enemyHealthCrossCheck })
        }
        test("win encounter") {
            holster()
            assert(aHasWon())
        }
    }

    override fun seed(): Long = 123456789

    override fun deck(): List<CardType> = listOf(
        CardType.fromString("incendiaryBullet"),
        CardType.fromString("poisonBullet"),
        CardType.fromString("iceBullet"),
    )

    override fun enemies(): List<String> = listOf(
        "Outlaw_test"
    )
}