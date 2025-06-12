package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.screen.ScreenManager
import com.fourinachamber.fortyfive.screen.gameWidgets.IntroScreenController
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import kotlin.reflect.KClass

class IntroScreen : ScreenCreator() {

    override val name: String = "introScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val background: String = "microwave_studios_brown_texture"

    override val transitionAwayTimes: Map<String, Int> = mapOf("*" to 5_000)


    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        image {
            backgroundHandle = "microwave_studios_logo"
            width = 2_030f * 0.5f
            height = 528f * 0.5f
            centerX()
            centerY()
        }
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        IntroScreenController(screen)
    )

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = IntroScreen::class
    }

}
