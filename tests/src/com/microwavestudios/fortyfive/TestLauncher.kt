package com.microwavestudios.fortyfive

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl.LwjglFiles
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.controller.EncounterContext
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.run.Encounter
import com.microwavestudios.fortyfive.testing.mockcomponents.MockAfterlife
import com.microwavestudios.fortyfive.testing.mockcomponents.MockCardHand
import com.microwavestudios.fortyfive.testing.mockcomponents.MockCardPresentation
import com.microwavestudios.fortyfive.testing.mockcomponents.MockRevolver
import com.microwavestudios.fortyfive.testing.mockcomponents.MockScreen
import com.microwavestudios.fortyfive.testing.mockservices.MockSoundPlayer
import com.microwavestudios.fortyfive.testing.mockcomponents.MockWarningParent
import com.microwavestudios.fortyfive.testing.mockservices.MockProfile
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager


class TestLauncher {

    fun run() {
        Gdx.files = LwjglFiles()

        val screen = MockScreen()
        val warningParent = MockWarningParent()
        val afterlife = MockAfterlife()
        val revolver = MockRevolver()
        val cardHand = MockCardHand(screen.events)

        val context = object : EncounterContext {

            override val encounter: Encounter = Encounter(
                enemiesGroups = listOf("Outlaw", "Outlaw"),
                encounterModifierNames = setOf(),
                forceCards = listOf(
                    CardType(null, "bewitchedBullet", null),
                    CardType(null, "bullet", null),
                    CardType(null, "bullet", null),
                    CardType(null, "workerBullet", null),
                    CardType(null, "silverBullet", null),
                ),
                forceConcreteEnemies = null,
                shuffleCards = false,
                unadjustedMajorDifficulty = 1,
                majorDifficulty = 1,
                minorDifficulty = 1f,
                difficultyScalingInfo = 0f,
                special = false
            )

            override val isExtraction: Boolean = false
            override fun completed() {}
        }

        val profile = MockProfile(
            name = "MockProfile",
            data = Profile.ProfileData(
                cardCollection = mutableListOf(),
                collectionDecks = mutableListOf(),
                completedSpecialRuns = mutableListOf(),
                currentDeckId = 100,
                playerMoney = 0,
                currentMap = "",
                currentNode = 0,
                lastNode = null,
                wonRuns = 0,
                runBoards = mutableMapOf()
            ),
            healthInRun = 100,
            maxHealthInRun = 100,
            currentRunDeck = null,
            talismans = listOf()
        )
        val profileManager = MockProfileManager()
        profileManager.currentProfile = profile

        FortyFive.initTest(MockSoundPlayer(), profileManager)

        val controller = GameControllerImpl(
            screen,
            screen.events,
            warningParent,
            afterlife,
            MockCardPresentation.mockProvider
        )
        controller.injectManual("revolver", revolver)
        controller.injectManual("cardHand", cardHand)
        controller.init(context)
        screen.addScreenController(controller)
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            TestLauncher().run()
        }
    }

}