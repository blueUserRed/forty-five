package com.fourinachamber.fortyfive.map.events.chooseCard

import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.ChooseCardMapEvent
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.toOnjYoga
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.parser.OnjParser
import onj.value.OnjArray
import onj.value.OnjFloat
import onj.value.OnjObject
import onj.value.OnjString
import kotlin.math.abs
import kotlin.random.Random

//TODO BIOME
// evtl. straßen different
// encounter modifier (wahrscheinlicher)

class ChooseCardScreenController(onj: OnjObject) : ScreenController() {
    private val cardsFilePath = onj.get<String>("cardsFile")
    private val leaveButtonName = onj.get<String>("leaveButtonName")
    private val cardsParentName = onj.get<String>("cardsParentName")
    private val addToDeckWidgetName = onj.get<String>("addToDeckWidgetName")
    private val addToBackpackWidgetName = onj.get<String>("addToBackpackWidgetName")
    private var context: ChooseCardMapEvent? = null
    private lateinit var addToDeckWidget: CustomImageActor
    private lateinit var addToBackpackWidget: CustomImageActor
    private var screen: OnjScreen? = null
    override fun init(onjScreen: OnjScreen, context: Any?) {
        if (context !is ChooseCardMapEvent) throw RuntimeException("context for ${this.javaClass.simpleName} must be a ChooseCardMapEvent")
        this.context = context
        init(onjScreen, context.seed, context.types.toMutableList(), context.nbrOfCards)
    }

    private fun init(screen: OnjScreen, seed: Long, types: MutableList<String>, nbrOfCards: Int) {
        val rnd = Random(seed)
        val onj = OnjParser.parseFile(cardsFilePath)
        Card.cardsFileSchema.assertMatches(onj)
        onj as OnjObject
        val cardPrototypes = Card.getFrom(onj.get<OnjArray>("cards"), screen) {}
        val cards = RandomCardSelection.getRandomCards(
            cardPrototypes,
            types,
            true,
            nbrOfCards,
            rnd,
            MapManager.currentDetailMap.biome,
            "chooseCard"
        )
        FortyFiveLogger.debug(
            logTag,
            "Generated with seed $seed and the types $types the following cards: ${cards.map { it.name }}"
        )
//        addListener(screen) //philip said for now not this feature bec he is indecisive
        initCards(screen, cards)
        this.screen = screen
        this.addToDeckWidget = screen.namedActorOrError(addToDeckWidgetName) as CustomImageActor
        this.addToBackpackWidget = screen.namedActorOrError(addToBackpackWidgetName) as CustomImageActor
        updateDropTargets()
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
            val curCard = cardPrototypes[i].create()
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

    private fun addListener(screen: OnjScreen) {
        (screen.namedActorOrError(leaveButtonName) as CustomLabel).onButtonClick { context?.completed() }
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
        context?.completed()
        SaveState.write()
        MapManager.changeToMapScreen()
    }

    companion object {
        var logTag: String = "ChooseCardScreenController"
    }
}