package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController

class StatsScreenController : ScreenController() {

    override fun init(onjScreen: OnjScreen, context: Any?) {
        val lostCards = SaveState.cards.toMutableList()
        val collection = PermaSaveState.collection
        lostCards.removeAll(collection)
        val prototypes = RandomCardSelection.allCardPrototypes
        val cardsContainer = onjScreen.namedActorOrError("cards_container") as? CustomFlexBox
            ?: throw RuntimeException("actor named cards_container must be a CustomFlexBox")
        println("adding cards: $lostCards")
        lostCards.forEach { cardName ->
            val card = prototypes.find { it.name == cardName }?.create(onjScreen)
                ?: throw RuntimeException("unknown card: $cardName")
            cardsContainer.add(card.actor)
        }
    }
}
