package com.fourinachamber.fortyfive.map.shop

import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.random
import dev.lyze.flexbox.FlexBox
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*
import kotlin.random.Random

class ShopCardsHandler(dataFile: String, private val screen: OnjScreen, private val parent: CustomScrollableFlexBox, private val boughtIndices: MutableSet<Int>) {
    private val _allCards: MutableList<Card>
    private val cardWidgets: MutableList<CustomImageActor> = mutableListOf()
    private val cards: MutableList<Card> = mutableListOf()
    private val labels: MutableList<CustomLabel> = mutableListOf()
    private val chances: HashMap<String, Float> = hashMapOf()

    init {
        val onj = OnjParser.parseFile(dataFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject
        val cardPrototypes = Card.getFrom(onj.get<OnjArray>("cards"), screen) {}
        _allCards = cardPrototypes.map { it.create() }.toMutableList()
    }

    fun addItems(seed: Long) {
        val rnd = Random(seed)
        val nbrOfItems = 15/*(5..8).random(rnd)*/
        FortyFiveLogger.debug(ShopWidget.logTag, "Created $nbrOfItems items with seed $seed")
        for (i in 0 until nbrOfItems) {
            if (chances.size == 0) break
            val cardId = getCardToAddWithChances(rnd)
            val curCard = _allCards[cardId]
            _allCards.removeAt(cardId)
            chances.remove(curCard.name)
            cards.add(curCard)
        }
        cards.shuffle(rnd)
        cards.forEach { addCard(it) }
        _allCards.clear()
        updateCards()
    }

    private fun addCard(card: Card) {
        val curParent = screen.screenBuilder.generateFromTemplate(
            "cardsWidgetParent",
            mapOf(),
            parent,
            screen
        )!! as FlexBox

        val tempMap: MutableMap<String, OnjValue> = mutableMapOf()
        tempMap["name"] = OnjString("Card_${curParent.children.size}")
        tempMap["textureName"] = OnjString("card%%" + card.name)
        val img = screen.screenBuilder.generateFromTemplate(
            "cardsWidgetImage",
            tempMap,
            curParent,
            screen
        ) as CustomImageActor
        val tempMap2: MutableMap<String, OnjValue> = mutableMapOf()
        tempMap2["name"] = OnjString("CardLabel" + parent.children.size)
        tempMap2["text"] = OnjString("" + card.price + "$")
        val label = screen.screenBuilder.generateFromTemplate(
            "cardsWidgetPrice",
            tempMap2,
            curParent,
            screen
        ) as CustomLabel
        cardWidgets.add(img)
        labels.add(label)
    }

    private fun getCardToAddWithChances(rnd: Random): Int {
        val maxWeight = chances.map { (_, b) -> b }.sum()
        val value = (0.0F..maxWeight).random(rnd)
        var curSum = 0.0
        for (e in chances) {
            curSum += e.value
            if (curSum > value) {
                return _allCards.indexOf(_allCards.find { it.name == e.key })
            }
        }
        return chances.size - 1
    }

    fun calculateChances(
        type: String,
        shopFile: OnjObject,
        person: OnjObject
    ) { //TODO biome, when they are added
        val allTypes = shopFile.get<OnjArray>("types").value.map { it as OnjObject }
        val curTypeChances = if (type !in allTypes.map { it.get<String>("name") }) {
            allTypes.first { it.get<String>("name") == person.get<String>("defaultShopParameter") }
                .get<OnjArray>("cardChanges").value.map { it as OnjObject }
        } else {
            allTypes.first { it.get<String>("name") == type }.get<OnjArray>("cardChanges").value.map { it as OnjObject }
        }
        _allCards.forEach { chances[it.name] = 0F }
        curTypeChances.forEach {
            applyChancesEffect(
                it.get<OnjNamedObject>("select"),
                it.get<OnjNamedObject>("effect")
            )
        }
    }

    private fun applyChancesEffect(selector: OnjNamedObject, effect: OnjNamedObject) {
        val cardsToChange: List<String> = _allCards.filter {
            (if (selector.name == "ByName") it.name == selector.get<String>("name") else it.tags.contains(
                selector.get<String>("name")
            ))
        }.map { it.name }
        when (effect.name) {
            "Blacklist" -> {
                cardsToChange.forEach { chances.remove(it) }
            }

            "ProbabilityAddition" -> {
                chances.map { (a, _) -> a }.filter { it in cardsToChange }.forEach {
                    chances[it] = chances[it]!! + effect.get<Double>("weight").toFloat()
                }
            }

            "PriceMultiplier" -> {
                _allCards.filter { it.name in cardsToChange }.forEach {
                    it.price = (it.price * effect.get<Double>("price")).toInt()
                }
            }
        }
    }

    fun buyCard(
        cardImg: CustomImageActor,
    ) {
        val i = cardWidgets.indexOf(cardImg)
        if (i !in boughtIndices) boughtIndices.add(i)
        val card = cards[i]
        SaveState.playerMoney -= card.price
        updateCards()
        FortyFiveLogger.debug(logTag, "Bought ${card.name} for a price of ${card.price}")
        SaveState.buyCard(card.name)
    }

    private fun updateCards() {
//        for (i in boughtIndices){
//            println(i.javaClass)
//            println(cards)
//        }
        for (i in cardWidgets.indices) {
            if (i in boughtIndices) {
                labels[i].setText("bought")
                cardWidgets[i].styleManager?.enterActorState("unbuyable")
                labels[i].styleManager?.enterActorState("bought")
            } else if (SaveState.playerMoney < cards[i].price) {
                cardWidgets[i].styleManager?.enterActorState("unbuyable")
                labels[i].styleManager?.enterActorState("poor")
            }
        }
    }

    companion object {

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }
        const val logTag: String = "ShopCardsHandler"
    }
}