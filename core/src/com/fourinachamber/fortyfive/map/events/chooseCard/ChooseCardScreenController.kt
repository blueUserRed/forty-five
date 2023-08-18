package com.fourinachamber.fortyfive.map.events.chooseCard

import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.map.detailMap.ChooseCardMapEvent
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.map.events.shop.ShopCardsHandler
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import onj.parser.OnjParser
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.random.Random

//BIOME
// road generation beeinflussungen
// decorations mehr
// backgrounds
// event-backgrounds
// evtl. straßen different
// encounter modifier wahrscheinlicher
// cards wahrscheindlicher


//TODO Asking marvin:
// 1. how to make black overlay without images
// 2. how to make this screen also sometimes from a win-screen
// 3. system with biomes (my idea is, to have all data for the current biome stored somewhere publicly, or all biome data is stored separately (fe. background different than encounter modifier)
class ChooseCardScreenController(onj: OnjObject) : ScreenController() {
    private val cardsFilePath = onj.get<String>("cardsFile")
    private val leaveButtonName = onj.get<String>("leaveButtonName")
    private val cardsParentName = onj.get<String>("cardsParentName")
    private val addToDeckWidgetName = onj.get<String>("addToDeckWidgetName")
    private val addToBackpackWidgetName = onj.get<String>("addToBackpackWidgetName")
    private lateinit var context: ChooseCardMapEvent
    override fun init(onjScreen: OnjScreen, context: Any?) {
        if (context !is ChooseCardMapEvent) throw RuntimeException("context for ${this.javaClass.simpleName} must be a ChooseCardMapEvent")
        this.context = context
        val types = context.types.toMutableList()
        types.add(context.biome)
        init(onjScreen, context.seed, types)
        initDropTargets(onjScreen)
    }

    private fun init(screen: OnjScreen, seed: Long, types: MutableList<String>) {
        val rnd = Random(seed)
        val onj = OnjParser.parseFile(cardsFilePath)
        Card.cardsFileSchema.assertMatches(onj)
        onj as OnjObject
        val cardPrototypes = Card.getFrom(onj.get<OnjArray>("cards"), screen) {}
        val cards = RandomCardSelection.getRandomCards(cardPrototypes, types, true, 3, rnd)
        FortyFiveLogger.debug(
            logTag,
            "Generated with seed $seed and the types $types the following cards: ${cards.map { it.name }}"
        )
    }

    private fun initDropTargets(screen: OnjScreen) {
        if (!SaveState.curDeck.canAddCards()) (screen.namedActorOrError(addToDeckWidgetName) as CustomImageActor).enterActorState(
            "disabled"
        )
        if (!SaveState.curDeck.canRemoveCards()) (screen.namedActorOrError(addToBackpackWidgetName) as CustomImageActor).enterActorState(
            "disabled"
        )
    }

    companion object {
        var logTag: String = this.javaClass.simpleName
    }
}