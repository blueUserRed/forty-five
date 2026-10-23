package com.microwavestudios.fortyfive.testing

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.keyInput.KeyCode
import com.microwavestudios.fortyfive.profile.GlobalSave
import com.microwavestudios.fortyfive.profile.MapSaver
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.run.RunGenerator
import com.microwavestudios.fortyfive.run.RunType
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.testing.mockservices.InputInterceptor
import com.microwavestudios.fortyfive.testing.mockservices.MockProfile
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.Timeline.TimelineBuilderDSL
import com.microwavestudios.fortyfive.utils.Vector2

abstract class FullGameTest : ApplicationListener {

    abstract val name: String

    private val testTimeline: Timeline = Timeline().also { it.startTimeline() }

    protected lateinit var artificialInputSender: InputProcessor
        private set

    private var mouseX: Int = 0
    private var mouseY: Int = 0

    var resourcesWereLoadedOnClose: Boolean = false
        private set

    protected open val testTimeout: Int = 8_000

    private var timeoutReferenceTime: Long = 0

    open val mockProfileManager = MockProfileManager({
        listOf(MockProfile(
            "MockProfile",
            Profile.ProfileData(
                cardCollection().toMutableList(),
                mutableListOf(
                    Deck("a", 1, mutableMapOf()),
                    Deck("b", 2, mutableMapOf()),
                    Deck("c", 3, mutableMapOf()),
                    Deck("d", 4, mutableMapOf()),
                    Deck("e", 5, mutableMapOf()),
                ),
                mutableListOf(),
                1,
                playerMoney(),
                "",
                0,
                null,
                0,
                mutableMapOf()
            ),
            MockProfile.MockRunSaveData(
                0,
                null,
                100,
                0,
                cardCollection().toMutableList(),
                mutableListOf(
                    Deck("a", 101, mutableMapOf()),
                    Deck("b", 102, mutableMapOf()),
                    Deck("c", 103, mutableMapOf()),
                    Deck("d", 104, mutableMapOf()),
                    Deck("e", 105, mutableMapOf()),
                ),
                mutableListOf(),
                101,
                0,
                listOf(),
                0.0,
                RunGenerator().generateRun(2, "wasteland", "aqua_balle", RunType.LIMITED)
            ),
            mapSaver()
        ))}
    )

    open fun preInit(inputInterceptor: InputInterceptor) {
        artificialInputSender = inputInterceptor.artificialInputSender
    }

    override fun render() {
        testTimeline.updateTimeline()
        val now = TimeUtils.millis()
        val timeDiff = now - timeoutReferenceTime
        if (timeDiff >= testTimeout) throw TestException("Test timed out")
    }

    abstract fun testTimeline(): Timeline


    override fun create() {
        testTimeline.appendAction(testTimeline().asAction())
        testTimeline.appendAction(Timeline.later {
            Gdx.app.exit()
        }.asAction())
        FortyFive.resourceManager.addResourceLeakListener { resourcesWereLoadedOnClose = true }
        resetReferenceTime()
    }

