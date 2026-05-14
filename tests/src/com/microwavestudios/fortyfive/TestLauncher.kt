package com.microwavestudios.fortyfive

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl.LwjglFiles
import com.microwavestudios.fortyfive.testing.GameControllerTest
import com.microwavestudios.fortyfive.testing.mockservices.MockSoundPlayer
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager
import com.microwavestudios.fortyfive.tests.AfterlifeTest
import com.microwavestudios.fortyfive.tests.BasicEncounterTest
import com.microwavestudios.fortyfive.tests.StatusEffectTest
import com.microwavestudios.fortyfive.utils.ANSI


class TestLauncher {

    val gameControllerTests: Array<GameControllerTest> = arrayOf(
//        BasicEncounterTest(),
//        AfterlifeTest(),
        StatusEffectTest()
    )

    fun run() {
        Gdx.files = LwjglFiles()
        val profileManager = MockProfileManager()
        FortyFive.initTest(MockSoundPlayer(), profileManager)

        FortyFive.logger.title("start tests")
        val failedTests = mutableListOf<String>()
        gameControllerTests.forEach { test ->
            FortyFive.logger.title("test: ${test.name}")
            val result = test.run()
            if (result) return@forEach
            failedTests.add(test.name)
        }
        FortyFive.logger.title("test summary")
        if (failedTests.isEmpty()) {
            println("${ANSI.green}All ${gameControllerTests.size} tests passed${ANSI.reset}")
        } else {
            println("${ANSI.red}${failedTests.size}/${gameControllerTests.size} tests failed")
            println("Failed tests:")
            failedTests.forEach { println(it) }
            println(ANSI.reset)
        }
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            TestLauncher().run()
        }
    }

}
