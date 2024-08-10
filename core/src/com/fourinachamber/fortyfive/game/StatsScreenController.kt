package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.TemplateString

class StatsScreenController(private val screen: OnjScreen) : ScreenController() {

    override fun init(context: Any?) {
        val cards = PermaSaveState.statCardsLastRun
        val prototypes = RandomCardSelection.allCardPrototypes
        TemplateString.updateGlobalParam("stat.bulletsCollected", cards.distinct().size)
        val obtainableBullets = prototypes
            .filter { "not in collection" !in it.tags }
            .size
        TemplateString.updateGlobalParam("stat.obtainableBullets", obtainableBullets)
        initCards()
    }

    private fun initCards() {
        val cards = PermaSaveState.statCardsLastRun
        val lostCards = cards.toMutableList()
        val collection = PermaSaveState.collection
        collection.forEach { lostCards.remove(it) }
        val prototypes = RandomCardSelection.allCardPrototypes
        val cardsContainer = screen.namedActorOrError("cards_container") as? CustomFlexBox
                ?: throw RuntimeException("actor named cards_container must be a CustomFlexBox")
        lostCards.forEach { cardName ->
            val card = prototypes
                .find { it.name == cardName }
                ?.create(screen, isSaved = false, areHoverDetailsEnabled = false)
                    ?: throw RuntimeException("unknown card: $cardName")
            screen.screenBuilder.addDataToWidgetFromTemplate(
                "cardTemplate",
                mapOf(),
                cardsContainer,
                screen,
                card.actor
            )
            screen.addDisposable(card)
        }
    }

}
