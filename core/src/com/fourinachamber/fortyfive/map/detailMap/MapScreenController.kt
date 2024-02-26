package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.math.Circle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Event
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.map.statusbar.Backpack
import com.fourinachamber.fortyfive.screen.gameComponents.TutorialInfoActor
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.Utils
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.lang.Float.max

class MapScreenController(onj: OnjObject) : ScreenController() {

    private lateinit var screen: OnjScreen
    private val timeline: Timeline = Timeline()

    private var tutorialInfoActorName: String = onj.get<String>("tutorialInfoActor")
    private var mapWidgetName: String = onj.get<String>("mapWidgetName")

    private var popupEvent: Event? = null

    private var currentlyShowingTutorialText: Boolean = false
    private var tutorialTextParts: MutableList<MapTutorialTextPart> = mutableListOf()
    private lateinit var tutorialInfoActor: TutorialInfoActor
    private lateinit var mapWidget: DetailMapWidget

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        timeline.startTimeline()
        doCardExtraction()
        tutorialInfoActor = screen.namedActorOrError(tutorialInfoActorName) as? TutorialInfoActor
            ?: throw RuntimeException("actor named $tutorialInfoActorName must be of type TutorialInfoActor")
        mapWidget = screen.namedActorOrError(mapWidgetName) as? DetailMapWidget
            ?: throw RuntimeException("actor named $mapWidgetName must be of type DetailMapWidget")
        tutorialTextParts = MapManager.currentDetailMap.tutorialText
    }

    private fun doCardExtraction() {
        val map = MapManager.currentDetailMap
        if (!map.isArea) return
        if (PermaSaveState.hasVisitedArea(map.name)) return
        PermaSaveState.visitedNewArea(map.name)
        val cardsBeforeExtraction = PermaSaveState.collection.toMutableList()
        SaveState.extract()
        val cardsAfterExtraction = PermaSaveState.collection.toMutableList()
        cardsBeforeExtraction.forEach { cardsAfterExtraction.remove(it) }
        initCards(screen, cardsAfterExtraction)
        timeline.appendAction(Timeline.timeline {
            action {
                screen.enterState(showExtractionPopupScreenState)
//                (screen.namedActorOrError("backpackActor") as Backpack).reloadDeck()
            }
            delayUntil { popupEvent != null }
            action {
                popupEvent = null
                screen.leaveState(showExtractionPopupScreenState)
            }
        }.asAction())
    }

    private fun initCards(onjScreen: OnjScreen, cards: List<String>) {
        val prototypes = RandomCardSelection.allCardPrototypes
        val cardsContainer = onjScreen.namedActorOrError("extraction_cards_parent") as? CustomFlexBox
                ?: throw RuntimeException("actor named extraction_cards_parent must be a CustomFlexBox")
//        Array(40) { "incendiaryBullet" }.forEach { cardName ->
        cards.forEach { cardName ->
            val card = prototypes
                .find { it.name == cardName }
                ?.create(onjScreen, isSaved = true)
                ?: throw RuntimeException("unknown card: $cardName")
            onjScreen.screenBuilder.addDataToWidgetFromTemplate(
                "cardTemplate",
                mapOf(),
                cardsContainer,
                onjScreen,
                card.actor
            )
            onjScreen.addDisposable(card)
        }
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
        (screen.namedActorOrError("tutorial_info_text") as AdvancedTextWidget).setRawText(tutorialTextPart.text, listOf())
        TemplateString.updateGlobalParam("game.tutorial.confirmButtonText", tutorialTextPart.confirmationText)
        tutorialInfoActor.removeFocus()
        tutorialTextPart.focusActorName?.let { tutorialInfoActor.focusActor(it) }
        tutorialTextPart.highlightObject?.let { highlightMapObject(it) }
    }

    private fun highlightMapObject(name: String) = when (name) {

        "player" -> tutorialInfoActor.focusByLambda {
            val playerBounds = mapWidget.screenSpacePlayerBounds()
            val center = Vector2()
            playerBounds.getCenter(center)
            val screenSpacePos = screen.viewport.project(center)
            val (width, height) =
                Utils.worldSpaceToScreenSpaceDimensions(playerBounds.width, playerBounds.height, screen.viewport)
            Circle(screenSpacePos, max(width, height))
        }

        else -> throw RuntimeException("cannot highlight $name; it is unknown")
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
        val triggerOnNodes: List<Int>,
        val highlightObject: String?
    ) {

        fun asOnjObject(): OnjObject = buildOnjObject {
            "text" with text
            "confirmationText" with confirmationText
            focusActorName?.let { "focusActorName" with it }
            highlightObject?.let { "highlightObject" with it }
            "triggerOnNodes" with triggerOnNodes
        }

        companion object {

            fun fromOnj(onj: OnjObject): MapTutorialTextPart = MapTutorialTextPart(
                onj.get<String>("text"),
                onj.get<String>("confirmationText"),
                onj.getOr<String?>("focusActor", null),
                onj.get<OnjArray>("triggerOnNodes").value.map { (it.value as Long).toInt() },
                onj.getOr<String?>("highlightObject", null)
            )
        }
    }

    companion object {

        const val showExtractionPopupScreenState = "show_extraction_popup"
        const val showTutorialActorScreenState = "showTutorial"

    }
}
