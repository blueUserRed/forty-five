package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.game.EncounterModifier
import com.fourinachamber.fortyfive.game.GameDirector
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.TransitionType
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.DetailMapWidget
import com.fourinachamber.fortyfive.map.detailMap.EncounterMapEvent
import com.fourinachamber.fortyfive.map.detailMap.MapNode
import com.fourinachamber.fortyfive.map.detailMap.MapScreenController
import com.fourinachamber.fortyfive.screen.NavbarCreator.getSharedNavBar
import com.fourinachamber.fortyfive.screen.gameComponents.TutorialInfoActor
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import ktx.actors.onClick

class MapScreen : ScreenCreator() {

    override val name: String = "mapScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "mapScreen" to 0,
        "*" to 200 //TODO maybe change back to 1000
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
            directionIndicatorHandle = "common_symbol_arrow_right",
            startButtonName = "",
            encounterModifierParentName = "",
            encounterModifierDisplayTemplateName = "",
            screenSpeed = 25f,
            scrollMargin = 0f,
            disabledDirectionIndicatorAlpha = 0.5f,
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

    override fun getInputMaps(): List<KeyInputMap> = listOf(
        KeyInputMap.createFromKotlin(listOf(),screen)
    )

    override fun getScreenControllers(): List<ScreenController> = listOf(
        MapScreenController(screen)
    )

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf(
        FocusableParent(
            listOf(
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf("Map_startEvent")
                ),
            ),
            startGroup = "Map_startEvent",
        )
    )

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
            backgroundHandle = when (MapManager.currentDetailMap.biome) {
                "wasteland" -> "map_background_wasteland_tileable"
                "bewitched_forest" -> "map_background_bewitched_forest_tileable"
                "magenta_mountains" -> "map_background_magenta_mountains_tileable"
                else -> null
            }
        }
        actor(getSharedNavBar(worldWidth)) {
            onLayoutAndNow { y = worldHeight - height }
            centerX()
        }
        getInfoPopup()
        val tutorial = actor(tutorialInfoActor) {
            name("tutorialInfoActor")
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            isVisible = false
        }
        val tutorialText = label("red_wing", "") {
            name("tutorial_info_text")
            wrap = true
            fontColor = Color.White
            setAlignment(Align.center)
            centerX()
            onLayout { y = worldHeight - prefHeight }
            syncHeight()
            relativeWidth(40f)
            isVisible = false
        }
        screen.listenToScreenState(MapScreenController.showTutorialActorScreenState) { entered ->
            tutorial.isVisible = entered
            tutorialText.isVisible = entered
        }
    }

    private fun Group.getInfoPopup() = verticalGroup {

        backgroundHandle = "map_detail_background"
        width = worldWidth * 0.23f
        height = worldHeight * 0.8f
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
            wrap = true
            fontColor = Color.White
            setAlignment(Align.center)
            relativeWidth(90f)
            centerX()
            syncHeight()
        }

        val eventDescription = label("red_wing", "") {
            height = 30f
            wrap = true
            setFontScale(0.5f)
            setAlignment(Align.center)
            fontColor = Color.White
            relativeWidth(90f)
            centerX()
            syncHeight()
        }

        fun updateDescription(node: MapNode) {
            val event = node.event ?: return
            if (!event.displayDescription) return
            eventName.setText(event.displayName)
            eventDescription.setText(event.descriptionText)
            invalidateChildren() // make sure spacers are invalidated
        }

        updateDescription(mapWidget.playerNode)

        mapWidget.events.watchFor<DetailMapWidget.PlayerChangedNodeEvent> { (node) ->
            updateOpenClosed(node)
            updateDescription(node)
        }

        verticalSpacer(10f)

        actor(encounterModifiers())

        verticalSpacer(10f)

        verticalGrowingSpacer(1f)

        label("red_wing", "Start") {
            name("StartButton")
            setAlignment(Align.center)
            forcedPrefWidth = 200f * 0.8f
            forcedPrefHeight = 60f * 0.8f
            isFocusable = true
            isSelectable = true
            group = "Map_startEvent"
            onSelect { fire(ButtonClickEvent()) }

            syncHeight()
            dropShadow = DropShadow(
                Color.Red,
                maxOpacity = 0.4f
            )
            styles(
                normal = {
                    fontColor = Color.Red
                    backgroundHandle = "map_detail_encounter_button"
                    dropShadow?.color = Color.White
                    dropShadow?.maxOpacity = 0.2f
                },
                focused = {
                    fontColor = Color.White
                    backgroundHandle = "map_detail_encounter_button_hover"
                    dropShadow?.color = Color.Red
                    dropShadow?.maxOpacity = 0.4f
                }
            )
            onButtonClick {
                mapWidget.onStartButtonClicked(this@label)
                isDisabled = true
            }
        }

        verticalSpacer(40f)
    }

    private fun Group.encounterModifiers() = verticalGroup {
        backgroundHandle = "map_detail_encounter_modifier_background"
        forcedPrefWidth = 320f
        forcedPrefHeight = 340f
        syncDimensions()
        centerX()

        fun encounterModifierDisplay(modifier: EncounterModifier) = horizontalGroup {
            val icon = GraphicsConfig.encounterModifierIcon(modifier)
            val name = GraphicsConfig.encounterModifierDisplayName(modifier)
            val description = GraphicsConfig.encounterModifierDescription(modifier)

            relativeWidth(100f)
            align(Align.topLeft)

            horizontalSpacer(40f)

            val iconImage = image {
                backgroundHandle = icon
                forcedPrefWidth = 30f
                forcedPrefHeight = 30f
                syncDimensions()
            }

            horizontalSpacer(10f)

            verticalGroup {

                forcedPrefWidth = parent.width - iconImage.width
                syncDimensions()
                align(Align.left)

                label("red_wing", name) {
                    fontColor = Color.Red
                    setAlignment(Align.left)
                    setFontScale(0.6f)
                    relativeWidth(100f)
                    onLayout { forcedPrefWidth = width }
                    syncHeight()
                }

                label("red_wing", description) {
                    fontColor = Color.Black
                    wrap = true
                    setAlignment(Align.left)
                    setFontScale(0.5f)
                    relativeWidth(100f)
                    onLayout { forcedPrefWidth = width }
                    syncHeight()
                }

            }
        }

        mapWidget.events.watchFor<DetailMapWidget.PlayerChangedNodeEvent> { (node) ->
            clearChildren()
            isVisible = false
            val event = node.event as? EncounterMapEvent ?: return@watchFor
            val encounter = GameDirector.encounters[event.encounterIndex]
            val modifiers = encounter.encounterModifier
            if (modifiers.isEmpty()) return@watchFor
            isVisible = true
            verticalSpacer(20f)
            modifiers.forEach { modifier ->
                verticalSpacer(20f)
                encounterModifierDisplay(modifier)
            }
        }
    }

}
