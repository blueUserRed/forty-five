package com.fourinachamber.fortyfive.map.events.shop

import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import dev.lyze.flexbox.FlexBox
import onj.parser.OnjParser
import onj.value.*
import kotlin.random.Random

class ShopCardsHandler(
    dataFile: String,
    private val screen: OnjScreen,
    private val parent: CustomScrollableFlexBox,
    private val boughtIndices: MutableSet<Int>,
    private val cardHoverDetailTemplateName: String
) {
    private val _allCards: MutableList<Card>
    private val cardWidgets: MutableList<CardActor> = mutableListOf()
    private val cards: MutableList<Card> = mutableListOf()
    private val labels: MutableList<CustomLabel> = mutableListOf()

    init {
        val onj = OnjParser.parseFile(dataFile)
        Card.cardsFileSchema.assertMatches(onj)
        onj as OnjObject
        val cardPrototypes = Card.getFrom(onj.get<OnjArray>("cards"), screen) {}
        _allCards = cardPrototypes.map { it.create() }.toMutableList()
    }

    fun addItems(rnd: Random, contextTypes: Set<String>, defaultType: String) {
        val nbrOfItems = (5..16).random(rnd)
        FortyFiveLogger.debug(logTag, "Creating $nbrOfItems items")
        val cardsToAdd = RandomCardSelection.getRandomCards(
            _allCards,
            contextTypes.toList(),
            true,
            nbrOfItems,
            rnd,
            MapManager.currentDetailMap.biome,
            "shop"
        )
        cards.addAll(cardsToAdd)
        cards.shuffle(rnd)
        cards.forEach { addCard(it) }
        updateCards()
    }

    private fun addCard(card: Card) {
        val curParent = screen.screenBuilder.generateFromTemplate(
            "cardsWidgetParent",
            mapOf(),
            parent,
            screen
        ) as FlexBox

        val tempMap: MutableMap<String, OnjValue> = mutableMapOf()
        tempMap["name"] = OnjString("Card_${curParent.children.size}")
        tempMap["textureName"] = OnjString(Card.cardTexturePrefix + card.name) //TODO remove this everywhere
        screen.screenBuilder.addDataToWidgetFromTemplate(
            "cardsWidgetImage",
            tempMap,
            curParent,
            screen,
            card.actor
        )
        val tempMap2: MutableMap<String, OnjValue> = mutableMapOf()
        tempMap2["name"] = OnjString("CardLabel" + parent.children.size)
        tempMap2["text"] = OnjString("" + card.price + "$")
        val label = screen.screenBuilder.generateFromTemplate(
            "cardsWidgetPrice",
            tempMap2,
            curParent,
            screen
        ) as CustomLabel
        cardWidgets.add(card.actor)
        labels.add(label)
    }

    fun buyCard(
        cardImg: CardActor,
        addToDeck: Boolean,
    ) {
        val i = cardWidgets.indexOf(cardImg)
        if (i !in boughtIndices) boughtIndices.add(i)
        val card = cards[i]
        SaveState.playerMoney -= card.price
        updateCards()
        FortyFiveLogger.debug(logTag, "Bought ${card.name} for a price of ${card.price}")
        SaveState.buyCard(card.name)
        if (addToDeck) SaveState.curDeck.addToDeck(SaveState.curDeck.nextFreeSlot(), card.name)
    }

    private fun updateCards() {
        for (i in cardWidgets.indices) {
            if (i in boughtIndices) {
                labels[i].setText("bought")
                cardWidgets[i].enterActorState("unbuyable")
                labels[i].enterActorState("bought")
            } else if (SaveState.playerMoney < cards[i].price) {
                cardWidgets[i].enterActorState("unbuyable")
                labels[i].enterActorState("poor")
            }
        }
    }

    companion object {

        const val logTag: String = "ShopCardsHandler"
    }
}