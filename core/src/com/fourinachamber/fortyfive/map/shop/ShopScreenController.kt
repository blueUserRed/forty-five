package com.fourinachamber.fortyfive.map.shop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.map.detailMap.ShopMapEvent
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.TemplateString
import dev.lyze.flexbox.FlexBox
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import onj.value.OnjString
import onj.value.OnjValue
import kotlin.random.Random

class ShopScreenController(onj: OnjObject) : ScreenController() {

    private lateinit var screen: OnjScreen
    private lateinit var context: ShopMapEvent

    private lateinit var person: OnjObject

    private val shopFilePath = onj.get<String>("shopsFile")
    private val npcsFilePath = onj.get<String>("npcsFile")
    private val cardsFilePath = onj.get<String>("cardsFile")
    private val personWidgetName = onj.get<String>("personWidgetName")
    private val messageWidgetName = onj.get<String>("messageWidgetName")
    private val cardsParentName = onj.get<OnjString>("cardsParentName").value
    private lateinit var personWidget: PersonWidget
    private lateinit var cardsParentWidget: CustomScrollableFlexBox
    private lateinit var shopCardsHandler: ShopCardsHandler

    override fun init(onjScreen: OnjScreen, context: Any?) {
        //Ruchsack-Backpack
        // mehrere Decks
        // 1 karte in mehreren Decks haben
        // actives deck wechseln
        // deck hat minimal und max. anzahl

        // slots für weight cards(alle decks gleich), locked

        //cards sortieren evtl. (dmg, kosten, alphabet), nur BACKPACK

        //card prebuilds evtl.

        //speichern im savestate
        //für größe Phillip
        screen = onjScreen
        if (context !is ShopMapEvent) throw RuntimeException("context for shopScreenController must be a shopMapEvent")
        this.context = context
        val personWidget = initWidgets(onjScreen)
        val shopFile = OnjParser.parseFile(Gdx.files.internal(shopFilePath).file())
        shopsSchema.assertMatches(shopFile)
        shopFile as OnjObject
        val npcsFile = OnjParser.parseFile(Gdx.files.internal(npcsFilePath).file())
        npcsSchema.assertMatches(npcsFile)
        npcsFile as OnjObject

        person = shopFile
            .get<OnjArray>("people")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == context.person }
            ?: throw RuntimeException("unknown shop: ${context.person}")
        val imgData = (npcsFile
            .get<OnjArray>("npcs")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == person.get<String>("npcImageName") }
            ?: throw RuntimeException("unknown shop: ${context.person}")).get<OnjObject>("image")
        personWidget.setDrawable(imgData)
        TemplateString.updateGlobalParam("map.curEvent.personDisplayName", person.get<String>("displayName"))
        val messageWidget = onjScreen.namedActorOrError(messageWidgetName) as AdvancedTextWidget
        val text = person.get<OnjArray>("texts").value
        val defaults = shopFile.get<OnjObject>("defaults")
        messageWidget.advancedText =
            AdvancedText.readFromOnj(text[(Math.random() * text.size).toInt()] as OnjArray, onjScreen, defaults)

        shopCardsHandler = ShopCardsHandler(cardsFilePath, screen, cardsParentWidget, context.boughtIndices)
        shopCardsHandler.calculateChances(context.type, shopFile, person)
        shopCardsHandler.addItems(context.seed)
    }

    private fun initWidgets(onjScreen: OnjScreen): PersonWidget {
        val personWidget = onjScreen.namedActorOrError(personWidgetName)
        if (personWidget !is PersonWidget) throw RuntimeException("widget with name $personWidgetName must be of type personWidget")
        this.personWidget = personWidget
        val cardsParentWidget = onjScreen.namedActorOrError(cardsParentName)
        if (cardsParentWidget !is CustomScrollableFlexBox) throw RuntimeException("widget with name $cardsParentName must be of type CustomScrollableFlexBox")
        this.cardsParentWidget = cardsParentWidget
        return personWidget
    }

    fun buyCard(actor: Actor) {
        shopCardsHandler.buyCard(actor as CustomImageActor)
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