package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.screen.gameWidgets.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.gameWidgets.CardHand
import com.fourinachamber.fortyfive.screen.gameWidgets.Revolver
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator

class GameScreen : ScreenCreator() {

    override val name: String = "gameScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = true

    override val background: String? = null

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 0
    )

    private val cardDragAndDrop = DragAndDrop()

    private val revolver by lazy {
        Revolver(
            "revolver_drum",
            "revolver_slot_texture",
            60f,
            screen
        ).apply {
            slotScale = 0.22f
            cardScale = 0.9f
            animationDuration = 0.2f
            radius = 140f
            rotationOff = (Math.PI / 2f) + (2f * Math.PI) / 5f
            cardZIndex = 100
            initDragAndDrop(cardDragAndDrop)
        }
    }

    private val cardHand by lazy {
        CardHand(
            worldWidth * 0.6f,
            596f * 0.22f,
            0.5f,
            300f,
            screen
        ).apply {
            hoveredCardScale = 1.3f
            maxCardSpacing = 100f
            startCardZIndicesAt = 100
            hoveredCardZIndex = 101
            draggedCardZIndex = 100
        }
    }

    override fun getRoot(): Group = newGroup {

        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        image {
            backgroundHandle = "game_screen_player"
            x = 20f
            y = 0f
            height = worldHeight
            width = (1305f / 1512f) * worldHeight
        }

        playerBar()

    }

    private fun CustomGroup.playerBar() = group {

        x = 0f
        y = 0f
        width = worldWidth
        height = worldWidth * (505f / 1920f)
        backgroundHandle = "player_bar"

        actor(revolver) {
            name("revolver")
            centerX()
            y = -30f
            syncDimensions()
        }

        actor(cardHand) {
            name("cardHand")
            centerX()
            y = 0f
            centerX()
            syncDimensions()
        }

    }


    override fun getScreenControllers(): List<ScreenController> = listOf(BiomeBackgroundScreenController(screen, false))

    override fun getInputMaps(): List<KeyInputMap> = listOf(KeyInputMap.createFromKotlin(listOf(), screen))

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf()
}