package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.math.Circle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Event
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.gameWidgets.TutorialInfoActor
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Utils
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjObject
import java.lang.Float.max

class MapScreenController(private val screen: OnjScreen) : ScreenController() {

    private var currentlyShowingTutorialText: Boolean = false
    private var tutorialTextParts: MutableList<MapTutorialTextPart> = mutableListOf()

    @Inject
    private lateinit var tutorialInfoActor: TutorialInfoActor

    @Inject(name = "map")
    private lateinit var mapWidget: DetailMapWidget

    override fun init(context: Any?) {
        SoundPlayer.changeMusicTo(SoundPlayer.Theme.MAIN)
        PermaSaveState.visitedNewArea(MapManager.currentDetailMap.name)
        tutorialTextParts = MapManager.currentDetailMap.tutorialText
    }

    override fun onShow() {
        FortyFive.currentRenderPipeline?.addDebugMenuPage(mapWidget.debugMenuPage)
    }

    override fun end() {
        FortyFive.currentRenderPipeline?.removeDebugMenuPage(mapWidget.debugMenuPage)
    }

    override fun onUnhandledEvent(event: Event) = when (event) {
        is TutorialConfirmedEvent -> hideTutorialPopupActor()
        else -> {}
    }

    override fun update() {
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

        const val showTutorialActorScreenState = "showTutorial"

    }
}
