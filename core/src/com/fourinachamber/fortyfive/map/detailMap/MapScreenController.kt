package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.scenes.scene2d.Event
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.gameComponents.TutorialInfoActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.PopupConfirmationEvent
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.TutorialConfirmedEvent
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class MapScreenController(onj: OnjObject) : ScreenController() {

    private lateinit var screen: OnjScreen
    private val timeline: Timeline = Timeline()

    private var tutorialInfoActorName: String = onj.get<String>("tutorialInfoActor")

    private var popupEvent: Event? = null

    private var currentlyShowingTutorialText: Boolean = false
    private var tutorialTextParts: MutableList<MapTutorialTextPart> = mutableListOf()
    private lateinit var tutorialInfoActor: TutorialInfoActor

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        timeline.startTimeline()
        doCardExtraction()
        tutorialInfoActor = screen.namedActorOrError(tutorialInfoActorName) as? TutorialInfoActor
            ?: throw RuntimeException("actor named $tutorialInfoActorName must be of type TutorialInfoActor")
        tutorialTextParts = MapManager.currentDetailMap.tutorialText
    }

    private fun doCardExtraction() {
        val map = MapManager.currentDetailMap
        if (!map.isArea) return
        if (PermaSaveState.hasVisitedArea(map.name)) return
        PermaSaveState.visitedNewArea(map.name)
        SaveState.extract()
        timeline.appendAction(Timeline.timeline {
            action {
                screen.enterState(showExtractionPopupScreenState)
            }
            delayUntil { popupEvent != null }
            action {
                popupEvent = null
                screen.leaveState(showExtractionPopupScreenState)
            }
        }.asAction())
    }

    override fun onUnhandledEvent(event: Event) = when (event) {
        is PopupConfirmationEvent -> {
            popupEvent = event
        }
        is TutorialConfirmedEvent -> hideTutorialPopupActor()
        else -> {}
    }

    override fun update() {
        timeline.updateTimeline()
        updateTutorialText()
    }

    private fun updateTutorialText() {
        if (currentlyShowingTutorialText) return
        if (tutorialTextParts.isEmpty()) return
        val nextPart = tutorialTextParts.first()
        if (nextPart.triggerOnNodes.isEmpty() || MapManager.currentMapNode.index in nextPart.triggerOnNodes) {
            showTutorialPopupActor(nextPart)
        }
    }

    private fun showTutorialPopupActor(tutorialTextPart: MapTutorialTextPart) {
        currentlyShowingTutorialText = true
        screen.enterState(showTutorialActorScreenState)
        TemplateString.updateGlobalParam("game.tutorial.text", tutorialTextPart.text)
        TemplateString.updateGlobalParam("game.tutorial.confirmButtonText", tutorialTextPart.confirmationText)
        if (tutorialTextPart.focusActorName == null) {
            tutorialInfoActor.focusActor = null
        } else {
            tutorialInfoActor.focusActor(tutorialTextPart.focusActorName)
        }
    }

    private fun hideTutorialPopupActor() {
        currentlyShowingTutorialText = false
        screen.leaveState(showTutorialActorScreenState)
        tutorialTextParts.removeFirst()
        updateTutorialText() // prevents the tutorial popup from flickering for one frame
    }

    data class MapTutorialTextPart(
        val text: String,
        val confirmationText: String,
        val focusActorName: String?,
        val triggerOnNodes: List<Int>
    ) {

        fun asOnjObject(): OnjObject = buildOnjObject {
            "text" with text
            "confirmationText" with confirmationText
            focusActorName?.let { "focusActorName" with it }
            "triggerOnNodes" with triggerOnNodes
        }

        companion object {

            fun fromOnj(onj: OnjObject): MapTutorialTextPart = MapTutorialTextPart(
                onj.get<String>("text"),
                onj.get<String>("confirmationText"),
                onj.getOr<String?>("focusActor", null),
                onj.get<OnjArray>("triggerOnNodes").value.map { (it.value as Long).toInt() }
            )
        }
    }

    companion object {

        const val showExtractionPopupScreenState = "show_extraction_popup"
        const val showTutorialActorScreenState = "showTutorial"

    }
}
