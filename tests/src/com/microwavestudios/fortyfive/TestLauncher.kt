package com.microwavestudios.fortyfive

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl.LwjglFiles
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.microwavestudios.fortyfive.encounterTests.AfterlifeTest
import com.microwavestudios.fortyfive.encounterTests.BasicEncounterTest
import com.microwavestudios.fortyfive.encounterTests.StatusEffectTest
import com.microwavestudios.fortyfive.encounterTests.TemporaryBehaviourTest
import com.microwavestudios.fortyfive.testing.ApplicationListenerRelay
import com.microwavestudios.fortyfive.testing.GameControllerTest
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager
import com.microwavestudios.fortyfive.testing.mockservices.MockSoundPlayer
import com.microwavestudios.fortyfive.encounterTests.talismans.TalismanBulletAndGoldNuggetTest
import com.microwavestudios.fortyfive.fullGameTests.ShopFullGameTest
import com.microwavestudios.fortyfive.screen.InputProcessorProvider
import com.microwavestudios.fortyfive.testing.FullGameTest
import com.microwavestudios.fortyfive.testing.mockservices.InputInterceptor
import com.microwavestudios.fortyfive.testing.mockservices.MockGlobalSave
import com.microwavestudios.fortyfive.utils.ANSI
import com.microwavestudios.fortyfive.utils.FortyFiveLogger
import java.io.File
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.collections.component1
import kotlin.collections.component2


class TestLauncher {

    val gameControllerTests: Array<GameControllerTest> = arrayOf(
        BasicEncounterTest(),
        AfterlifeTest(),
        StatusEffectTest(),
        TemporaryBehaviourTest(),
        TalismanBulletAndGoldNuggetTest(),
    )

    val fullGameTests: Array<FullGameTest> = arrayOf(
//        ShopFullGameTest()
    )

    fun run() {
        Gdx.files = LwjglFiles()

        val doFileLogging = false
//        val doFileLogging = true

        val testLogDir = if (doFileLogging) {
            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss")
            val time = formatter.format(LocalDateTime.now())
            val testLogDir = File("./test_logs/$time")
            testLogDir.mkdir()
            testLogDir
        } else {
            null
        }

        val gameControllerDir = testLogDir?.let { dir ->
            File("${dir.canonicalPath}/encounter_tests")
        }
        gameControllerDir?.mkdir()
        val fullGameDir = testLogDir?.let { dir ->
            File("${dir.canonicalPath}/full_game_tests")
        }
        fullGameDir?.mkdir()

        val gameControllerTestResults = runGameControllerTests(gameControllerDir)
        val fullGameTestResults = runFullGameTests(fullGameDir)

        FortyFive.reset()
        FortyFive.init()

        val summaryFile = testLogDir?.let { dir -> File("${dir.canonicalPath}/summary.log") }
        val logger = FortyFive.logger

        val outputHandle = if (doFileLogging) {
            logger.addOutput(PrintStream(summaryFile!!), true)
        } else null

        logger.rawPrintln("\n\n\n")
        logger.rawPrintln("Test Summary ------------------------------------------------------\n")
        logger.rawPrintln("Encounter Tests -------------------------\n")
        printTests(gameControllerTestResults, logger)

        logger.rawPrintln("\n\n")
        logger.rawPrintln("Full Game Tests -------------------------\n")
        printTests(fullGameTestResults, logger)

        logger.rawPrintln("\n\n")
        logger.rawPrintln("Summary ---------------------------------\n")
        val passedControllerTests = gameControllerTestResults.filter { it.value == null }.count()
        val passedFullGameTests = fullGameTestResults.filter { it.value == null }.count()
        logger.rawPrintln("encounter tests: $passedControllerTests/${gameControllerTestResults.size} passed")
        logger.rawPrintln("full game tests: $passedFullGameTests/${fullGameTestResults.size} passed")
        val totalPassed = passedFullGameTests + passedControllerTests
        val totalTests = fullGameTestResults.size + gameControllerTestResults.size
        logger.rawPrintln("total: $totalPassed/$totalTests passed\n")

        when {
            totalTests == totalPassed -> logger.rawPrintln("${ANSI.green}All tests passed${ANSI.reset}")
            totalPassed == 0 -> logger.rawPrintln("${ANSI.red}All tests failed${ANSI.reset}")
            else -> logger.rawPrintln("${ANSI.red}Some tests failed${ANSI.reset}")
        }
        logger.rawPrintln("\n\n")

        if (outputHandle != null) logger.removeOutput(outputHandle)
        FortyFive.dispose()
    }

