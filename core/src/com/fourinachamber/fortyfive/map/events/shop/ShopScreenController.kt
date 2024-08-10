package com.fourinachamber.fortyfive.map.events.shop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.ShopMapEvent
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.AdvancedTextParser
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.toIntRange
import com.fourinachamber.fortyfive.utils.toOnjYoga
import dev.lyze.flexbox.FlexBox
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*
import kotlin.random.Random

class ShopScreenController(private val screen: OnjScreen, onj: OnjObject) : ScreenController() {

    private lateinit var context: ShopMapEvent

    private val shopFilePath = onj.get<String>("shopsFile")
    private val npcsFilePath = onj.get<String>("npcsFile")

    private val messageWidgetName = onj.get<String>("messageWidgetName")
    private val cardsParentName = onj.get<String>("cardsParentName")
    private val addToDeckWidgetName = onj.get<String>("addToDeckWidgetName")
    private val addToBackpackWidgetName = onj.get<String>("addToBackpackWidgetName")
    private val cardHoverDetailTemplateName = onj.get<String>("hoverDetailActorTemplateName")

    private lateinit var person: CustomImageActor

    private lateinit var cardsParentWidget: CustomScrollableFlexBox

    private lateinit var addToDeckWidget: CustomImageActor
    private lateinit var addToBackpackWidget: CustomImageActor

    private val cardWidgets: MutableList<CardActor> = mutableListOf()
    private val labels: MutableList<CustomLabel> = mutableListOf()

    private lateinit var random: Random

