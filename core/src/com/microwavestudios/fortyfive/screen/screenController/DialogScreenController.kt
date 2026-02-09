package com.microwavestudios.fortyfive.screen.screenController

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.config.Npc
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.map.Completable
import com.microwavestudios.fortyfive.map.events.dialog.Dialog
import com.microwavestudios.fortyfive.map.events.dialog.DialogPart
import com.microwavestudios.fortyfive.map.events.dialog.NextDialogPartSelector
import com.microwavestudios.fortyfive.run.RunType
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreen
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreenContext
import com.microwavestudios.fortyfive.screen.screens.CreditsScreen
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Promise
import onj.value.OnjArray
import onj.value.OnjObject

class DialogScreenController(
    private val screen: CustomScreen,
    private val events: EventPipeline
) : ScreenController() {

    private lateinit var context: DialogScreenContext

    private lateinit var npcs: Map<String, Npc>

    private lateinit var dialog: Dialog

    private var currentDialogPartIndex: Int = 0
    private var lastPart: DialogPart? = null

    private var inChoice: Boolean = false

    override fun preInit(context: Any?) {
        if (context !is DialogScreenContext) {
            throw RuntimeException("context for DialogScreenController must be a DialogMapEvent")
        }
        this.context = context

        val configFile = ConfigFileManager.getConfigFile("dialogConfig")
        val dialogOnj = configFile
            .get<OnjArray>("dialogs")
            .value
            .map { it as OnjObject }
            .find { it.get<String>("name") == context.dialog }
            ?: throw RuntimeException("unknown dialog: ${context.dialog}")

        npcs = ConfigFileManager.npcConfig.npcs
        dialog = Dialog.readFromOnj(dialogOnj, screen)
    }

    override fun onShow() {
        if (dialog.parts.isEmpty()) {
            FortyFive.logger.warn(logTag, "dialog ${dialog.name} is empty")
            FortyFive.screenManager.screenFinished()
            return
        }

        events.watchFor<NextClicked> {
            if (!inChoice) advanceDialog()
        }

        val currentPart = dialog.parts.first()
        lastPart = currentPart

        val leftNpc = npcs[currentPart.leftNpc]
        val rightNpc = npcs[currentPart.rightNpc]
        events.fire(ChangeNpcEvent(leftNpc, true))
        events.fire(ChangeNpcEvent(rightNpc, false))

        events.fire(ChangeToNewDialogPart(currentPart))
    }

    private fun advanceDialog() {
        val lastPart = dialog.parts[currentDialogPartIndex]
        val selector = lastPart.nextDialogPartSelector
        when (selector) {

            is NextDialogPartSelector.Continue -> {
                currentDialogPartIndex++
                updateToNewDialog()
            }

            is NextDialogPartSelector.End -> {
                FortyFive.screenManager.screenFinished()
            }

            is NextDialogPartSelector.ToCreditScreenEnd -> {
                FortyFive.screenManager.appendScreen(CreditsScreen)
                FortyFive.screenManager.screenFinished()
            }

            is NextDialogPartSelector.Fixed -> {
                val part = dialog.partWithLabel(selector.next)
                currentDialogPartIndex = dialog.parts.indexOf(part)
                updateToNewDialog()
            }

            is NextDialogPartSelector.ByPredicate -> {
                val profile = FortyFive.profileManager.currentProfile!!
                val map = profile.currentMapSaver.currentMap
                val condition = selector.predicate.check(map)
                val partName = if (condition) selector.ifTrue else selector.ifFalse
                val part = dialog.partWithLabel(partName)
                currentDialogPartIndex = dialog.parts.indexOf(part)
                updateToNewDialog()
            }

            is NextDialogPartSelector.MatchPredicate -> {
                val profile = FortyFive.profileManager.currentProfile!!
                val map = profile.currentMapSaver.currentMap
                var chosen: String? = null
                selector.options.forEach { (predicate, label) ->
                    if (chosen != null) return@forEach
                    if (predicate.check(map)) chosen = label
                }
                val part = dialog.partWithLabel(chosen ?: selector.default)
                currentDialogPartIndex = dialog.parts.indexOf(part)
                updateToNewDialog()
            }

            is NextDialogPartSelector.StartSpecialRunEnd -> {
                val run = ConfigFileManager.runConfig.loadRun(selector.run)
                require(run.type == RunType.SPECIAL_NOT_IN_BOARD) {
                    "run started via Dialog must be of type SPECIAL_NOT_IN_BOARD"
                }
                val profile = FortyFive.profileManager.currentProfile!!
                profile.startRun(run)
                FortyFive.screenManager.screenFinished()
            }

            is NextDialogPartSelector.AddSpecialRunEnd -> {
                val run = ConfigFileManager.runConfig.loadRun(selector.run)
                require(run.type == RunType.SPECIAL || run.type == RunType.PROGRESS) {
                    "dialogs can only add special or progress runs to the runBoard"
                }
                val profile = FortyFive.profileManager.currentProfile!!
                profile.addRunToRunBoard(profile.currentAreaMapName, run)
                FortyFive.screenManager.screenFinished()
            }

            is NextDialogPartSelector.GiftCardEnd -> {
                val context = object : ChooseCardScreenContext {
                    override var seed: Long = 0
                    override val nbrOfCards: Int = 1
                    override val types: List<String> = listOf()
                    override val enableRerolls: Boolean = false
                    override var amountOfRerolls: Int = 0
                    override val rerollPriceIncrease: Int = 0
                    override val rerollBasePrice: Int = 0

                    override val forceCards: List<CardType> = listOf(selector.card)

                    override fun completed() {}
                }
                FortyFive.screenManager.ensureNextScreen(ChooseCardScreen, context)
                FortyFive.screenManager.screenFinished()
            }

            is NextDialogPartSelector.Choice -> {
                val promise = Promise<String>()
                val event = Choice(selector.choices.keys, promise)
                inChoice = true
                events.fire(event)
                promise.then { chosen ->
                    val label = selector.choices[chosen]!!
                    val part = dialog.partWithLabel(label)
                    currentDialogPartIndex = dialog.parts.indexOf(part)
                    inChoice = false
                    updateToNewDialog()
                }
            }
        }
    }

    private fun updateToNewDialog() {
        if (currentDialogPartIndex >= dialog.parts.size) {
            FortyFive.logger.warn(
                logTag,
                "Dialog ${dialog.name} reached end without encountering \$EndOfDialog"
            )
            FortyFive.screenManager.screenFinished()
            return
        }

        val currentPart = dialog.parts[currentDialogPartIndex]

        if (lastPart?.leftNpc != currentPart.leftNpc) {
            val npc = npcs[currentPart.leftNpc]
            events.fire(ChangeNpcEvent(npc, true))
        }

        if (lastPart?.rightNpc != currentPart.rightNpc) {
            val npc = npcs[currentPart.rightNpc]
            events.fire(ChangeNpcEvent(npc, false))
        }

        lastPart = currentPart
        events.fire(ChangeToNewDialogPart(currentPart))
    }

    override fun end() {
        super.end()
        context.completed()
    }


    companion object {
        const val logTag = "dialogScreenController"
    }

    data class ChangeNpcEvent(val newNpc: Npc?, val isLeft: Boolean)
    data object NextClicked
    data class ChangeToNewDialogPart(val part: DialogPart)
    data class Choice(val choices: Set<String>, val promise: Promise<String>)

}

interface DialogScreenContext : Completable {
    val dialog: String
}
