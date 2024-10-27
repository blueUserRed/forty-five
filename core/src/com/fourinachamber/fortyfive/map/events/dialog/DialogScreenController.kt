package com.fourinachamber.fortyfive.map.events.dialog

import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.DialogMapEvent
import com.fourinachamber.fortyfive.map.events.chooseCard.ChooseCardScreenContext
import com.fourinachamber.fortyfive.screen.general.AdvancedTextWidget
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.onSelect
import com.fourinachamber.fortyfive.screen.general.onSelectChange
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.TemplateString
import ktx.actors.alpha
import onj.value.OnjArray
import onj.value.OnjObject

class DialogScreenController(
    private val screen: OnjScreen,
    private val dialogWidgetName: String,
    private val continueWidgetName: String,
    private val npcLeftImageWidgetName: String,
    private val npcRightImageWidgetName: String,
    private val optionsParentName: String,
    private val addOption: () -> AdvancedTextWidget,
) : ScreenController() {

    private lateinit var context: DialogMapEvent
    private lateinit var dialogWidget: AnimatedAdvancedTextWidget
    private lateinit var npcs: List<DialogNPC>
    private lateinit var dialog: Dialog
    private var dialogIndex: Int = 0
    private var waitingToContinue: Boolean = false
    private var shownNPCs: Array<String?> = arrayOfNulls(2)

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
            val curLeft = (it as OnjObject).getOr<String?>("npcLeftChangeTo", null)
            val curRight = it.getOr<String?>("npcRightChangeTo", null)
            curLeft?.let { element -> npcNames.add(element) }
            curRight?.let { element -> npcNames.add(element) }
        }
        addListener()

        npcs = toNPCArray(configFile.get<OnjArray>("npcs"), npcNames)
        dialog = Dialog.readFromOnj(dialogOnj, screen)
        startPart(0)
    }

    private fun addListener() {
        val box = screen.namedActorOrError(dialogWidgetName)
        box.onSelect {
            screen.changeSelectionFor(box)
            if (!waitingToContinue) return@onSelect
            val selector = dialog.parts[dialogIndex].nextDialogPartSelector
            when (selector) {
                is NextDialogPartSelector.Continue -> startPart(dialogIndex + 1)
                is NextDialogPartSelector.End -> {
                    end()
                    FortyFive.changeToScreen(ConfigFileManager.screenBuilderFor(selector.nextScreen))
                }

                is NextDialogPartSelector.Fixed -> startPart(selector.next)
                is NextDialogPartSelector.GiftCardEnd -> {
                    val context = object : ChooseCardScreenContext {
                        override val forwardToScreen: String = selector.nextScreen
                        override var seed: Long = -1
                        override val nbrOfCards: Int = 1
                        override val types: List<String> = listOf()
                        override val enableRerolls: Boolean = false
                        override var amountOfRerolls: Int = 0
                        override val rerollPriceIncrease: Int = 0
                        override val rerollBasePrice: Int = 0
                        override val forceCards: List<String> = listOf(selector.card)
                        override fun completed() {}
                    }
                    end()
                    MapManager.changeToChooseCardScreen(context)
                }

                is NextDialogPartSelector.ToCreditScreenEnd -> {
                    end()
                    MapManager.changeToCreditsScreen()
                }

                else -> throw IllegalStateException("'waitingToContinue' should never be true for when choosing an option")
            }
        }
    }

    private fun startPart(index: Int) {
        dialogIndex =
            if (index < dialog.parts.size)
                index
            else {
                FortyFiveLogger.warn(
                    LOG_TAG,
                    "Trying to access dialog '${dialog.name}' on part with index '${index}', but there are only '${dialog.parts.size}'"
                )
                dialog.parts.size - 1
            }
        val part = dialog.parts[dialogIndex]
        dialogWidget.onNextFinish.add {
            finishedPart(part.nextDialogPartSelector)
        }
        val actor = screen.namedActorOrError(continueWidgetName)
        actor.isVisible = false
        waitingToContinue = false

        updatePeople(part)
        dialogWidget.advancedText = part.text

        val optionParent = screen.namedActorOrError(optionsParentName) as CustomBox
        while (optionParent.children.size > 0) {
            screen.removeActorFromScreen(optionParent.children[0])
        }
    }

    override fun end() {
        super.end()
        context.completed()
    }

    private fun finishedPart(selector: NextDialogPartSelector) {
        if (selector !is NextDialogPartSelector.Choice) {
            val actor = screen.namedActorOrError(continueWidgetName)
            actor.isVisible = true
            waitingToContinue = true
            return
        }
        selector.choices.forEach { s, i ->
            val o = addOption.invoke()
            o.setRawText(s, null)
            o.onSelect {
                startPart(i)
            }
        }
        screen.curSelectionParent.updateFocusableActors(screen)
    }


    private fun updatePeople(part: DialogPart) {
        part.leftNpcNameChangeTo?.let { setPerson(it, npcLeftImageWidgetName, 0) }
        part.rightNpcNameChangeTo?.let { setPerson(it, npcRightImageWidgetName, 1) }
        checkTalking(part.talkingNpcName, npcLeftImageWidgetName, 0)
        checkTalking(part.talkingNpcName, npcRightImageWidgetName, 1)
    }

    private fun checkTalking(talkingNPC: String, widgetToCheck: String, index: Int) {
        val actor = screen.namedActorOrError(widgetToCheck) as CustomImageActor
        var isTalking = talkingNPC == shownNPCs[index]

        actor.alpha = if (isTalking) 1.0F else 0.7F
    }


    fun setPerson(name: String?, widgetName: String, index: Int) {
        shownNPCs[index] = name
        val templateText = if (index == 0) "map.cur_event.person_left.displayName"
        else "map.cur_event.person_right.displayName"

        if (name == null) {
            TemplateString.updateGlobalParam(templateText, null)
            return
        }
        val actor = screen.namedActorOrError(widgetName) as CustomImageActor
        actor.isVisible = name.isNotBlank()
        if (!actor.isVisible) {
            TemplateString.updateGlobalParam(templateText, null)
            return
        }

        val npc = npcs.find { it.name == name } ?: return
        actor.backgroundHandle = npc.textureName
        actor.drawOffsetX = npc.offset.x
        actor.drawOffsetY = npc.offset.y
        actor.setScale(npc.scale)
        TemplateString.updateGlobalParam(templateText, npc.displayName)
    }

    private fun toNPCArray(array: OnjArray, npcNames: Set<String>): List<DialogNPC> {
        return array
            .value
            .filterIsInstance<OnjObject>()
            .filter { it.get<String>("name") in npcNames }
            .map {
                val img = it.get<OnjObject>("image")
                DialogNPC(
                    it.get<String>("name"),
                    it.get<String>("displayName"),
                    img.get<String>("textureName"),
                    Vector2(img.getOr<Double>("offsetX", 0.0).toFloat(), img.getOr<Double>("offsetY", 0.0).toFloat()),
                    img.getOr<Double>("scale", 1.0).toFloat(),
                    img.getOr<Boolean>("flipOnRightSide", false),

                    )
            }
    }

    companion object {
        const val LOG_TAG = "DialogScreenController"
    }
}

data class DialogNPC(
    val name: String,
    val displayName: String,
    val textureName: String,
    val offset: Vector2,
    val scale: Float,
    val flipOnRightSide: Boolean
);