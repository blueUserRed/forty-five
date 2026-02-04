package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.EncounterModifier
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.map.DetailMapWidget
import com.microwavestudios.fortyfive.map.EncounterMapEvent
import com.microwavestudios.fortyfive.map.MapNode
import com.microwavestudios.fortyfive.profile.MapSaver
import com.microwavestudios.fortyfive.screen.BakedDropShadow
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.CustomLabel
import com.microwavestudios.fortyfive.screen.commonComponents.TutorialInfoActor
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.NewLabel
import com.microwavestudios.fortyfive.screen.actors.setText
import com.microwavestudios.fortyfive.screen.commonComponents.WarningParent
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import kotlin.reflect.KClass

class MapScreen : ScreenCreator() {

    override val name: String = "mapScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    private val warningEvents: EventPipeline = EventPipeline()

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        name to noTransition(),
        "*" to geometricFadeTransition()
    )

    private val mapSaver: MapSaver by lazy {
        FortyFive.profileManager.currentProfile!!.currentMapSaver
    }

    private val mapWidget by lazy {
        DetailMapWidget(
            screen = screen,
            defaultNodeDrawableHandle = "map_node_default",
            edgeTextureHandle = "map_path",
            playerDrawableHandle = "map_player",
            playerWidth = 170f,
            playerHeight = 170f,
            playerHeightOffset = 50f,
            nodeSize = 60f,
            lineWidth = 10f,
            playerMoveTime = 300,
            directionIndicatorHandle = "common_symbol_arrow_right",
            startButtonName = "",
            screenSpeed = 25f,
            scrollMargin = 0f,
            disabledDirectionIndicatorAlpha = 0.5f,
            mapSaver = mapSaver,
            mapScale = 10f
        )
    }

    private val tutorialInfoActor by lazy {
        TutorialInfoActor(
            "tutorial_info_actor_background",
            2f,
            200f,
            screen
        )
    }

    override fun getScreenControllers(): List<ScreenController> = listOf()

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight
        actor(mapWidget) {
            name("map")
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            backgroundHandle = when (mapSaver.currentMap.biome) {
                "wasteland" -> "map_background_wasteland_tileable"
                "bewitched_forest" -> "map_background_bewitched_forest_tileable"
                "magenta_mountains" -> "map_background_magenta_mountains_tileable"
                else -> null
            }
        }
        getInfoPopup()

        val inRun = FortyFive.profileManager.currentProfile!!.isRunActive
        addDefaultOverlays(
            worldWidth,
            worldHeight,
            warningEvents,
            canHaveRunBoard = true,
            hasBackpack = inRun,
            hasCollection = !inRun,
            warnings = WarningParent(this@MapScreen, screen, warningEvents)
        )
    }

    private fun Group.getInfoPopup() = box {

        backgroundHandle = "map_detail_background"
        width = worldWidth * 0.23f
        height = worldHeight * 0.8f
        y = (worldHeight / 2 - height / 2)
        val normalX = worldWidth - width + 10f
        val closedX = normalX + 300f
        x = normalX
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.CENTER
        verticalAlign = CustomAlign.SPACE_BETWEEN

        dropShadow = BakedDropShadow(
            "map_detail_background",
            screen,
            -10f, 0f,
            1.3f, 1.3f
        )

        val startButtonGroup = "map-popup-start-button"
        val startButtonFilter = InputManager.FocusFilter(listOf(startButtonGroup), screen)

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
            if (shouldBeOpen) {
                startButtonFilter.end()
            } else {
                startButtonFilter.start()
            }
        }

        val eventName: NewLabel
        val eventDescription: NewLabel

        box {
            flexDirection = FlexDirection.COLUMN
            relativeWidth(100f)
            horizontalAlign = CustomAlign.CENTER
            marginTop = 20f
            height = 500f

            eventName = label("red wing", "", fontSize = (32 * 1.3).toInt()) {
                wrap = true
                fontColor = Color.White
                setAlignment(Align.center)
                relativeWidth(90f)
                syncHeight()
            }

            eventDescription = label("red wing", "", fontSize = (32 * 0.7).toInt()) {
                wrap = true
                setAlignment(Align.center)
                fontColor = Color.White
                relativeWidth(90f)
                syncHeight()
            }

            warningLabel()
            encounterModifiers()
        }

        fun updateDescription(node: MapNode) {
            val event = node.event ?: return
            if (!event.displayDescription) return
            eventName.setText(event.displayName)
            if (event.isCompleted) {
                eventDescription.setText(event.completedDescriptionText)
            } else {
                eventDescription.setText(event.descriptionText)
            }
        }

        updateDescription(mapWidget.playerNode)

        mapWidget.events.watchFor<DetailMapWidget.PlayerChangedNodeEvent> { (node) ->
            updateOpenClosed(node)
            updateDescription(node)
        }

        label("red wing", "Start", fontSize = 32) {
            name("StartButton")
            setAlignment(Align.center)
            width = 200f * 0.8f
            height = 60f
            fontColor = Color.Red
            backgroundHandle = "map_detail_encounter_button"
            touchable = Touchable.enabled
            keyboardFocusable = KeyboardFocusable.LEAF
            marginBottom = 27f
            joinGroup(startButtonGroup)
            onInput(GameInputs.interact) {
                if (mapWidget.playerNode.event?.startable == true) {
                    FortyFive.soundPlayer.situation("general_button_click", screen)
                    mapWidget.onStartButtonClicked(this@label)
                    isDisabled = true
                }
            }

            mapWidget.events.watchFor<DetailMapWidget.PlayerChangedNodeEvent> { (node) ->
                isVisible = node.event?.canBeStarted(mapSaver.currentMap) ?: false
            }

            val dropShadow = BakedDropShadow(
                "map_detail_encounter_button_hover",
                screen,
                0f, 0f,
                1.6f, 1.8f
            )
            dropShadow.showDropShadow = false
            this.dropShadow = dropShadow

            observeInputState(
                GameInputs.States.focused,
                {
                    backgroundHandle = "map_detail_encounter_button_hover"
                    fontColor = Color.White
                    dropShadow.showDropShadow = true
                },
                {
                    backgroundHandle = "map_detail_encounter_button"
                    fontColor = Color.Red
                    dropShadow.showDropShadow = false
                },
            )
        }
    }

    private fun Group.warningLabel() = box {
        backgroundHandle = "red_texture"
        width = 320f
        height = 140f

        verticalAlign = CustomAlign.CENTER
        horizontalAlign = CustomAlign.CENTER

        isVisible = false

        val label = label("roadgeek", "", Color.FortyWhite, fontSize = (32 * 0.6).toInt()) {
            setAlignment(Align.center)
            badTexture("map info popup warning label")
            wrap = true
            width = 320f
            height = 140f
        }

        mapWidget.events.watchFor<DetailMapWidget.PlayerChangedNodeEvent> { (node) ->
            val warning = node.event?.warningText
            label.setText(warning)
            isVisible = warning != null
        }
    }

    private fun Group.encounterModifiers() = box {
        backgroundHandle = "map_detail_encounter_modifier_background"
        width = 320f
        height = 320f
        flexDirection = FlexDirection.COLUMN
        verticalAlign = CustomAlign.CENTER
        horizontalAlign = CustomAlign.SPACE_AROUND

        fun encounterModifierDisplay(modifier: EncounterModifier) = box {
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.SPACE_AROUND
            val icon = GraphicsConfig.encounterModifierIcon(modifier)
            val name = GraphicsConfig.encounterModifierDisplayName(modifier)
            val description = GraphicsConfig.encounterModifierDescription(modifier)

            relativeWidth(100f)
            syncHeight()

            val iconImage = image {
                backgroundHandle = icon
                width = 30f
                height = 30f
            }

            box {

                flexDirection = FlexDirection.COLUMN
                width = parent.width - iconImage.width - 40f
                syncHeight()

                label("red wing", name, fontSize = (32 * 0.6).toInt()) {
                    fontColor = Color.Red
                    setAlignment(Align.left)
                    relativeWidth(100f)
                    syncHeight()
                }

                label("red wing", description, fontSize = 16) {
                    fontColor = Color.Black
                    wrap = true
                    setAlignment(Align.left)
                    relativeWidth(100f)
                    syncHeight()
                }

            }
        }

        isVisible = false
        mapWidget.events.watchFor<DetailMapWidget.PlayerChangedNodeEvent> { (node) ->
            clearChildren()
            isVisible = false
            val event = node.event as? EncounterMapEvent ?: return@watchFor
            val encounter = event.encounter
            val modifiers = encounter.encounterModifier
            if (modifiers.isEmpty()) return@watchFor
            isVisible = true
            modifiers.forEach { modifier ->
                encounterModifierDisplay(modifier)
            }
        }
    }

    override fun debugMenuPages(): List<String> = listOf("Map", "Encounter Preview")

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = MapScreen::class
    }
}
