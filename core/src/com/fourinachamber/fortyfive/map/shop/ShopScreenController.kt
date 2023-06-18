package com.fourinachamber.fortyfive.map.shop

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.map.detailMap.ShopMapEvent
//import com.fourinachamber.fortyfive.map.shop.Shop
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.TemplateString
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

class ShopScreenController(onj: OnjObject) : ScreenController() {

    private lateinit var screen: OnjScreen
    private lateinit var context: ShopMapEvent

    //    private val personImageActorName = onj.get<String>("shopPersonImageActor")
    private lateinit var person: OnjObject

    //    private val cardConfigFile = onj.get<String>("cardsFile")
//    private val cardDragAndDropBehaviour = onj.get<OnjNamedObject>("cardDragBehaviour")
//    private val cardDrawActorName = onj.get<String>("cardDrawActor")
//    lateinit var closeButton: Actor

    private val shopFilePath = onj.get<String>("shopsFile")
    private val npcsFilePath = onj.get<String>("npcsFile")
    private val personWidgetName = onj.get<String>("personWidgetName")

    //    lateinit var personImageActor: CustomImageActor
    private lateinit var personWidget: PersonWidget

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        if (context !is ShopMapEvent) throw RuntimeException("context for shopScreenController must be a shopMapEvent")
        this.context = context
        val personWidget = onjScreen.namedActorOrError(personWidgetName)
//        personImageActor = screen.namedActorOrError(personImageActorName) as CustomImageActor
        if (personWidget !is PersonWidget) {
            throw RuntimeException("widget with name $personWidgetName must be of type shopWidget")
        }
        this.personWidget = personWidget
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
        println(person)
        val imgData = (npcsFile
            .get<OnjArray>("npcs")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == person.get<String>("npcImageName") }
            ?: throw RuntimeException("unknown shop: ${context.person}")).get<OnjObject>("image")

        personWidget.setDrawable(imgData)
        TemplateString.updateGlobalParam("map.curEvent.personDisplayName", person.get<String>("displayName"))
        TemplateString.updateGlobalParam("map.curEvent.money", "0$")

        TemplateString.updateGlobalParam("map.curEvent.message", "Hello Darling!")
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