    private fun printTests(tests: Map<String, String?>, logger: FortyFiveLogger) {
        tests.forEach { (test, message) ->
            val passed = message == null
            if (passed) {
                logger.rawPrintln("${ANSI.green}$test: passed${ANSI.reset}")
            } else {
                logger.rawPrintln("${ANSI.red}$test: failed${ANSI.reset}\n")
                logger.rawPrintln(message)
            }
        }
    }

    fun runGameControllerTests(testLogDir: File?): Map<String, String?> {
        val profileManager = MockProfileManager()
        FortyFive.init()
        FortyFive.overrideGlobalsForTest(MockSoundPlayer(), profileManager)

        FortyFive.logger.title("start tests")
        val testResults = mutableMapOf<String, String?>()
        gameControllerTests.forEach { test ->
            FortyFive.logger.title("test: ${test.name}")
            val message = test.run(testLogDir)
            testResults[test.name] = message
        }
        return testResults
    }

    fun runFullGameTests(testLogDir: File?): Map<String, String?> {
        // avoid FortyFive.logger here because it gets disposed/reinitialized all the time
        println("---------------------------------------------------------------")
        println("------------------------Full game tests------------------------")
        println("---------------------------------------------------------------")
        println()

        val appArgs = FortyFive.AppArguments(false, listOf(), false, null)
        FortyFive.appArguments = appArgs

        val config = Lwjgl3ApplicationConfiguration()
        config.setForegroundFPS(60)
        config.setTitle(".Forty-Five")
        config.setWindowedMode(1000, 800)
        config.setDecorated(true)
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
        config.setWindowIcon(Files.FileType.Internal, "blobs/icon.png")

        val testResults = mutableMapOf<String, String?>()

        fullGameTests.forEach { test ->
            println("------------------------ ${test.name} ------------------------")
            testResults[test.name] = null
            var fileLoggerHandle: Int? = null
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
                    inputProcessorProvider = inputInterceptorProvider,
                    mockGlobalSave = MockGlobalSave(test.globalSaveData())
                )
                FortyFive.soundPlayer.masterVolume = 0f
                FortyFive.init()
                testLogDir?.let { logDir ->
                    val logFile = File("${logDir.canonicalPath}/${test.name}.log")
                    fileLoggerHandle = FortyFive.logger.addOutput(PrintStream(logFile), true)
                }
                Lwjgl3Application(relay, config)
            } catch (e: Exception) {
                e.printStackTrace()
                testResults[test.name] = "Test threw an exception:\n${e.stackTraceToString()}"
            } finally {
                if (fileLoggerHandle != null) {
                    FortyFive.logger.removeOutput(fileLoggerHandle)
                }
            }
            if (!FortyFive.cleanExit) {
                testResults[test.name] = "Test failed because the cleanExit flag is set to false"
            }
            if (test.resourcesWereLoadedOnClose) {
                testResults[test.name] = "Test failed because resources were loaded when the game was closed"
            }
            println("\n")
            val message = testResults[test.name]
            if (message == null) {
                println("${ANSI.green}Test ${test.name} successful${ANSI.reset}\n\n")
            } else {
                println("${ANSI.red}Test ${test.name} failed:${ANSI.reset}\n$message\n\n")
            }
        }
        return testResults
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            TestLauncher().run()
        }
    }

}
