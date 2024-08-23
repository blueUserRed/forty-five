package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.DetailMapWidget
import com.fourinachamber.fortyfive.map.detailMap.MapNode
import com.fourinachamber.fortyfive.screen.general.onHoverEnter
import com.fourinachamber.fortyfive.screen.general.onHoverLeave
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator

class TestScreen : ScreenCreator() {

    override val name: String = "testScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "mapScreen" to 0,
        "*" to 1000
    )

    private val mapWidget by lazy {
        DetailMapWidget(
            screen = screen,
            map = MapManager.currentDetailMap,
            defaultNodeDrawableHandle = "map_node_default",
            edgeTextureHandle = "map_path",
            playerDrawableHandle = "map_player",
            playerWidth = 170f,
            playerHeight = 170f,
            playerHeightOffset = 50f,
            nodeSize = 60f,
            lineWidth = 10f,
            playerMoveTime = 300,
            directionIndicatorHandle = "common_symbol_arrow",
            startButtonName = "",
            encounterModifierParentName = "",
            encounterModifierDisplayTemplateName = "",
            screenSpeed = 25f,
            scrollMargin = 0f,
            disabledDirectionIndicatorAlpha = 0.5f,
            mapScale = 10f
        )
    }


    override fun getRoot(): Group  = group {
        actor(mapWidget) {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            backgroundHandle = when (MapManager.currentDetailMap.biome) {
                "wasteland" -> "map_background_wasteland_tileable"
                "bewitched_forest" -> "map_background_bewitched_forest_tileable"
                "magenta_mountains" -> "map_background_magenta_mountains_tileable"
                else -> null
            }
        }
        actor(getInfoPopup()) {}
    }

    private fun getInfoPopup() = verticalGroup {

        backgroundHandle = "map_detail_background"
        width = worldWidth * 0.23f
        height = worldHeight * 0.9f
        y = (worldHeight / 2 - height / 2)
        val normalX = worldWidth - width + 10f
        val closedX = normalX + 300f
        x = normalX

        fun getAction(to: Float) = MoveToAction().also {
            it.x = to
            it.y = y
            it.duration = 0.2f
            it.interpolation = Interpolation.pow2In
        }

        var open = true

        fun updateOpenClosed(node: MapNode) {
            val shouldBeOpen = node.event?.displayDescription ?: false
            if (open == shouldBeOpen) return
            open = shouldBeOpen
            val action = getAction(if (shouldBeOpen) normalX else closedX)
            addAction(action)
        }

        verticalSpacer(60f)

        val eventName = label("red_wing", "") {
            debug()
            wrap = true
            fontColor = Color.WHITE
            setAlignment(Align.center)
            relativeWidth(90f)
            centerX()
            syncHeight()
        }

        val eventDescription = label("red_wing", "") {
            debug()
            height = 30f
            wrap = true
            setFontScale(0.5f)
            setAlignment(Align.center)
            fontColor = Color.WHITE
            relativeWidth(90f)
            centerX()
            syncHeight()
        }

        fun updateDescription(node: MapNode) {
            val event = node.event ?: return
            if (!event.displayDescription) return
            eventName.setText(event.displayName)
            eventDescription.setText(event.descriptionText)
        }

        updateDescription(mapWidget.playerNode)

        mapWidget.events.watchFor<DetailMapWidget.PlayerChangedNodeEvent> { (node) ->
            updateOpenClosed(node)
            updateDescription(node)
        }

        verticalGrowingSpacer(1f) {
        }

        label("red_wing", "Start") {
            setAlignment(Align.center)
            onLayout { height = prefHeight }
            fontColor = Color.RED
            forcedPrefWidth = 200f * 0.8f
            forcedPrefHeight = 60f * 0.8f
            onHoverEnter { fontColor = Color.WHITE }
            onHoverLeave { fontColor = Color.RED }
            backgrounds(
                normal = "map_detail_encounter_button",
                hover = "map_detail_encounter_button_hover",
            )
        }

        verticalSpacer(40f)
    }

}
