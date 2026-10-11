package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.animation.scaleAbstractProperty
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.screenController.IntroScreenController
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.SoundPlayer
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.PropertyAction
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.screen.screenController.TimelineController
import com.microwavestudios.fortyfive.utils.Colors
import com.microwavestudios.fortyfive.utils.FortyFiveLogger
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.alpha
import kotlin.reflect.KClass

class IntroScreen : ScreenCreator() {

    override val name: String = "introScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val background: String = "brighter_black_texture"

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        "*" to fadeToBlackTransition(500)
    )

    private val timelines: TimelineController = TimelineController()

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        val logoTimeline = logo()
        val studiosTimeline = studios()

        FortyFive.soundPlayer.changeMusicTo(SoundPlayer.Theme.TITLE)
        screen.afterMs(0) {
            FortyFive.currentRenderPipeline!!.changeVignetteForScreen(1f, 2f)
        }

        timelines.appendMainTimeline {
            delay(600)
            include(logoTimeline)
            delay(100)
            include(studiosTimeline)
            delay(1100)
            action {
                FortyFive.screenManager.screenFinished()
            }
        }
    }

    private fun CustomGroup.logo(): Timeline {

        val logo = image {
            backgroundHandle = "logo_red"
            width = worldWidth * 0.6f
            heightByAspectRatio(1573.0 / 371.0)
            centerX()
            centerY()
            originCenter()
        }

        val scaleAction = PropertyAction(
            Float::class,
            logo,
            logo.scaleAbstractProperty(),
            0.0f
        )
        scaleAction.duration = 0.8f
        scaleAction.interpolation = Interpolation.pow3In

        val fadeAction = PropertyAction(
            Float::class,
            logo,
            logo::alpha,
            0.0f
        )
        fadeAction.duration = 0.4f
        fadeAction.interpolation = Interpolation.pow5In

        return Timeline.timeline {
            delay(2000)
            action { logo.addAction(scaleAction) }
            delay(400)
            action { logo.addAction(fadeAction) }
            delayUntil { scaleAction.isComplete && fadeAction.isComplete }
        }
    }

    private fun CustomGroup.studios(): Timeline {
        val studios = box {
            relativeHeight(100f)
            relativeWidth(100f)
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.CENTER
            drawOffsetY = worldHeight

            label("red wing", "Created by:", Colors.FortyWhite, 60) {
                syncDimensions()
                centerX()
                y = worldHeight * 0.7f
            }
            verticalSpacer(20f)
            image {
                backgroundHandle = "microwave_studios_logo"
                relativeWidth(40f)
                heightByAspectRatio(2030.0 / 528)
            }
            verticalSpacer(40f)
            label("red wing", "in cooperation with:", Colors.FortyWhite, 60) {
                syncDimensions()
                centerX()
                y = worldHeight * 0.7f
            }
            verticalSpacer(20f)
            image {
                backgroundHandle = "crested_fox_logo"
                relativeWidth(24f)
                heightByAspectRatio(4500.0 / 3245.0)
            }
        }

        val yPosAction = PropertyAction(
            Float::class,
            studios,
            studios::drawOffsetY,
            0f
        )
        yPosAction.duration = 0.8f
        yPosAction.interpolation = Interpolation.swingOut

        return Timeline.timeline {
            action { studios.addAction(yPosAction) }
            delayUntil { yPosAction.isComplete }
        }
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        timelines,
//        IntroScreenController(screen)
    )

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = IntroScreen::class
    }

}
