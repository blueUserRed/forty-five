package com.fourinachamber.fortyfive.map.events.chooseCard

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
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
import kotlin.random.Random

//BIOME
// road generation beeinflussen
// decorations mehr

// backgrounds
// event-backgrounds

// evtl. straßen different

// encounter modifier wahrscheinlicher

// cards wahrscheinlicher

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
        val types = context.types.toMutableList()
        types.add(context.biome)
        init(onjScreen, context.seed, types)
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
//        addListener(screen) //phillip said for now not this feature bec he is indecisive
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
        val data = arrayOf(
            7 to -2,
            0 to 0,
            -7 to -2
        )
        for (i in cardPrototypes.indices) {
            val curData = data[i]
            val curCard = cardPrototypes[i].create()

            val curActor = screen.screenBuilder.generateFromTemplate(
                "cardTemplate",
                mapOf(
                    "rotation" to OnjFloat(curData.first.toDouble()),
                    "bottom" to curData.second.toFloat().toOnjYoga(YogaUnit.PERCENT),
                    "textureName" to OnjString(Card.cardTexturePrefix + "bullet")
                ),
                parent,
                screen
            )!! as CustomImageActor
            curActor.name = curCard.name
            curActor.programmedDrawable = TextureRegionDrawable(curCard.actor.pixmapTextureRegion)
        }
    }

    private fun addListener(screen: OnjScreen) {
        (screen.namedActorOrError(leaveButtonName) as CustomLabel).addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                context?.complete()
            }
        })
    }

    private fun updateDropTargets() {
        if (!SaveState.curDeck.canAddCards()) addToDeckWidget.enterActorState("disabled")
//        else addToDeckWidget.leaveActorState("disabled")

        if (!SaveState.curDeck.canRemoveCards()) addToBackpackWidget.enterActorState("disabled")
//        else  addToBackpackWidget.leaveActorState("disabled")
    }

    fun getCard(card: String, addToDeck: Boolean) {
        FortyFiveLogger.debug(logTag, "Chose card: $card")
        SaveState.buyCard(card)
        if (addToDeck) SaveState.curDeck.addToDeck(SaveState.curDeck.nextFreeSlot(), card)
        context?.complete()
        SaveState.write()
        MapManager.changeToMapScreen()
    }

    companion object {
        var logTag: String = "ChooseCardScreenController"
    }
}