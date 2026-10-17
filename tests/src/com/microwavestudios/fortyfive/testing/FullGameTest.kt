package com.microwavestudios.fortyfive.testing

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.keyInput.KeyCode
import com.microwavestudios.fortyfive.profile.IProfile
import com.microwavestudios.fortyfive.profile.IProfileManager
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.testing.mockservices.InputInterceptor
import com.microwavestudios.fortyfive.testing.mockservices.MockProfile
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.Timeline.TimelineBuilderDSL

abstract class FullGameTest : ApplicationListener {

    abstract val name: String

    private val testTimeline: Timeline = Timeline().also { it.startTimeline() }

    protected lateinit var artificialInputSender: InputProcessor
        private set

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
                0,
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
                null
            ),
            null
        ))}
    )

    open fun preInit(inputInterceptor: InputInterceptor) {
        artificialInputSender = inputInterceptor.artificialInputSender
    }

    override fun render() {
        testTimeline.updateTimeline()
    }

    abstract fun testTimeline(): Timeline


    override fun create() {
        testTimeline.appendAction(testTimeline().asAction())
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


    protected fun TimelineBuilderDSL.waitUntilScreenIsLoaded() {
        delayUntil {
            FortyFive.currentScreen?.screenState?.contains(RenderableScreen.transitionAwayScreenState)?.not() ?: false
        }
    }

    protected fun TimelineBuilderDSL.brieflyPressKey(key: KeyCode) {
        action {
            artificialInputSender.keyDown(key)
            artificialInputSender.keyUp(key)
        }
    }
}
