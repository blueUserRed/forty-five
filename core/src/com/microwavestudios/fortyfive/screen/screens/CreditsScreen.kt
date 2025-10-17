package com.microwavestudios.fortyfive.screen.screens

import com.microwavestudios.fortyfive.utils.Color
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

    override val background: String = "black_texture"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 0
    )

    private val scrollSpeed = 3f

    private lateinit var contentBox: CustomBox
    private lateinit var backToTitleScreen: NewLabel
    private var enterEndsImmediately: Boolean = false
    private var animFinished: Boolean = false

    private val timelines: TimelineController = TimelineController()

    override fun getScreenControllers(): List<ScreenController> = listOf(timelines)

    private fun getMainTimeline(): Timeline = Timeline.timeline {
        delay(900)
        delayUntil {
            contentBox.drawOffsetY += scrollSpeed
            contentBox.drawOffsetY >= 6180f
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

            backToTitleScreen = label("roadgeek", "Press enter to end credits", Color.Red, (28 * 1.2).toInt()) {
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

        val nameDistance = 400f
        nameBox("Philip Jankovic", arrayOf("Lead Game Designer", "Lead Visual Artist"))
        verticalSpacer(nameDistance)
        nameBox("Marvin Kurka", arrayOf("Lead Programmer"))
        verticalSpacer(nameDistance)
        nameBox("Markus Böheim", arrayOf("Visual Artist", "UI Designer"))
        verticalSpacer(nameDistance)
        nameBox("Felix Zwickelstorfer", arrayOf("Programmer"))
        verticalSpacer(nameDistance)
        nameBox("Nils Hubmann", arrayOf("Sound Designer"))

        verticalSpacer(700f)

        val textDistance = 300f
        textWithHeader("Music", "Nils Jandrasits")
        verticalSpacer(textDistance)
        textWithHeader(
            "Past Team Members",
            "Christoph Allmer,\nDylan Calderon,\nDavid Angelo",
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
        verticalSpacer(500f)
        developedBy()
    }

    private fun CustomBox.logo() {
        image {
            backgroundHandle = "logo_red"
            width = worldWidth * 0.7f
            onLayoutAndNow { height = width * (371f / 1573f) }
        }
    }

    private fun CustomBox.nameBox(name: String, titles: Array<String>) = box {
        width = worldWidth * 0.7f
        flexDirection = FlexDirection.ROW
        verticalAlign = CustomAlign.CENTER
        horizontalAlign = CustomAlign.SPACE_AROUND

        label("red wing", name, Color.Red, (128 * 0.7).toInt()) {
            relativeWidth(45f)
            setAlignment(Align.right)
        }

        box {
            relativeWidth(45f)
            flexDirection = FlexDirection.COLUMN
            titles.forEach { title -> label("roadgeek", title, Color.FortyWhite, (28 * 1.3).toInt()) {
                syncHeight()
            } }
            syncHeight()
        }
    }

    private fun CustomBox.textWithHeader(header: String, text: String, subHeader: String? = null) = box {
        width = worldWidth * 0.7f
        syncHeight()
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.CENTER
        label("red wing", header, Color.Red, (128 * 0.45).toInt()) {
            relativeWidth(100f)
            setAlignment(Align.center)
            syncHeight()
        }
        verticalSpacer(30f)
        subHeader?.let { subHeader ->
            label("roadgeek", subHeader, Color.Red, (28 * 1.3).toInt()) {
                relativeWidth(100f)
                wrap = true
                setAlignment(Align.center)
                syncHeight()
            }
            verticalSpacer(30f)
        }
        label("roadgeek", text, Color.FortyWhite, (28 * 1.3).toInt()) {
            relativeWidth(100f)
            wrap = true
            setAlignment(Align.center)
            syncHeight()
        }
    }

    private fun CustomBox.developedBy() = box {
        width = worldWidth * 0.7f
        syncHeight()
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.CENTER
        label("red wing", "developed by", Color.FortyWhite, (128 * 0.4).toInt()) {
            relativeWidth(100f)
            setAlignment(Align.center)
            syncHeight()
        }
        verticalSpacer(40f)
        image {
            backgroundHandle = "microwave_studios_logo"
            width = worldWidth * 0.6f
            onLayoutAndNow { height = width * (528f / 2030f) }
        }
        verticalSpacer(90f)
        label("red wing", "Thank you for playing!", Color.FortyWhite, (128 * 0.5).toInt()) {
            relativeWidth(100f)
            setAlignment(Align.center)
            syncHeight()
        }
    }

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = CreditsScreen::class
    }

}