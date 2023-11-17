package com.fourinachamber.fortyfive.map.detailMap

import com.badlogic.gdx.scenes.scene2d.Event
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.PermaSaveState
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.rendering.PostProcessedRenderPipeline
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.PopupConfirmationEvent
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.Timeline

class MapScreenController : ScreenController() {

    private lateinit var screen: OnjScreen
    private val timeline: Timeline = Timeline()

    private var popupEvent: Event? = null

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        // TODO: do this properly
        val mapWidget = onjScreen.namedActorOrError("map")
        mapWidget as DetailMapWidget
//        val pipeline = PostProcessedRenderPipeline(
//            onjScreen,
//            "bewitched_fog_dynamic_noise_shader",
//            shaderPreparer = { it.shader.setUniformf("u_mapOffset", mapWidget.mapOffset) }
//        )
//        FortyFive.useRenderPipeline(pipeline)
        timeline.startTimeline()
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
        else -> {}
    }

    override fun update() {
        timeline.updateTimeline()
    }

    companion object {

        const val showExtractionPopupScreenState = "show_extraction_popup"

    }
}
