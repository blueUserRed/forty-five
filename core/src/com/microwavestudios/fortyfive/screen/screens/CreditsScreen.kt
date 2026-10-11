package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.graphics.Color
import com.microwavestudios.fortyfive.utils.Colors
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.AlphaAction
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.CustomLabel
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.NewLabel
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.screen.screenController.TimelineController
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.alpha
import kotlin.reflect.KClass

class CreditsScreen : ScreenCreator() {

    override val name: String = "creditsScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "brighter_black_texture"

    override val doPostProcessing: Boolean = false

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        name to noTransition(),
        "*" to fadeToBlackTransition(2500)
    )

    private val scrollSpeed = 4f

    private lateinit var contentBox: CustomBox
    private lateinit var backToTitleScreen: NewLabel
    private var enterEndsImmediately: Boolean = false
    private var animFinished: Boolean = false

    private val darkWhite = Color.valueOf("CEC2B6")

    private val timelines: TimelineController = TimelineController()

    override fun getScreenControllers(): List<ScreenController> = listOf(timelines)

    private fun getMainTimeline(): Timeline = Timeline.timeline {
        delay(900)
        delayUntil {
            contentBox.drawOffsetY += scrollSpeed
            contentBox.drawOffsetY >= contentBox.prefHeight - worldHeight
        }
        delay(500)

        val alphaAction = AlphaAction()
        alphaAction.alpha = 1f
        alphaAction.duration = 0.5f

        action {
            backToTitleScreen.addAction(alphaAction)
            animFinished = true
            enterEndsImmediately = true
        }
        delayUntil { alphaAction.isComplete }
    }

    private fun enterPressedTimeline(): Timeline = Timeline.timeline {
        val fadeInAction = AlphaAction()
        fadeInAction.alpha = 1f
        fadeInAction.duration = 0.2f

        val fadeOutAction = AlphaAction()
        fadeOutAction.alpha = 0f
        fadeOutAction.duration = 0.2f

        action {
            enterEndsImmediately = true
            backToTitleScreen.addAction(fadeInAction)
        }
        delayUntil { fadeInAction.isComplete }
        delay(1800)
        action { backToTitleScreen.addAction(fadeOutAction) }
        delayUntil { fadeOutAction.isComplete }
        action { if (!animFinished) enterEndsImmediately = false }
    }

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        getContent()

        box {
            x = 0f
            y = 30f
            width = worldWidth
            height = 200f
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.END

            backToTitleScreen = label("roadgeek", "Press enter to end credits", Colors.HemoglobinRed, (28 * 1.2).toInt()) {
                backgroundHandle = "transparent_black_texture"
                setAlignment(Align.center)
                alpha = 0f
            }
        }

        onInput(GameInputs.skipCredits) {
            if (enterEndsImmediately) {
                FortyFive.screenManager.appendScreen(TitleScreen)
                FortyFive.screenManager.screenFinished()
            } else {
                timelines.dispatchTimeline(enterPressedTimeline())
            }
        }

        timelines.appendMainTimeline(getMainTimeline())
    }

    private fun Group.getContent(): Group = box {
        contentBox = this@box
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.CENTER

        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        verticalSpacer(340f)
        logo()
        verticalSpacer(600f)

//        val nameDistance = 400f
//        nameBox("Philip Jankovic", arrayOf("Lead Game Designer", "Lead Visual Artist"))
//        verticalSpacer(nameDistance)
//        nameBox("Marvin Kurka", arrayOf("Lead Programmer"))
//        verticalSpacer(nameDistance)
//        nameBox("Markus Böheim", arrayOf("Visual Artist", "UI Designer"))
//        verticalSpacer(nameDistance)
//        nameBox("Felix Zwickelstorfer", arrayOf("Programmer"))
//        verticalSpacer(nameDistance)
//        nameBox("Nils Hubmann", arrayOf("Sound Designer"))

        label("red wing", "Current Team Members", Colors.HemoglobinRed, 100) {
            syncDimensions()
        }

        verticalSpacer(120f)

        name("Philip Jankovic", "Project Manager, Gamedesigner, UI Designer")
        name("Marvin Kurka", "Lead Programmer")
        name("Felix Zwickelstorfer", "Programmer")
        name("Karanbir Singh", "Producer, Designer")
        name("Jordan Thomas", "Lead Artist")
        name("Markus Böheim", "UI Designer, Visual Artist")
        name("Jessica Tran", "Visual Artist")
        name("Daniel Ward", "Visual Artist, Animator")
        name("Sean Nguyen", "Concept Artist")
        name("Jasiri", "Visual Artist")
        name("Willem Granzin", "Composer")

        val textDistance = 300f

        verticalSpacer(textDistance)

        textWithHeader("Music by", "Willem Granzin & Nils Jandrasits")

        verticalSpacer(textDistance)

        textWithHeader(
            "Past Team Members",
            "Nils Hubmann,\nChristoph Allmer,\nDylan Calderon,\nDavid Angelo",
            subHeader = "People who left the project along the way or worked on\nprevious iterations of .Forty-Five"
        )

        verticalSpacer(textDistance)

        textWithHeader(
            "Special Thanks",
            "Nenad, Simon,\nGitti, Dragan, Danji,\nLena, Emmi, Pippi,\nDavid Q., Anton, Lisa,\nAna, Emma, Philipp," +
                    "\nCyprian, Mario,\nProf. Doppler,\n5BI (HTL3R 2023/24),\nScoutgroup 14 Raro,\nThe osq Team," +
                    "\nThe Warden of Time Team"
        )

        verticalSpacer(textDistance)

        textWithHeader(
            "Thank you",
            "Florian Weiss,\nGerhard Sturm,\nVincent Nussbaumer,\nMitra Bayandor,\nRoman Jerabek",
            subHeader = "to the professors at HTL Rennweg\n who supported the project along the way"
        )

        verticalSpacer(400f)

        developedBy()
        verticalSpacer(80f)
        label("red wing", "Thank you for playing!", darkWhite, 70) {
            syncDimensions()
        }
        verticalSpacer(100f)
    }

    private fun CustomBox.name(name: String, description: String) = box {
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.CENTER
        syncHeight()
        relativeWidth(70f)
        label("red wing", name, Colors.HemoglobinRed, 80) {
            syncDimensions()
        }
        label(
            "red wing",
            description,
            darkWhite,
            40
        ) {
            relativeWidth(100f)
            wrap = true
            setAlignment(Align.center)
            syncHeight()
        }
        verticalSpacer(130f)
    }

    private fun CustomBox.developedBy() = box {
        relativeWidth(100f)
        syncHeight()
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.CENTER
        verticalAlign = CustomAlign.CENTER

        label("red wing", "developed by:", darkWhite, 60) {
            syncDimensions()
        }

        verticalSpacer(30f)

        box {
            flexDirection = FlexDirection.ROW
            horizontalAlign = CustomAlign.SPACE_AROUND
            verticalAlign = CustomAlign.CENTER
            relativeWidth(100f)
            syncHeight()
            image {
                backgroundHandle = "microwave_studios_logo"
                relativeWidth(50f)
                heightByAspectRatio(2030.0 / 528)
            }
            image {
                backgroundHandle = "crested_fox_logo"
                relativeWidth(30f)
                heightByAspectRatio(4500.0 / 3245.0)
            }
        }
    }

    private fun CustomBox.logo() {
        image {
            backgroundHandle = "logo_red"
            width = worldWidth * 0.7f
            onLayoutAndNow { height = width * (371f / 1573f) }
        }
    }

    private fun CustomBox.textWithHeader(header: String, text: String, subHeader: String? = null) = box {
        width = worldWidth * 0.7f
        syncHeight()
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.CENTER
        label("red wing", header, Colors.HemoglobinRed, (128 * 0.45).toInt()) {
            relativeWidth(100f)
            setAlignment(Align.center)
            syncHeight()
        }
        verticalSpacer(30f)
        subHeader?.let { subHeader ->
            label("roadgeek", subHeader, Colors.HemoglobinRed, (28 * 1.3).toInt()) {
                relativeWidth(100f)
                wrap = true
                setAlignment(Align.center)
                syncHeight()
            }
            verticalSpacer(30f)
        }
        label("roadgeek", text, darkWhite, (28 * 1.3).toInt()) {
            relativeWidth(100f)
            wrap = true
            setAlignment(Align.center)
            syncHeight()
        }
    }

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = CreditsScreen::class
    }

}