package com.fourinachamber.fortyfive.map.events.dialog

import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.map.detailMap.DialogMapEvent
import com.fourinachamber.fortyfive.screen.general.AdvancedTextWidget
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.utils.TemplateString
import onj.value.OnjArray
import onj.value.OnjObject

class DialogScreenController(private val screen: OnjScreen, private val dialogWidgetName: String) : ScreenController() {

    private lateinit var context: DialogMapEvent
    private lateinit var dialogWidget: AnimatedAdvancedTextWidget
    private lateinit var npcs: List<DialogNPC>

    override fun init(context: Any?) {
        if (context !is DialogMapEvent) throw RuntimeException("context for DialogScreenController must be a DialogMapEvent")
        this.context = context
        val dialogWidget = screen.namedActorOrError(dialogWidgetName)
        if (dialogWidget !is AnimatedAdvancedTextWidget) {
            throw RuntimeException("widget with name $dialogWidgetName must be of type AnimatedAdvancedTextWidget")
        }
        this.dialogWidget = dialogWidget
        val configFile = ConfigFileManager.getConfigFile("dialogConfig")
        val dialogOnj = configFile
            .get<OnjArray>("dialogs")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == context.dialog }
            ?: throw RuntimeException("unknown dialog: ${context.dialog}")
        val npcNames = mutableSetOf<String>()
        dialogOnj.get<OnjArray>("parts").value.forEach { it ->
            val curLeft = (it as OnjObject).getOr<String?>("npcLeft", null)
            val curRight = it.getOr<String?>("npcRight", null)
            curLeft?.let { element -> npcNames.add(element) }
            curRight?.let { element -> npcNames.add(element) }
        }

        println("relevant npcs" + npcNames)
        npcs = toNPCArray(configFile.get<OnjArray>("npcs"), npcNames)

        val dialog = Dialog.readFromOnj(dialogOnj, screen)
//        dialogWidget.onNextFinish.add { context.completed() }
        dialogWidget.onNextFinish.add{ println("now finished")}
        dialogWidget.advancedText = dialog.parts[0].text
    }

    fun setPerson(name: String?, isLeft: Boolean, isTalking: Boolean) {

    }

    private fun toNPCArray(array: OnjArray, npcNames: Set<String>): List<DialogNPC> {
        return array.value.filterIsInstance<OnjObject>().filter { it.get<String>("name") in npcNames }.map {
            DialogNPC(
                it.get<String>("name"),
                it.get<String>("displayName"),
                it.get<String>("textureName"),
                Vector2(it.getOr<Double>("offsetX", 0.0).toFloat(), it.getOr<Double>("offsetY", 0.0).toFloat()),
                it.getOr<Double>("scale", 1.0).toFloat()
            )
        }
    }
}

data class DialogNPC(
    val name: String,
    val displayName: String,
    val textureName: String,
    val offset: Vector2,
    val scale: Float
);