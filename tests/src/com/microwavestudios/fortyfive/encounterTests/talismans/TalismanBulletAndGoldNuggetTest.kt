package com.microwavestudios.fortyfive.encounterTests.talismans

import com.microwavestudios.fortyfive.game.Talisman
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.enemy.EnemyAction
import com.microwavestudios.fortyfive.testing.GameControllerTest
import com.microwavestudios.fortyfive.utils.Timeline

class TalismanBulletAndGoldNuggetTest : GameControllerTest() {

    override val name: String = "talismanTest"

    override fun testTimeline(): Timeline = Timeline.timeline {
        waitForTurnBegin()
        test("goldNugget") {
            val config = GameControllerImpl.Config
            assert(aAmountHandCards() equal config.cardsToDrawInFirstRound + 2)
            holster()
            waitForTurnBegin()
            assert(aAmountHandCards() equal config.cardsToDrawInFirstRound + config.cardsToDraw + 2)
        }
        test("talismanBullet") {
            later {
                val cardsInHand = cardHand.amountOfCards
                repeat(cardsInHand) { i ->
                    assert(aCardInHand(i).aCurDamage() equal aCardInHand(i).aBaseDamage() + 2)
                }
            }
            dragCardOnSlot("bewitchedBullet", 5)
            assert(aCardInSlot(5).aCurDamage() equal aCardInSlot(5).aBaseDamage() + 2)
            shootAtTargetedEnemy()
            holster()
            waitForTurnBegin()
            assert(aCardInHand("bewitchedBullet").isNotNull())
            assert(aCardInHand("bewitchedBullet").aCurDamage() equal aCardInHand("bewitchedBullet").aBaseDamage() + 2)
        }
    }

    override fun talismans(): List<Talisman> = listOf(
        Talisman.GoldNugget, Talisman.TalismanBullet
    )

    override fun seed(): Long = 123456789

    override fun deck(): List<CardType> = listOf(
        CardType(null, "bullet", null),
        CardType(null, "workerBullet", null),
        CardType(null, "bewitchedBullet", null),
        CardType(null, "bigBullet", null),
    )

    override fun scriptedEnemies(): List<Pair<Int, List<Pair<Int, EnemyAction?>>>> = listOf(
        40 to listOf(
        ),
        40 to listOf(
        ),
    )
}