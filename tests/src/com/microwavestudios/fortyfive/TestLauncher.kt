package com.microwavestudios.fortyfive

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl.LwjglFiles
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.microwavestudios.fortyfive.testing.ApplicationListenerRelay
import com.microwavestudios.fortyfive.testing.GameControllerTest
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager
import com.microwavestudios.fortyfive.testing.mockservices.MockSoundPlayer
import com.microwavestudios.fortyfive.tests.AfterlifeTest
import com.microwavestudios.fortyfive.tests.BasicEncounterTest
import com.microwavestudios.fortyfive.tests.StatusEffectTest
import com.microwavestudios.fortyfive.utils.ANSI


class TestLauncher {

    val gameControllerTests: Array<GameControllerTest> = arrayOf(
        BasicEncounterTest(),
        AfterlifeTest(),
        StatusEffectTest()
    )

    fun run() {
//        runGameControllerTests()
        runFullGameTests()
    }

    fun runGameControllerTests() {
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
            FortyFive.logger.rawPrintln("${ANSI.green}All ${gameControllerTests.size} tests passed${ANSI.reset}")
        } else {
            FortyFive.logger.rawPrintln("${ANSI.red}${failedTests.size}/${gameControllerTests.size} tests failed")
            failedTests
                .joinToString(prefix = "Failed tests:\n", separator = "\n", postfix = ANSI.reset)
                .let { FortyFive.logger.rawPrintln(it) }
        }
    }

    fun runFullGameTests() {
        val appArgs = FortyFive.AppArguments(false, listOf(), false, null)
        FortyFive.appArguments = appArgs

        val config = Lwjgl3ApplicationConfiguration()
        config.setForegroundFPS(60)
        config.setTitle(".Forty-Five")
        config.setWindowedMode(1000, 800)
        config.setDecorated(true)
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
        config.setWindowIcon(Files.FileType.Internal, "blobs/icon.png")

        val listener = object : ApplicationAdapter() {

            override fun render() {
                println("hi")
            }
        }

        val relay = ApplicationListenerRelay(listOf(FortyFive, listener))

        try {
            Lwjgl3Application(relay, config)
        } catch (e: Exception) {
        }
//        Lwjgl3Application(relay, config)
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            TestLauncher().run()
        }
    }

}
