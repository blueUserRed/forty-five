package com.microwavestudios.fortyfive.tests

import com.microwavestudios.fortyfive.game.SelectorFactory
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.widgets.IRevolverSlot
import com.microwavestudios.fortyfive.testing.GameControllerTest
import com.microwavestudios.fortyfive.testing.mockservices.MockSelector
import com.microwavestudios.fortyfive.utils.Timeline

class AfterlifeTest : GameControllerTest() {

    override val name: String = "AfterlifeTest"

    private val slotSelector: MockSelector<IRevolverSlot> = MockSelector()

    override fun prepare() {
        SelectorFactory.revolverSlotSelectorCreator = SelectorFactory.SelectorCreator { _, _, _ -> slotSelector }
    }

    override fun testTimeline(): Timeline = Timeline.timeline {
        waitForTurnBegin()
        test("destroy bullets") {
            dragCardOnSlot("zombieBullet", 4)
            dragCardOnSlot("purgeBullet", 5)
            assert(aCardInSlot(4).isNull())
            assert(aCardInSlot(5).aName() equal "purgeBullet")
            assert(aCardInAfterlife(0).aName() equal "zombieBullet")
        }
        test("resurrect") {
            action { slotSelector.setupNextSelection(revolverSlot(4)) }
            dragCardOnSlot("parrabellumBullet", 1)
            waitForFreeUi()
            assert(aCardInSlot(4).aName() equal "zombieBullet")
            assert(aCardInAfterlife(0).aName() equal "miniBullet")
            assert(aCardInAfterlife(6).aName() equal "miniBullet")
        }
        test("descend") {
            shoot()
            val enemyHealth = enemy(0).health - 11 - (3 * 2)
            waitForFreeUi()
            assert(aEnemy(0).aCurrentHealth() equal enemyHealth)
            assert(aCardInAfterlife(6).isNull())
        }
    }

    override fun seed(): Long = 123456789

    override fun deck(): List<CardType> = listOf(
        CardType(null, "bullet", null),
        CardType(null, "parrabellumBullet", null),
        CardType(null, "zombieBullet", null),
        CardType(null, "purgeBullet", null),
    )

    override fun enemies(): List<String> = listOf("Outlaw-1")

}
