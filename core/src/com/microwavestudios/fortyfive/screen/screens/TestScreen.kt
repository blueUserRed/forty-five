package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
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

        label("red wing", "AVWXY hello world", Color.FortyWhite, fontSize = 100) {
            wrap = false
            relativeWidth(100f)
            syncHeight()
        }

//        label("redwing14", "Be at ease little sister, support is on the way, the Onathahans will not go down without a fight. Take care TENYA. May the blessings of the gods find you. Yours, URIKA\\\" -Letter to Salem", Color.White, isDistanceField = false) {
//            wrap = true
//            setFontScale(1f)
//            relativeWidth(100f)
//            syncHeight()
//        }


//        label("redwing200", ".Forty-Five", Color.FortyWhite, isDistanceField = false) {
//            setFontScale(1f * 0.5f)
//            syncDimensions()
//        }
//        label("redwing200", ".Forty-Five", Color.FortyWhite, isDistanceField = false) {
//            setFontScale(0.8f * 0.5f)
//            syncDimensions()
//        }
//        label("redwing200", ".Forty-Five", Color.FortyWhite, isDistanceField = false) {
//            setFontScale(0.6f * 0.5f)
//            syncDimensions()
//        }
//        label("redwing200", ".Forty-Five", Color.FortyWhite, isDistanceField = false) {
//            setFontScale(0.4f * 0.5f)
//            syncDimensions()
//        }
    }

    private fun CustomBox.newLabels() {

        label("red wing", "AVWXY hello world", Color.FortyWhite, fontSize = 100) {
            useShader = false
            wrap = false
            relativeWidth(100f)
            syncHeight()
        }

//        label("redwing200", "AVWXY", Color.White, isDistanceField = false) {
//            wrap = false
//            setFontScale(0.07f)
//            relativeWidth(100f)
//            syncHeight()
//        }

//        label("redwing60", "Be at ease little sister, support is on the way, the Onathahans will not go down without a fight. Take care TENYA. May the blessings of the gods find you. Yours, URIKA\\\" -Letter to Salem", Color.FortyWhite, isDistanceField = false) {
//            wrap = true
//            setFontScale(0.23333333f)
//            relativeWidth(100f)
//            syncHeight()
//        }

//        actor(NewLabel(screen, ".Forty-Five")) {
//            fontGroup = "red wing"
//            fontSize = 100
//            syncDimensions()
//        }
//        actor(NewLabel(screen, ".Forty-Five")) {
//            fontGroup = "red wing"
//            fontSize = (100 * 0.8f).toInt()
//            syncDimensions()
//        }
//        actor(NewLabel(screen, ".Forty-Five")) {
//            fontGroup = "red wing"
//            fontSize = (100 * 0.6f).toInt()
//            syncDimensions()
//        }
//        actor(NewLabel(screen, ".Forty-Five")) {
//            fontGroup = "red wing"
//            fontSize = (100 * 0.4f).toInt()
//            syncDimensions()
//        }
//        actor(NewLabel(screen, ".Forty-Five")) {
//            fontGroup = "red wing"
//            fontSize = (100 * 0.2f).toInt()
//            syncDimensions()
//        }
//        actor(NewLabel(screen, ".Forty-Five")) {
//            fontGroup = "red wing"
//            fontSize = (100 * 0.1f).toInt()
//            syncDimensions()
//        }
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