    override fun resize(width: Int, height: Int) {
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override fun dispose() {
    }

    protected open fun cardCollection(): List<CardType> = listOf()

    protected open fun mapSaver(): MapSaver? = null

    protected open fun playerMoney(): Int = 0

    open fun globalSaveData(): GlobalSave.GlobalSaveData = GlobalSave.GlobalSaveData(
        soundEffectVolume = 0f,
        musicVolume = 0f,
        masterVolume = 0f,
        enableScreenShake = true,
        skipIntroScreen = skipIntroScreen(),
        lastUsedProfile = null,
        useBorderlessWindowFullscreen = false,
        fullscreen = false,
        enableControllerVibration = true,
        pluginConfig = mutableListOf()
    )
    
    protected open fun skipIntroScreen(): Boolean = false

    protected fun resetReferenceTime() {
        timeoutReferenceTime = TimeUtils.millis()
    }

    protected fun TimelineBuilderDSL.assert(test: () -> Boolean, message: String) {
        action {
            resetReferenceTime()
            if (test()) return@action
            throw AssertionTestException(name, message)
        }
    }

    protected fun TimelineBuilderDSL.assert(test: () -> Boolean, message: () -> String) {
        action {
            resetReferenceTime()
            if (test()) return@action
            throw AssertionTestException(name, message())
        }
    }

    protected fun TimelineBuilderDSL.assertOnScreen(name: String) {
        assert(
            { FortyFive.currentScreen?.name == name },
            { "Expected to be on screen $name, actually on screen ${FortyFive.currentScreen?.name}" }
        )
    }

    protected fun TimelineBuilderDSL.waitUntilScreenIsLoaded() {
        delayUntil {
            FortyFive.currentScreen?.screenState?.contains(RenderableScreen.transitionToScreenState)?.not() ?: false
        }
    }

    protected fun TimelineBuilderDSL.waitForNextScreen() {
        var screen: RenderableScreen? = null
        action { screen = FortyFive.currentScreen }
        delayUntil { FortyFive.currentScreen != screen }
    }

    protected fun TimelineBuilderDSL.pressKey(key: KeyCode) {
        action {
            resetReferenceTime()
            artificialInputSender.keyDown(key)
            artificialInputSender.keyUp(key)
        }
    }

    protected fun TimelineBuilderDSL.holdKey(key: KeyCode, block: TimelineBuilderDSL.() -> Unit) {
        action {
            resetReferenceTime()
            artificialInputSender.keyDown(key)
        }
        later { block() }
        action {
            artificialInputSender.keyUp(key)
        }
    }

    protected fun TimelineBuilderDSL.moveMouseToWindowRelative(
        relativeX: Float,
        relativeY: Float,
        drag: Boolean = false
    ) {
        action {
            val x = (Gdx.graphics.width * relativeX).toInt()
            val y = (Gdx.graphics.height * relativeY).toInt()
            setMousePos(x, y, dragged = drag)
        }
    }

    protected fun TimelineBuilderDSL.moveMouseBy(x: Int, y: Int, drag: Boolean = false) {
        action {
            val newX = mouseX + x
            val newY = mouseY + y
            setMousePos(newX, newY, dragged = drag)
        }
    }

    protected fun TimelineBuilderDSL.moveMouseToNamedActor(actorName: String, drag: Boolean = false) {
        later {
            val screen = FortyFive.currentScreen
            requireNotNull(screen) { "screen is null" }
            val actor = screen.namedActorOrNull(actorName)
            requireNotNull(actor) { "no actor with name $actorName" }
            moveMouseToActor(actor, drag)
        }
    }

    protected fun TimelineBuilderDSL.moveMouseToActor(actor: Actor, drag: Boolean = false) {
        action {
            val screen = FortyFive.currentScreen
            requireNotNull(screen) { "screen is null" }
            val pos = Vector2(actor.width / 2, actor.height / 2)
            actor.localToStageCoordinates(pos)
            screen.viewport.project(pos)
            pos.y = Gdx.graphics.height - pos.y
            setMousePos(pos.x.toInt(), pos.y.toInt(), dragged = drag)
        }
    }

    protected fun setMousePos(x: Int, y: Int, dragged: Boolean = false) {
        resetReferenceTime()
        mouseX = x
        mouseY = y
        if (dragged) {
            artificialInputSender.touchDragged(x, y, 0)
        } else {
            artificialInputSender.mouseMoved(x, y)
        }
    }

    protected fun TimelineBuilderDSL.leftClick() {
        action {
            resetReferenceTime()
            artificialInputSender.touchDown(mouseX, mouseY, 0, Input.Buttons.LEFT)
            artificialInputSender.touchUp(mouseX, mouseY, 0, Input.Buttons.LEFT)
        }
    }

    protected fun TimelineBuilderDSL.holdLeftClick(block: TimelineBuilderDSL.() -> Unit) {
        action {
            resetReferenceTime()
            artificialInputSender.touchDown(mouseX, mouseY, 0, Input.Buttons.LEFT)
        }
        later { block() }
        action {
            artificialInputSender.touchUp(mouseX, mouseY, 0, Input.Buttons.LEFT)
        }
    }

}