    override fun init(context: Any?) {
        //TODO fix bug with cards when being on the edge of other cards when drag and dropping
        addToDeckWidget = screen.namedActorOrError(addToDeckWidgetName) as CustomImageActor
        addToBackpackWidget = screen.namedActorOrError(addToBackpackWidgetName) as CustomImageActor
        if (context !is ShopMapEvent) throw RuntimeException("context for shopScreenController must be a shopMapEvent")
        this.context = context
        val shopFile = ConfigFileManager.getConfigFile("shopConfig")
        val npcsFile = ConfigFileManager.getConfigFile("npcConfig")
        val personData = shopFile
            .get<OnjArray>("people")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == context.person }
            ?: throw RuntimeException("unknown shop: ${context.person}")
        val imgData = (npcsFile
            .get<OnjArray>("npcs")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == personData.get<String>("npcImageName") }
            ?: throw RuntimeException("unknown shop: ${context.person}")).get<OnjObject>("image")
        initWidgets(screen, imgData)

        TemplateString.updateGlobalParam("map.cur_event.personDisplayName", personData.get<String>("displayName"))
        val messageWidget = screen.namedActorOrError(messageWidgetName) as AdvancedTextWidget
        val text = personData.get<OnjArray>("texts").value

        random = Random(context.seed)
        addCards(context.types)

        val textToShow = text[(random.nextDouble() * text.size).toInt()] as OnjObject

        messageWidget.setRawText(
            textToShow.get<String>("rawText"),
            textToShow.get<OnjArray?>("effects")?.value?.map {
                AdvancedTextParser.AdvancedTextEffect.getFromOnj(
                    screen,
                    it as OnjNamedObject
                )
            } ?: listOf()
        )
    }

    @EventHandler
    fun rerollShop(event: ButtonClickEvent, actor: CustomLabel) {
        val rerollPrice = context.currentRerollPrice
        if (SaveState.playerMoney < rerollPrice) return
        SaveState.payMoney(rerollPrice)
        context.amountOfRerolls++
        context.boughtIndices.clear()
        context.selectedCards.clear()
        context.seed = Random(context.seed).nextLong()
        screen.removeAllStyleManagersOfChildren(cardsParentWidget)
        cardsParentWidget.clear()
        cardWidgets.clear()
        labels.forEach { (it.parent as FlexBox).remove(it.styleManager!!.node) }
        labels.clear()
        addCards(context.types)
    }

    private fun addCards(contextTypes: Set<String>) {
        if (context.selectedCards.isEmpty()) {
            val amount = context.amountCards.random(random)
            val cards = RandomCardSelection.getRandomCards(
                screen,
                contextTypes.toList(),
                amount,
                random,
                MapManager.currentDetailMap.biome,
                "shop",
                unique = true
            )
            context.selectedCards.addAll(cards.map { it.name })
        }
        val allPrototypes = RandomCardSelection.allCardPrototypes
        val availableCards = RandomCardSelection.availableCards(allPrototypes).toMutableList()
        val cardsToAdd = context
            .selectedCards
            .map { name -> allPrototypes.find { it.name == name }!! }
        cardsToAdd.forEach { cardProto ->
            val card = cardProto.create(screen)
            screen.addDisposable(card)
            addCard(card)
            if (cardProto !in availableCards) updateStateOfCard(card, setSoldOut = true)
            availableCards.remove(cardProto)
        }
        context.boughtIndices.forEach {
            val cardActor = cardWidgets[it]
            val label = labels[it]
            updateStateOfCard(cardActor.card, setBought = true, label = label)
        }
        TemplateString.updateGlobalParam("shop.currentRerollPrice", context.currentRerollPrice)
    }

    private fun addCard(card: Card) {
        val curParent = screen.screenBuilder.generateFromTemplate(
            "cardsWidgetParent",
            mapOf(),
            cardsParentWidget,
            screen
        ) as FlexBox

        val tempMap: MutableMap<String, OnjValue> = mutableMapOf()
        tempMap["name"] = OnjString("Card_${curParent.children.size}")
        screen.screenBuilder.addDataToWidgetFromTemplate(
            "cardsWidgetImage",
            tempMap,
            curParent,
            screen,
            card.actor
        )
        val tempMap2: MutableMap<String, OnjValue> = mutableMapOf()
        tempMap2["name"] = OnjString("CardLabel" + cardsParentWidget.children.size)
        tempMap2["text"] = OnjString("" + card.price + "$")
        val label = screen.screenBuilder.generateFromTemplate(
            "cardsWidgetPrice",
            tempMap2,
            curParent,
            screen
        ) as CustomLabel
        label.setText("${card.price}$")
        cardWidgets.add(card.actor)
        labels.add(label)
    }

    private fun updateStatesOfUnboughtCards() {
        cardWidgets.forEachIndexed { index, cardActor ->
            if (cardActor.inActorState("unbuyable")) return@forEachIndexed
            updateStateOfCard(cardActor.card, label = labels[index])
        }
    }

    private fun updateStateOfCard(
        card: Card,
        setBought: Boolean = false,
        setSoldOut: Boolean = false,
        label: CustomLabel = labels[cardWidgets.indexOf(card.actor)]
    ) {
        label.leaveActorState("bought")
        label.leaveActorState("poor")
        card.actor.leaveActorState("unbuyable")
        if (!setBought && !setSoldOut && card.price > SaveState.playerMoney) {
            label.enterActorState("poor")
            card.actor.enterActorState("unbuyable")
            return
        }
        if (setBought) {
            label.enterActorState("bought")
            label.setText("bought")
            card.actor.enterActorState("unbuyable")
        }
        if (setSoldOut) {
            label.enterActorState("sold out")
            label.setText("sold out")
            card.actor.enterActorState("unbuyable")
        }
    }

    private fun initWidgets(onjScreen: OnjScreen, imgData: OnjObject) {
        val data = imgData.value.toMutableMap()
        data["offsetX"] = ((data["offsetX"] as OnjFloat?)?.value?.toFloat() ?: 0F).toOnjYoga(YogaUnit.POINT)
        val flexParent =
            highestFlexParent(onjScreen.namedActorOrError(messageWidgetName))!!.children[0] as CustomFlexBox
        val person = onjScreen.screenBuilder.generateFromTemplate(
            "personWidget",
            data,
            flexParent,
            onjScreen
        ) as CustomImageActor
        this.person = person
        flexParent.resortZIndices()
        val cardsParentWidget = onjScreen.namedActorOrError(cardsParentName)
        if (cardsParentWidget !is CustomScrollableFlexBox) throw RuntimeException("widget with name $cardsParentName must be of type CustomScrollableFlexBox")
        this.cardsParentWidget = cardsParentWidget
    }

    private fun highestFlexParent(actor: Actor): CustomFlexBox? {
        var curActor = actor
        val ladder = mutableListOf<Actor>()
        while (curActor.parent != null) {
            curActor = curActor.parent
            ladder.add(curActor)
        }
        return ladder.last { it is CustomFlexBox } as CustomFlexBox?
    }

    fun buyCard(actor: Actor, addToDeck: Boolean) {
        actor as CardActor
        if (actor.inActorState("unbuyable")) return
        if (actor.card.price > SaveState.playerMoney) return
        SaveState.payMoney(actor.card.price)
        SaveState.buyCard(actor.card.name)
        context.boughtIndices.add(cardWidgets.indexOf(actor))
        if (addToDeck) SaveState.curDeck.addToDeck(SaveState.curDeck.nextFreeSlot(), actor.card.name)
        updateStateOfCard(actor.card, setBought = true)
        updateStatesOfUnboughtCards()
    }

    fun displayBuyPopups() {
        val curDeck = SaveState.curDeck
        if (curDeck.canAddCards()) addToDeckWidget.enterActorState("display")
        if (curDeck.hasEnoughCards()) addToBackpackWidget.enterActorState("display")
    }

    fun closeBuyPopups() {
        addToDeckWidget.leaveActorState("display")
        addToBackpackWidget.leaveActorState("display")
    }

}
