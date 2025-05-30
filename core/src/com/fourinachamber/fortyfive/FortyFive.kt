package com.fourinachamber.fortyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.map.*
import com.fourinachamber.fortyfive.game.card.CardTextureManager
import com.fourinachamber.fortyfive.game.controller.EncounterContext
import com.fourinachamber.fortyfive.game.controller.GameController
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.onjNamespaces.*
import com.fourinachamber.fortyfive.rendering.RenderPipeline
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.DebugBoundsActor
import com.fourinachamber.fortyfive.screen.general.customActor.DebugBoundsActorImpl
import com.fourinachamber.fortyfive.screen.screenBuilder.FromKotlinScreenBuilder
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenBuilder
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.screen.screens.TestScreen
import com.fourinachamber.fortyfive.steam.SteamHandler
import com.fourinachamber.fortyfive.utils.*
import onj.customization.OnjConfig
import onj.value.OnjArray
import onj.value.OnjObject

import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

object FortyFive : Game() {

    const val logTag = "forty-five"

    val cardTextureManager = CardTextureManager()
    val serviceThread = ServiceThread()
    val soundPlayer = SoundPlayer()
    val logger = FortyFiveLogger()
    val resourceManager = ResourceManager()

    lateinit var steamHandler: SteamHandler
        private set

    var currentRenderPipeline: RenderPipeline? = null
        private set

    private var currentScreen: OnjScreen? = null
    private var nextScreen: OnjScreen? = null

    var cleanExit: Boolean = true

    private var inScreenTransition: Boolean = false

    private val mainThreadTasks: ConcurrentHashMap<() -> Any?, Promise<*>> = ConcurrentHashMap()

    private var renderCounter: Long = 0L
    val renderTimes: IntArray = IntArray(15 * 60)

    private var screenTransitionCount: Long = 0L
    val screenTransitionTimes: IntArray = IntArray(5)

    private val timedCallbacks: MutableMap<() -> Unit, Long> = mutableMapOf()

    private val tutorialEncounterContext = object : EncounterContext {

        override val encounterIndex: Int = 0 // = first tutorial encounter

        override val forwardToScreen: String
            get() = "mapScreen"

        override fun completed() {
            SaveState.playerCompletedFirstTutorialEncounter = true
        }
    }

    override fun create() {
        init()
        UserPrefs.startScreen = UserPrefs.StartScreen.MAP
        when (UserPrefs.startScreen) {
            UserPrefs.StartScreen.INTRO -> TODO()
            UserPrefs.StartScreen.TITLE -> MapManager.changeToTitleScreen()
            UserPrefs.StartScreen.MAP -> changeToInitialScreen()
        }
    }

    fun changeToInitialScreen() {
        if (!SaveState.playerCompletedFirstTutorialEncounter) {
            MapManager.changeToEncounterScreen(tutorialEncounterContext)
        } else {
            MapManager.changeToMapScreen()
        }
    }

    fun <T> mainThreadTask(task: () -> T): Promise<T> {
        val promise = Promise<T>()
        mainThreadTasks[task] = promise
        return promise
    }

    fun inMs(time: Int, callback: () -> Unit) {
        timedCallbacks[callback] = TimeUtils.millis() + time
    }

    override fun render() {
        val renderTime = measureTimeMillis {
            timedCallbacks.iterateRemoving { (callback, time), remove ->
                if (TimeUtils.millis() < time) return@iterateRemoving
                callback()
                remove()
            }
            mainThreadTasks.forEach { (task, promise) ->
                val result = task()
                @Suppress("UNCHECKED_CAST")
                (promise as Promise<Any?>).resolve(result)
                mainThreadTasks.remove(task)
            }
            currentScreen?.update(Gdx.graphics.deltaTime)
            currentRenderPipeline?.render(Gdx.graphics.deltaTime)
        }
        renderTimes[(renderCounter % renderTimes.size).toInt()] = renderTime.toInt()
        renderCounter++
    }

    fun changeToScreen(screenCreator: ScreenCreator, controllerContext: Any? = null) {
        val builder = FromKotlinScreenBuilder(screenCreator)
        changeToScreen(builder, controllerContext)
    }

    fun changeToScreen(screenBuilder: ScreenBuilder, controllerContext: Any? = null) = Gdx.app.postRunnable {
        if (inScreenTransition) return@postRunnable
        inScreenTransition = true
        val currentScreen = currentScreen
        if (currentScreen?.transitionAwayTimes != null) currentScreen.transitionAway()
        val screen = screenBuilder.build(controllerContext, currentScreen)
        nextScreen = screen

        fun onScreenChange() {
            logger.title("changing screen to ${screenBuilder.name}")
            currentScreen?.dispose()
            this.currentScreen = screen
            nextScreen = null
            currentRenderPipeline?.dispose()
            currentRenderPipeline = RenderPipeline(screen, screen)
            setScreen(screen)
            // TODO: not 100% clean, this function is sometimes called when it isn't necessary
            MapManager.invalidateCachedAssets()
            inScreenTransition = false
            inMs(100) {
                val lagSpike = renderTimes.max()
                screenTransitionTimes[(screenTransitionCount % screenTransitionTimes.size).toInt()] = lagSpike
                screenTransitionCount++
            }
        }

        val transitionAwayTime = currentScreen?.transitionAwayTimes?.let {
            it[screenBuilder.name] ?: it["*"]
        } ?: 0
        if (currentScreen == null) {
            onScreenChange()
        } else currentScreen.afterMs(transitionAwayTime) {
            onScreenChange()
        }
    }

    fun useRenderPipeline(renderPipeline: RenderPipeline) {
        currentRenderPipeline?.dispose()
        currentRenderPipeline = renderPipeline
    }

    fun newRun(forwardToLooseScreen: Boolean) {
        logger.title("newRun called; forwardToLooseScreen = $forwardToLooseScreen")
        PermaSaveState.newRun()
        if (forwardToLooseScreen) SaveState.copyStats()
        SaveState.reset()
        MapManager.newRunSync()
        if (forwardToLooseScreen) TODO()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        currentRenderPipeline?.sizeChanged()
        if (UserPrefs.windowMode == UserPrefs.WindowMode.Window) UserPrefs.windowWidth = width
    }

    fun resetAll() {
        PermaSaveState.reset()
        SaveState.reset()
        MapManager.resetAllSync()
        UserPrefs.reset()
        newRun(false)
    }

    private fun init() {
        ShaderProgram.pedantic = false
        with(OnjConfig) {
            registerNameSpace("Common", CommonNamespace)
            registerNameSpace("Cards", CardsNamespace)
            registerNameSpace("Map", MapNamespace)
        }
        ConfigFileManager.init()
        TemplateString.init()
        logger.init()
        steamHandler = SteamHandler()
        UserPrefs.read()
        soundPlayer.init()
        GameDirector.init()
        MapManager.init()

        if (!Gdx.files.internal("saves/perma_savefile.onj").file().exists()) {
            resetAll()
        }
        PermaSaveState.read()
        SaveState.read()
        MapManager.read()
        GraphicsConfig.init()
        resourceManager.init()
        serviceThread.start()
        cardTextureManager.init()
        RandomCardSelection.init()
    }

    override fun dispose() {
        logger.debug(logTag, "game closing")
        DebugBoundsActorImpl.dumpActorsWithBadTextures()
        MapManager.write()
        PermaSaveState.write()
        SaveState.write()
        UserPrefs.write()
        currentScreen?.dispose()
        serviceThread.close()
        resourceManager.end()
        super.dispose()
    }
}
