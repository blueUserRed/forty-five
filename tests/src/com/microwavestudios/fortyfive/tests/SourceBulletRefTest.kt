package com.microwavestudios.fortyfive.tests

import com.microwavestudios.fortyfive.game.Poison
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.enemy.EnemyAction
import com.microwavestudios.fortyfive.testing.GameControllerTest
import com.microwavestudios.fortyfive.utils.Timeline

class SourceBulletRefTest : GameControllerTest() {

    override val name: String = "SourceBulletRefTest"

    override fun testTimeline(): Timeline = Timeline.timeline {
        waitForTurnBegin()
        test("poison arrow") {
            test("give poison") {
                dragCardOnSlot("poisonBullet", 5)
                shootAtTargetedEnemy()
                assert(aEnemy(0).aFindStatusEffect<Poison>().aPoisonValue() equal 6)
            }
            test("shoot") {
                dragCardOnSlot("poisonArrow", 5)
                assert(aCardInSlot(5).aName() equal "poisonArrow")
                shootAtTargetedEnemy()
                assert(aCardInSlot(1).aName() equal "poisonArrow")
            }
            test("destroy") {
                holster()
                waitForTurnBegin()
                assert(aEnemy(0).aFindStatusEffect<Poison>().aPoisonValue() equal 3)
                holster()
                waitForTurnBegin()
                assert(aEnemy(0).aFindStatusEffect<Poison>().aPoisonValue() equal 1)
                holster()
                waitForTurnBegin()
                assert(aEnemy(0).aFindStatusEffect<Poison>().isNull())
                assert(aCardInSlot(1).isNull())
                assert(aCardInAfterlife(0).aName() equal "poisonArrow")
            }
        }
        test("amplifyingBullet") {
            test("prepare") {
                dragCardOnSlot("bullet", 1)
                dragCardOnSlot("amplifyingBullet", 4)
                assert(aCurReserves() equal 1)
                holster()
                waitForTurnBegin()
            }
            test("buff") {
                dragCardOnSlot("leadersBullet", 5)
                assert(aCardInSlot(1).aCurDamage() equal deferred { cardInSlot(1)!!.baseDamage + 4 + 5 })
                shootAtTargetedEnemy()
                assert(aCardInSlot(2).aCurDamage() equal aCardInSlot(2).aBaseDamage())
                shootAtTargetedEnemy()
                holster()
                waitForTurnBegin()
            }
        }
        test("bulletPointBullet") {
            dragCardOnSlot("bulletPointBullet", 2)
            assert(aCardInSlot(3).aCurDamage() equal aCardInSlot(3).aBaseDamage())
            assert(aCardInSlot(2).aCurDamage() equal aCardInSlot(2).aBaseDamage())
            dragCardOnSlot("leadersBullet", 5)
            assert(aCardInSlot(5).aCurDamage() equal deferred { cardInSlot(5)!!.baseDamage + 4 })
        }
    }

    override fun seed(): Long = 123456789

    override fun deck(): List<CardType> = listOf(
        CardType(null, "amplifyingBullet", null),
        CardType(null, "poisonArrow", null),
        CardType(null, "poisonBullet", null),
        CardType(null, "bulletPointBullet", null),
        CardType(null, "leadersBullet", null),
        CardType(null, "bullet", null),
    )

    override fun scriptedEnemies(): List<Pair<Int, List<Pair<Int, EnemyAction?>>>> = listOf(
        40 to listOf(
        ),
        40 to listOf(
        ),
    )

}