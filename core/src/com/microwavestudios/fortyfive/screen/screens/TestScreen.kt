package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.NewLabel
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import kotlin.reflect.KClass

class TestScreen : ScreenCreator() {

    override val name: String = "test"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val background: String = "black_texture"

    override val transitionAwayTimes: Map<String, Int> = mapOf()

    override fun getRoot(): Group = newGroup {
        box {
            width = worldWidth
            height = worldHeight
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.CENTER

            box {
                width = worldWidth * 0.5f
                height = worldHeight
                flexDirection = FlexDirection.COLUMN
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.CENTER

                biggerLabels()
            }

            box {
                width = worldWidth * 0.5f
                height = worldHeight
                flexDirection = FlexDirection.COLUMN
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.CENTER

                newLabels()
            }

//            smallerLabels()
//            biggerLabels()
        }
    }

    private fun CustomBox.biggerLabels() {

        backgroundHandle = "statusbar_option"

        label("red wing", "Backpack", Color.FortyWhite, fontSize = (32 * 0.7).toInt()) {
            wrap = false
            debug()
            setAlignment(Align.center)
            relativeWidth(100f)
            syncHeight()
        }
    }

    private fun CustomBox.newLabels() {

        label("red wing", "Backpack", Color.FortyWhite, fontSize = 100) {
            wrap = false
            debug()
            relativeWidth(100f)
            syncHeight()
        }
    }

//    private fun CustomBox.smallerLabels() {
//        label("red wing", ".Forty-Five", Color.FortyWhite) {
//            debug()
//            fontSize = 60
//            syncDimensions()
//        }
//        label("red wing", ".Forty-Five", Color.FortyWhite) {
//            fontSize = 50
//            syncDimensions()
//        }
//        label("red wing", ".Forty-Five", Color.FortyWhite) {
//            fontSize = 40
//            syncDimensions()
//        }
//        label("red wing", ".Forty-Five", Color.FortyWhite) {
//            fontSize = 30
//            syncDimensions()
//        }
//        label("red wing", ".Forty-Five", Color.FortyWhite) {
//            fontSize = 20
//            syncDimensions()
//        }
//    }

    override fun getScreenControllers(): List<ScreenController> = listOf()

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = TestScreen::class
    }

}