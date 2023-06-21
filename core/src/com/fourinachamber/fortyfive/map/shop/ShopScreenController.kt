package com.fourinachamber.fortyfive.map.shop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.map.detailMap.ShopMapEvent
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.TemplateString
import ktx.actors.onClick
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import onj.value.OnjString

class ShopScreenController(onj: OnjObject) : ScreenController() {

    private lateinit var screen: OnjScreen
    private lateinit var context: ShopMapEvent

    private lateinit var person: OnjObject

    private val shopFilePath = onj.get<String>("shopsFile")
    private val npcsFilePath = onj.get<String>("npcsFile")
    private val personWidgetName = onj.get<String>("personWidgetName")
    private val messageWidgetName = onj.get<String>("messageWidgetName")
    private val shopWidgetNames = onj.get<List<OnjString>>("shopWidgetNames").map { it.value }
    private val backButtonName = "back_button"  //onj.get<List<OnjString>>("shopWidgetNames").map { it.value }

    private lateinit var personWidget: PersonWidget
    private val dragAndDrop = DragAndDrop()

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        if (context !is ShopMapEvent) throw RuntimeException("context for shopScreenController must be a shopMapEvent")
        this.context = context
        val personWidget = onjScreen.namedActorOrError(personWidgetName)
        if (personWidget !is PersonWidget) throw RuntimeException("widget with name $personWidgetName must be of type shopWidget")
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
        val imgData = (npcsFile
            .get<OnjArray>("npcs")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == person.get<String>("npcImageName") }
            ?: throw RuntimeException("unknown shop: ${context.person}")).get<OnjObject>("image")
        personWidget.setDrawable(imgData)
        personWidget.addDropTarget(dragAndDrop)
        TemplateString.updateGlobalParam("map.curEvent.personDisplayName", person.get<String>("displayName"))
        val messageWidget = onjScreen.namedActorOrError(messageWidgetName) as AdvancedTextWidget
        val text = person.get<OnjArray>("texts").value
        val defaults = shopFile.get<OnjObject>("defaults")
        messageWidget.advancedText =
            AdvancedText.readFromOnj(text[(Math.random() * text.size).toInt()] as OnjArray, onjScreen, defaults)
//        addItemWidgets(shopFile, person) //TODO wieder einfügen


        val backButton = onjScreen.namedActorOrError(backButtonName)
        backButton.onClick { shopWidgetNames.forEach{ _ ->
            personWidget.giveResourcesBack()
        }}
    }

    private fun addItemWidgets(shopFile: OnjObject, person: OnjObject) {
        shopWidgetNames.forEach {
            val shopWidget = screen.namedActorOrError(it)
            if (shopWidget !is ShopWidget) throw RuntimeException("widget with name $it must be of type shopWidget")
            shopWidget.calculateChances(context.type, shopFile, person)
            shopWidget.addItems(context.seed, context.boughtIndices, dragAndDrop)
        }
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