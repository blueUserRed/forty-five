package com.fourinachamber.fortyfive.map.shop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.fourinachamber.fortyfive.map.detailMap.ShopMapEvent
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
//import com.fourinachamber.fortyfive.map.shop.Shop
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjFloat
import onj.value.OnjObject
import kotlin.random.Random

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


    lateinit var personImageActor: CustomImageActor
    private lateinit var shopWidget: ShopWidget

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        if (context !is ShopMapEvent) throw RuntimeException("context for shopScreenController must be a shopMapEvent")
        this.context = context

        personImageActor = screen.namedActorOrError(personImageActorName) as CustomImageActor
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

//        println(person)
//        val imgData=person.get<OnjObject>("image").value
//        personImageActor.setPosition(imgData["positionLeft"]?.value as Float, imgData["positionTop"]?.value as Float)
//        personImageActor.setSize(imgData["width"]?.value as Float, imgData["height"]?.value as Float)
//        personImageActor.backgroundHandle
        val imgData = person.get<OnjObject>("image")
        println(imgData)
//        personImageActor.setPosition(imgData.get<OnjFloat>("positionLeft").value.toFloat(),imgData.get<OnjFloat>("positionTop").value.toFloat())
        personImageActor.setSize(200.0F,700.0F)
//        personImageActor.setPosition(200F,500F)

        println("" + personImageActor.x + "  " + personImageActor.y)
        println("" + personImageActor.width + "  " + personImageActor.height)
//        val rnd= Random(1)
//        val shopOnj = shop.get<OnjObject>("shop")
//        val shop2 = shop.readFromOnj(shopOnj, screen)
//        shopWidget.start(shop2)
        println(personImageActor.parent.children)

        onjScreen.addEarlyRenderTask(renderTask)

    }

    //    override fun update() {
//        super.update()
//        personImageActor.setSize(200.0F,700.0F)
//        personImageActor.setPosition(200F,500F)
//        personImageActor.isVisible=true
//        personImageActor.zIndex=2
//    }

    private val renderTask: (Batch) -> Unit = { batch ->
        personImageActor.draw(batch, 0F)
    }

    override fun update() {
        super.update()
        println(personImageActor.width)
//        System.exit(0)
        personImageActor.setSize(200F,800F)
        personImageActor.isVisible=true
    }

    companion object {

        const val schemaPath: String = "onjschemas/shops.onjschema"

        val shopsSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal(schemaPath).file())
        }

    }

}