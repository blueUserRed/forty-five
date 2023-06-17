package com.fourinachamber.fortyfive.map.shop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.map.detailMap.ShopMapEvent
//import com.fourinachamber.fortyfive.map.shop.Shop
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

class ShopScreenController(onj: OnjObject) : ScreenController() {

    private lateinit var screen: OnjScreen
    private lateinit var context: ShopMapEvent
    private val personImageActorName = onj.get<String>("shopPersonImageActor")

    //    private val cardConfigFile = onj.get<String>("cardsFile")
//    private val cardDragAndDropBehaviour = onj.get<OnjNamedObject>("cardDragBehaviour")
//    private val cardDrawActorName = onj.get<String>("cardDrawActor")
    lateinit var closeButton: Actor

    //    private val shopWidgetName = onj.get<String>("shopWidgetName")
    private val shopFilePath = onj.get<String>("shopsFile")


    lateinit var personImageActor: Actor
    private lateinit var shopWidget: ShopWidget

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        if (context !is ShopMapEvent) throw RuntimeException("context for shopScreenController must be a shopMapEvent")
        this.context = context

        println(personImageActorName)
//        println(onj.get<String>(""))
        personImageActor = screen.namedActorOrError(personImageActorName)
        println(personImageActor)
//        if (shopWidget !is ShopWidget) {
//            throw RuntimeException("widget with name $shopWidgetName must be of type shopWidget")
//        }
//        this.shopWidget = shopWidget
        val shopFile = OnjParser.parseFile(Gdx.files.internal(shopFilePath).file())
        shopsSchema.assertMatches(shopFile)
        shopFile as OnjObject
        val person = shopFile
            .get<OnjArray>("people")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == context.person }
            ?: throw RuntimeException("unknown shop: ${context.person}")

        println(person)
//        val shopOnj = shop.get<OnjObject>("shop")
//        val shop2 = shop.readFromOnj(shopOnj, screen)
//        shopWidget.start(shop2)
    }

    companion object {

        const val schemaPath: String = "onjschemas/shops.onjschema"

        val shopsSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal(schemaPath).file())
        }

    }
}