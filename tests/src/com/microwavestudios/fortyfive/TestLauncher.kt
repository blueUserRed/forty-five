package com.microwavestudios.fortyfive

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.backends.lwjgl.LwjglFiles
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.microwavestudios.fortyfive.testing.ApplicationListenerRelay
import com.microwavestudios.fortyfive.testing.GameControllerTest
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager
import com.microwavestudios.fortyfive.testing.mockservices.MockSoundPlayer
import com.microwavestudios.fortyfive.encounterTests.talismans.TalismanBulletAndGoldNuggetTest
import com.microwavestudios.fortyfive.fullGameTests.BasicFullGameTest
import com.microwavestudios.fortyfive.screen.InputProcessorProvider
import com.microwavestudios.fortyfive.testing.FullGameTest
import com.microwavestudios.fortyfive.testing.mockservices.InputInterceptor
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
//        TemporaryBehaviourTest(),
        TalismanBulletAndGoldNuggetTest(),
    )

    val fullGameTests: Array<FullGameTest> = arrayOf(
        BasicFullGameTest()
    )

    fun run() {
        Gdx.files = LwjglFiles()

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

//        runGameControllerTests(testLogDir)
        runFullGameTests()
    }

    fun runGameControllerTests(testLogDir: File?) {
        val profileManager = MockProfileManager()
        FortyFive.init()
        FortyFive.overrideGlobalsForTest(MockSoundPlayer(), profileManager)

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

        fullGameTests.forEach { test ->
            println("-----------------${test.name}-----------------")
            val relay = ApplicationListenerRelay(listOf(FortyFive, test))
            val interceptor = InputInterceptor()
            val inputInterceptorProvider = InputProcessorProvider { stage, inputManager ->
                interceptor.intercept(stage, inputManager)
            }
            test.preInit(interceptor)
            try {
                FortyFive.reset()
                FortyFive.overrideGlobalsForTest(
                    mockProfileManager = test.mockProfileManager,
                    inputProcessorProvider = inputInterceptorProvider
                )
                FortyFive.init()
                Lwjgl3Application(relay, config)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (!FortyFive.cleanExit) {
                println("${ANSI.red}Test failed: cleanExit is false${ANSI.reset}")
            }
        }
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            TestLauncher().run()
        }
    }

}
