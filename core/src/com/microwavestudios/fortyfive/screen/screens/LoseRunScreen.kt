package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import kotlin.reflect.KClass

class LoseRunScreen : ScreenCreator() {

    override val name: String = "LoseRunScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val background: String = "black_texture"

    override val transitionAwayTimes: Map<String, Int> = mapOf("*" to 0)


    override fun getRoot(): Group = newBox {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        flexDirection = FlexDirection.COLUMN
        verticalAlign = CustomAlign.CENTER
        horizontalAlign = CustomAlign.CENTER

        label("red_wing_bmp", "You lost!", isDistanceField = false, color = Color.Red)
        label("roadgeek", "Press any key to continue", color = Color.FortyWhite)

        val listener = object : InputListener() {

            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                FortyFive.screenManager.screenFinished()
            }

            override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
                FortyFive.screenManager.screenFinished()
                return true
            }
        }

        addListener(listener)
    }

    override fun getScreenControllers(): List<ScreenController> = listOf()

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = LoseRunScreen::class
    }
}