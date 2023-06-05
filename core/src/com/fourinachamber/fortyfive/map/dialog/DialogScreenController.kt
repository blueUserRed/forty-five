package com.fourinachamber.fortyfive.map.dialog

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.map.detailMap.NPCMapEvent
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
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
        val dialog = Dialog.readFromOnj(dialogOnj, screen)
        dialogWidget.start(dialog)
    }

    companion object {

        const val schemaPath: String = "onjschemas/npcs.onjschema"

        val npcsSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal(schemaPath).file())
        }

    }

}
