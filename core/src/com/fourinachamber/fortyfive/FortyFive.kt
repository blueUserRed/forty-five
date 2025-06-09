package com.fourinachamber.fortyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.map.*
import com.fourinachamber.fortyfive.game.card.CardTextureManager
import com.fourinachamber.fortyfive.game.controller.EncounterContext
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.map.events.chooseCard.ChooseCardScreenContext
import com.fourinachamber.fortyfive.onjNamespaces.*
import com.fourinachamber.fortyfive.rendering.RenderPipeline
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.ScreenManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.DebugActorImpl
import com.fourinachamber.fortyfive.screen.screenBuilder.FromKotlinScreenBuilder
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenBuilder
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.screen.screens.ChooseCardScreen
import com.fourinachamber.fortyfive.screen.screens.MapScreen
import com.fourinachamber.fortyfive.screen.screens.TitleScreen
import com.fourinachamber.fortyfive.steam.SteamHandler
import com.fourinachamber.fortyfive.utils.*
import onj.customization.OnjConfig

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis

object FortyFive : Game() {

    const val logTag = "forty-five"

    val cardTextureManager = CardTextureManager()
    val serviceThread = ServiceThread()
    val soundPlayer = SoundPlayer()
    val logger = FortyFiveLogger()
    val resourceManager = ResourceManager()
    val screenManager = ScreenManager(TitleScreen, null)

    lateinit var steamHandler: SteamHandler
        private set

    var currentRenderPipeline: RenderPipeline? = null
        private set

    var currentScreen: OnjScreen? = null

    var cleanExit: Boolean = true

    private val mainThreadTasks: ConcurrentHashMap<() -> Any?, Promise<*>> = ConcurrentHashMap()

    private var renderCounter: Long = 0L
    val renderTimes: IntArray = IntArray(15 * 60)

//    private var screenTransitionCount: Long = 0L
    val screenTransitionTimes: IntArray = IntArray(5)

    private val timedCallbacks: MutableMap<() -> Unit, Long> = mutableMapOf()

    override fun create() {
        init()
        val context = object : ChooseCardScreenContext {
            override var seed: Long = 2378949203847
            override val nbrOfCards: Int = 3
            override val types: List<String> = listOf()
            override val enableRerolls: Boolean = true
            override var amountOfRerolls: Int = 0
            override val rerollPriceIncrease: Int = 30
            override val rerollBasePrice: Int = 30

            override fun completed() {
            }
        }
        screenManager.transitionImmediate(ChooseCardScreen, context)
//        when (UserPrefs.startScreen) {
//            UserPrefs.StartScreen.INTRO -> screenManager.transitionImmediate(TitleScreen)
//            UserPrefs.StartScreen.TITLE -> screenManager.transitionImmediate(TitleScreen)
//            UserPrefs.StartScreen.MAP -> toMap()
//        }
    }

    fun toMap() {
        screenManager.newBaseScreen(MapScreen)
        screenManager.screenFinished()
    }

    fun toTitleScreen() {
        screenManager.newBaseScreen(TitleScreen)
        screenManager.screenFinished()
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
        DebugActorImpl.dumpActorsWithDebugWarnings()
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
