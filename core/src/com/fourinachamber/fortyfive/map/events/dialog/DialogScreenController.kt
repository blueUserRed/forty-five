package com.fourinachamber.fortyfive.map.events.dialog

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.map.detailMap.NPCMapEvent
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.TemplateString
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

class DialogScreenController(onj: OnjObject) : ScreenController() {

    private lateinit var screen: OnjScreen

    private lateinit var context: NPCMapEvent
    private val dialogWidgetName = onj.get<String>("dialogWidgetName")
    private val npcFilePath = onj.get<String>("npcsFile")
    private lateinit var dialogWidget: DialogWidget

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        if (context !is NPCMapEvent) throw RuntimeException("context for DialogScreenController must be a NPCMapEvent")
        this.context = context
        val dialogWidget = screen.namedActorOrError(dialogWidgetName)
        if (dialogWidget !is DialogWidget) {
            throw RuntimeException("widget with name $dialogWidgetName must be of type DialogWidget")
        }
        this.dialogWidget = dialogWidget
        val npcs = OnjParser.parseFile(Gdx.files.internal(npcFilePath).file())
        npcsSchema.assertMatches(npcs)
        npcs as OnjObject
        val npc = npcs
            .get<OnjArray>("npcs")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == context.npc }
            ?: throw RuntimeException("unknown npc: ${context.npc}")
        val dialogOnj = npc.get<OnjObject>("dialog")
        TemplateString.updateGlobalParam("map.cur_event.person.displayName", npc.get<String>("displayName"))
        val dialog = Dialog.readFromOnj(dialogOnj, screen)
        dialogWidget.start(dialog)

       val personLeftImage= screen.generateFromTemplate("personImageLeft", npc.get<OnjObject>("image").value, screen.stage.root.children[0] as CustomFlexBox,screen)
        personLeftImage?.toBack()
    }

    companion object {

        const val schemaPath: String = "onjschemas/npcs.onjschema"

        val npcsSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal(schemaPath).file())
        }

    }

}
