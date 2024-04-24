package com.fourinachamber.fortyfive.map.events.chooseCard

import com.badlogic.gdx.scenes.scene2d.Event
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.toOnjYoga
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.value.OnjFloat
import onj.value.OnjObject
import onj.value.OnjString
import kotlin.math.abs
import kotlin.random.Random

//TODO BIOME
// evtl. straßen different
// encounter modifier (wahrscheinlicher)

class ChooseCardScreenController(onj: OnjObject) : ScreenController() {

    private val leaveButtonName = onj.get<String>("leaveButtonName")
    private val cardsParentName = onj.get<String>("cardsParentName")
    private val addToDeckWidgetName = onj.get<String>("addToDeckWidgetName")
    private val addToBackpackWidgetName = onj.get<String>("addToBackpackWidgetName")
    private lateinit var addToDeckWidget: CustomImageActor
    private lateinit var addToBackpackWidget: CustomImageActor
    private lateinit var screen: OnjScreen
    private lateinit var context: ChooseCardScreenContext

    override fun init(onjScreen: OnjScreen, context: Any?) {
        if (context !is ChooseCardScreenContext) {
            throw RuntimeException("context for ${this.javaClass.simpleName} must be a ChooseCardScreenContext")
        }
        this.screen = onjScreen
        this.context = context
        addToDeckWidget = screen.namedActorOrError(addToDeckWidgetName) as CustomImageActor
        addToBackpackWidget = screen.namedActorOrError(addToBackpackWidgetName) as CustomImageActor
        if (!context.enableRerolls) screen.enterState("disable rerolls")
        initCards()
    }

    private fun initCards(newCards: Boolean = false) = with(context) {

        val cards = if (newCards) {
            getRandomCards(seed, types, nbrOfCards)
        } else {
            forceCards?.let { getFixedCards(it) } ?: getRandomCards(seed, types, nbrOfCards)
        }

        FortyFiveLogger.debug(
            logTag,
            "Generated with seed $seed and the types $types the following cards: ${cards.map { it.name }}"
        )
        initCards(screen, cards)
        if (cards.isEmpty()) screen.enterState("no_cards_left")
        if (cards.size > 1) {
            TemplateString.updateGlobalParam("screen.chooseCard.text", "Choose one Bullet")
        } else {
            TemplateString.updateGlobalParam("screen.chooseCard.text", "You get one Bullet")
        }
        TemplateString.updateGlobalParam("screen.chooseCard.currentRerollPrice", currentRerollPrice)
        updateDropTargets()
    }

    @EventHandler
    fun reroll(event: ButtonClickEvent, actor: CustomLabel) {
        val price = context.currentRerollPrice
        if (SaveState.playerMoney < price) return
        SaveState.payMoney(price)
        context.amountOfRerolls++
        val parent = screen.namedActorOrError(cardsParentName) as CustomFlexBox
        screen.removeAllStyleManagersOfChildren(parent)
        parent.clear()
        context.seed = Random(context.seed).nextLong()
        initCards(newCards = true)
    }

    override fun onUnhandledEvent(event: Event) {
        if (event is PopupConfirmationEvent) MapManager.changeToMapScreen()
    }

    private fun getRandomCards(seed: Long, types: List<String>, nbrOfCards: Int): List<CardPrototype> {
        val rnd = Random(seed)
        return RandomCardSelection.getRandomCards(
            screen,
            types,
            nbrOfCards,
            rnd,
            MapManager.currentDetailMap.biome,
            "chooseCard",
            unique = true
        )
    }

    private fun getFixedCards(cards: List<String>): List<CardPrototype> {
        val protos = RandomCardSelection.allCardPrototypes
        return cards
            .map { name -> protos.find { it.name == name } ?: throw RuntimeException("unknown card $name") }
    }

    override fun update() {
        super.update()
        updateDropTargets()
    }

    private fun initCards(screen: OnjScreen, cardPrototypes: List<CardPrototype>) {
        val parent = screen.namedActorOrError(cardsParentName) as CustomFlexBox
        val data: List<Pair<Double, Float>> = getDataForCards(cardPrototypes.size)
        for (i in cardPrototypes.indices) {
            val curData = data[i]
            val curCard = cardPrototypes[i].create(screen)
            screen.addDisposable(curCard)
            screen.screenBuilder.addDataToWidgetFromTemplate(
                "cardTemplate",
                mapOf(
                    "rotation" to OnjFloat(curData.first),
                    "bottom" to curData.second.toOnjYoga(YogaUnit.PERCENT),
                    "textureName" to OnjString(Card.cardTexturePrefix + "bullet")
                ),
                parent,
                screen,
                curCard.actor
            )
            curCard.actor.name = curCard.name
        }
    }

    private fun getDataForCards(size: Int): List<Pair<Double, Float>> {
        val pos = getXPositionsBasedOnSize(size)
        val res = mutableListOf<Pair<Double, Float>>()
        for (i in pos) res.add(-7 * i to -abs(2 * i.toFloat()))
        return res
    }

    private fun getXPositionsBasedOnSize(size: Int): DoubleArray {
        if (size <= 0) return DoubleArray(0)
        val points = DoubleArray(size)
        val mid = size / 2
        val gap = 2.0 / size

        for (i in 0 until mid) {
            points[i] = -1 + i * gap
            points[size - 1 - i] = 1 - i * gap
        }
        if (size % 2 == 1) {
            points[mid] = 0.0
        } else {
            points[mid] = 0.0
            points[mid - 1] = points[mid]
        }
        return points
    }

    private fun updateDropTargets() {
        if (!SaveState.curDeck.canAddCards()) addToDeckWidget.enterActorState("disabled")
        else addToDeckWidget.leaveActorState("disabled")

        if (!SaveState.curDeck.hasEnoughCards()) addToBackpackWidget.enterActorState("disabled")
        else addToBackpackWidget.leaveActorState("disabled")
    }

    fun getCard(card: String, addToDeck: Boolean) {
        FortyFiveLogger.debug(logTag, "Chose card: $card")
        SaveState.buyCard(card)
        if (addToDeck) SaveState.curDeck.addToDeck(SaveState.curDeck.nextFreeSlot(), card)
        context.completed()
        SaveState.write()
        MapManager.changeToMapScreen()
    }

    companion object {
        var logTag: String = "ChooseCardScreenController"
    }
}

interface ChooseCardScreenContext {

    val forwardToScreen: String
    var seed: Long
    val nbrOfCards: Int
    val types: List<String>

    val enableRerolls: Boolean
    var amountOfRerolls: Int
    val rerollPriceIncrease: Int
    val rerollBasePrice: Int

    val currentRerollPrice: Int
        get() = rerollBasePrice + rerollPriceIncrease * amountOfRerolls

    val forceCards: List<String>?
        get() = null

    fun completed()
}
