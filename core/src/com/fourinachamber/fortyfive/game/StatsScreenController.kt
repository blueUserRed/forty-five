package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.TemplateString

class StatsScreenController : ScreenController() {

    override fun init(onjScreen: OnjScreen, context: Any?) {
        val cards = SaveState.cards
        val lostCards = cards.toMutableList()
        val collection = PermaSaveState.collection
        lostCards.removeAll(collection)
        val prototypes = RandomCardSelection.allCardPrototypes
        val cardsContainer = onjScreen.namedActorOrError("cards_container") as? CustomFlexBox
            ?: throw RuntimeException("actor named cards_container must be a CustomFlexBox")
        lostCards.forEach { cardName ->
            val card = prototypes.find { it.name == cardName }?.create(onjScreen)
                ?: throw RuntimeException("unknown card: $cardName")
            cardsContainer.add(card.actor)
            onjScreen.addDisposable(card)
        }
        TemplateString.updateGlobalParam("stat.bulletsCollected", cards.distinct().size)
        val obtainableBullets = prototypes
            .filter { "unobtainable" !in it.tags }
            .filter { "not used" !in it.tags }
            .size
        TemplateString.updateGlobalParam("stat.obtainableBullets", obtainableBullets)
    }
}
