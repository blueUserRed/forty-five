package com.fourinachamber.fortyfive.map.events.dialog

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.map.MapManager
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

class DialogScreenController(private val screen: OnjScreen, onj: OnjObject) : ScreenController() {

    private lateinit var context: NPCMapEvent
    private val dialogWidgetName = onj.get<String>("dialogWidgetName")
    private val npcFilePath = onj.get<String>("npcsFile")
    private lateinit var dialogWidget: DialogWidget

    override fun init(context: Any?) {
        if (context !is NPCMapEvent) throw RuntimeException("context for DialogScreenController must be a NPCMapEvent")
        this.context = context
        val dialogWidget = screen.namedActorOrError(dialogWidgetName)
        if (dialogWidget !is DialogWidget) {
            throw RuntimeException("widget with name $dialogWidgetName must be of type DialogWidget")
        }
        this.dialogWidget = dialogWidget
        val npc = ConfigFileManager
            .getConfigFile("npcConfig")
            .get<OnjArray>("npcs")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == context.npc }
            ?: throw RuntimeException("unknown npc: ${context.npc}")
        val dialogOnj = npc.get<OnjObject>("dialog")
        TemplateString.updateGlobalParam("map.cur_event.person.displayName", npc.get<String>("displayName"))
        val dialog = Dialog.readFromOnj(dialogOnj, screen)
        dialogWidget.onFinish { context.completed() }
        dialogWidget.start(dialog)

       val personLeftImage= screen.screenBuilder.generateFromTemplate(
           "personImageLeft",
           npc.get<OnjObject>("image").value,
           screen.stage.root.children[0] as CustomFlexBox,screen
       )
        personLeftImage?.toBack()
    }

}
