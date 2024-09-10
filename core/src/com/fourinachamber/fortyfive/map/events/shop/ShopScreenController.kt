package com.fourinachamber.fortyfive.map.events.shop

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.ShopMapEvent
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.CustomScrollableBox
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.utils.AdvancedTextParser
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.TemplateString
import dev.lyze.flexbox.FlexBox
import ktx.actors.alpha
import onj.value.*
import kotlin.random.Random

class ShopScreenController(
    private val screen: OnjScreen,
    private val messageWidgetName: String,
    private val cardsParentName: String,
    private val addToDeckWidgetName: String,
    private val addToBackpackWidgetName: String,
    private val shopPersonWidgetName: String,
    private val rerollWidgetName: String,
) : ScreenController() {

    private lateinit var context: ShopMapEvent

    private lateinit var personWidget: CustomImageActor

    private lateinit var cardsParentWidget: CustomScrollableBox

    private lateinit var addToDeckWidget: CustomImageActor
    private lateinit var addToBackpackWidget: CustomImageActor

    private val cardWidgets: MutableList<CardActor> = mutableListOf()
    private val labels: MutableList<CustomLabel> = mutableListOf()

    private lateinit var random: Random

    override fun init(context: Any?) {
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
//
        val textToShow = text[(random.nextDouble() * text.size).toInt()] as OnjObject

        messageWidget.setRawText(
            textToShow.get<String>("rawText"),
            textToShow.get<OnjArray?>("effects")?.value?.map {
                AdvancedTextParser.AdvancedTextEffect.getFromOnj(
                    it as OnjNamedObject
                )
            } ?: listOf()
        )
    }

    fun rerollShop() {
        val rerollPrice = context.currentRerollPrice
        if (SaveState.playerMoney < rerollPrice) return
        SaveState.payMoney(rerollPrice)
        context.amountOfRerolls++
        context.boughtIndices.clear()
        context.selectedCards.clear()
        context.seed = Random(context.seed).nextLong()
        screen.removeAllStyleManagersOfChildren(cardsParentWidget)
        cardsParentWidget.children.filterIsInstance<CustomBox>().toMutableList().forEach { screen.removeActorFromScreen(it) }
        cardWidgets.clear()
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
        if (context.currentRerollPrice > SaveState.playerMoney) {
            val customLabel = screen.namedActorOrNull(rerollWidgetName) as CustomLabel
            customLabel.isDisabled = true
            customLabel.setFocusableTo(false, customLabel)
            customLabel.backgroundHandle = "common_button_disabled"
        }

        cardsParentWidget.invalidate()
    }

    private fun addCard(card: Card) {

        val curParent = CustomBox(screen)
        cardsParentWidget.addActor(curParent)
        curParent.width = curParent.parent.width * 0.21f
        curParent.height = curParent.parent.height * 0.42f
        curParent.onLayout {
            curParent.width = curParent.parent.width * 0.21f
            curParent.height = curParent.parent.height * 0.42f
        }
        curParent.flexDirection = FlexDirection.COLUMN
        curParent.minVerticalDistBetweenElements = 2f
        curParent.horizontalAlign = CustomAlign.CENTER
        screen.addNamedActor("cardsWidgetParent", curParent)

        curParent.addActor(card.actor)
        screen.addNamedActor("Card_${curParent.children.size}", card.actor)
        curParent.onLayout {
            val fl = card.actor.parent.height * 0.8f
            card.actor.setSize(fl, fl)
        }
        card.actor.targetGroups = listOf("shop_targets")
        card.actor.makeDraggable(card.actor)
        card.actor.group = "shop_cards"
        card.actor.resetCondition = { true }
        card.actor.bindDragging(card.actor, screen)

        val forceGet = ResourceManager.forceGet<BitmapFont>(screen, screen, "red_wing")
        val label =
            CustomLabel(screen, "${card.price}$", Label.LabelStyle(forceGet, Color.DarkBrown), isDistanceField = true)
        curParent.addActor(label)
        label.setFontScale(0.8f)
        label.setAlignment(Align.center)
        screen.addNamedActor("CardLabel" + cardsParentWidget.children.size, label)
        cardWidgets.add(card.actor)
        labels.add(label)
        updateStateOfCard(card, label = label)
    }

    private fun updateStatesOfUnboughtCards() {
        cardWidgets.forEachIndexed { index, cardActor ->
            updateStateOfCard(cardActor.card, label = labels[index])
        }
    }

    private fun updateStateOfCard(
        card: Card,
        setBought: Boolean = false,
        setSoldOut: Boolean = false,
        label: CustomLabel = labels[cardWidgets.indexOf(card.actor)]
    ) {
        fun CardActor.unavailable() {
            this.alpha = 0.5f
            this.isSelectable = false
            this.isDraggable = false
        }
        if (!setBought && !setSoldOut && card.price > SaveState.playerMoney) {
            if (label.alpha != 1f) return
            label.alpha = 0.6f
            card.actor.unavailable()
            return
        }
        if (setBought) {
            label.alpha = 0.9f
            label.setText("bought")
            card.actor.unavailable()
        }
        if (setSoldOut) {
            label.alpha = 0.9f
            label.setText("sold out")
            card.actor.unavailable()
        }
    }

    private fun initWidgets(onjScreen: OnjScreen, imgData: OnjObject) {
        val shopPersonWidget = onjScreen.namedActorOrError(shopPersonWidgetName)
        if (shopPersonWidget !is CustomImageActor) throw RuntimeException("widget with name $shopPersonWidgetName must be of type CustomImageActor")
        this.personWidget = shopPersonWidget

        personWidget.backgroundHandle = imgData.get<String>("textureName")
        val scale = imgData.get<Double>("scale").toFloat()
        personWidget.scaleX = scale
        personWidget.scaleY = scale
        personWidget.drawOffsetX = imgData.getOr<Double>("offsetX", 0.0).toFloat()
        personWidget.drawOffsetY = imgData.getOr<Double>("offsetY", 0.0).toFloat()

        val cardsParentWidget = onjScreen.namedActorOrError(cardsParentName)
        if (cardsParentWidget !is CustomScrollableBox) throw RuntimeException("widget with name $cardsParentName must be of type CustomScrollableBox")
        this.cardsParentWidget = cardsParentWidget
    }

    fun buyCard(actor: Actor, addToDeck: Boolean) {
        println("this works definitly too i hope${this.context}")
        actor as CardActor
        SaveState.payMoney(actor.card.price)
        SaveState.buyCard(actor.card.name)
        context.boughtIndices.add(cardWidgets.indexOf(actor))
        if (addToDeck) SaveState.curDeck.addToDeck(SaveState.curDeck.nextFreeSlot(), actor.card.name)
        updateStateOfCard(actor.card, setBought = true)
        updateStatesOfUnboughtCards()
    }
}
