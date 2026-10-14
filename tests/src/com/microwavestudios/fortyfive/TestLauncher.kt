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
import com.microwavestudios.fortyfive.tests.SourceBulletRefTest
import com.microwavestudios.fortyfive.tests.StatusEffectTest
import com.microwavestudios.fortyfive.utils.ANSI
import java.io.File
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class TestLauncher {

    val gameControllerTests: Array<GameControllerTest> = arrayOf(
//        BasicEncounterTest(),
//        AfterlifeTest(),
//        StatusEffectTest(),
        SourceBulletRefTest(),
    )

    fun run() {
        val doFileLogging = false

        val testLogDir = if (doFileLogging) {
            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss")
            val time = formatter.format(LocalDateTime.now())
            val testLogDir = File("./test_logs/$time")
            testLogDir.mkdir()
            testLogDir
        } else {
            null
        }

        runGameControllerTests(testLogDir)
//        runFullGameTests()
    }

    fun runGameControllerTests(testLogDir: File?) {
        Gdx.files = LwjglFiles()
        val profileManager = MockProfileManager()
        FortyFive.initTest(MockSoundPlayer(), profileManager)

        FortyFive.logger.title("start tests")
        val failedTests = mutableListOf<Pair<String, String>>()
        gameControllerTests.forEach { test ->
            FortyFive.logger.title("test: ${test.name}")
            val message = test.run(testLogDir)
            val passed = message == null
            if (passed) return@forEach
            failedTests.add(test.name to message)
        }
        val doFileLogging = testLogDir != null
        val outputHandle = if (doFileLogging) {
            val summaryLog = File("${testLogDir.canonicalPath}/summary.log")
            summaryLog.createNewFile()
            FortyFive.logger.addOutput(PrintStream(summaryLog), true)
        } else {
            null
        }
        FortyFive.logger.title("test summary")
        if (failedTests.isEmpty()) {
            FortyFive.logger.rawPrintln("${ANSI.green}All ${gameControllerTests.size} tests passed${ANSI.reset}")
        } else {
            FortyFive.logger.rawPrintln("${ANSI.red}${failedTests.size}/${gameControllerTests.size} tests failed:")
            failedTests.forEach { (test, message) ->
                FortyFive.logger.rawPrintln("")
                FortyFive.logger.rawPrintln("--$test:--")
                FortyFive.logger.rawPrintln(message)
            }
        }
        if (doFileLogging) FortyFive.logger.removeOutput(outputHandle!!)
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
