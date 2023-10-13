package com.fourinachamber.fortyfive.map.events.shop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.map.detailMap.ShopMapEvent
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.toOnjYoga
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*
import kotlin.random.Random

class ShopScreenController(onj: OnjObject) : ScreenController() {

    private lateinit var screen: OnjScreen
    private lateinit var context: ShopMapEvent

    private val shopFilePath = onj.get<String>("shopsFile")
    private val npcsFilePath = onj.get<String>("npcsFile")
    private val cardsFilePath = onj.get<String>("cardsFile")

    private val messageWidgetName = onj.get<String>("messageWidgetName")
    private val cardsParentName = onj.get<String>("cardsParentName")
    private val addToDeckWidgetName = onj.get<String>("addToDeckWidgetName")
    private val addToBackpackWidgetName = onj.get<String>("addToBackpackWidgetName")

    private lateinit var person: CustomImageActor

    private lateinit var cardsParentWidget: CustomScrollableFlexBox

    private lateinit var shopCardsHandler: ShopCardsHandler

    private lateinit var addToDeckWidget: CustomImageActor
    private lateinit var addToBackpackWidget: CustomImageActor

    override fun init(onjScreen: OnjScreen, context: Any?) { //TODO change image for "to_deck.png"
        screen = onjScreen
        addToDeckWidget = screen.namedActorOrError(addToDeckWidgetName) as CustomImageActor
        addToBackpackWidget = screen.namedActorOrError(addToBackpackWidgetName) as CustomImageActor
        if (context !is ShopMapEvent) throw RuntimeException("context for shopScreenController must be a shopMapEvent")
        this.context = context
        val shopFile = OnjParser.parseFile(Gdx.files.internal(shopFilePath).file())
        shopsSchema.assertMatches(shopFile)
        shopFile as OnjObject
        val npcsFile = OnjParser.parseFile(Gdx.files.internal(npcsFilePath).file())
        npcsSchema.assertMatches(npcsFile)
        npcsFile as OnjObject

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
        initWidgets(onjScreen, imgData)

        TemplateString.updateGlobalParam("map.curEvent.personDisplayName", personData.get<String>("displayName"))
        val messageWidget = onjScreen.namedActorOrError(messageWidgetName) as AdvancedTextWidget
        val text = personData.get<OnjArray>("texts").value
        val defaults = shopFile.get<OnjObject>("defaults")

        val rnd = Random(context.seed)
        shopCardsHandler = ShopCardsHandler(cardsFilePath, screen, cardsParentWidget, context.boughtIndices)
        shopCardsHandler.addItems(rnd, context.type, personData.get<String>("defaultShopParameter"))
//        messageWidget.advancedText = AdvancedText.readFromOnj(text[(rnd.nextDouble() * text.size).toInt()] as OnjArray, onjScreen, defaults)
    }

    private fun initWidgets(onjScreen: OnjScreen, imgData: OnjObject) {
        val data = imgData.value.toMutableMap()
        data["offsetX"] = ((data["offsetX"] as OnjFloat?)?.value?.toFloat() ?: 0F).toOnjYoga(YogaUnit.POINT)
        screen.borrowResource(imgData.get<String>("textureName"))
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
        shopCardsHandler.buyCard(actor as CustomImageActor, addToDeck)
    }

    fun displayBuyPopups() {
        val curDeck = SaveState.curDeck
        if (curDeck.canAddCards()) addToDeckWidget.enterActorState("display")
        if (curDeck.canRemoveCards()) addToBackpackWidget.enterActorState("display")
    }

    fun closeBuyPopups() {
        addToDeckWidget.leaveActorState("display")
        addToBackpackWidget.leaveActorState("display")
    }

    companion object {

        private const val schemaPathShop: String = "onjschemas/shops.onjschema"
        private const val schemaPathNpcs: String = "onjschemas/npcs.onjschema"

        val shopsSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal(schemaPathShop).file())
        }
        val npcsSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal(schemaPathNpcs).file())
        }
    }
}