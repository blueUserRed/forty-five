package com.fourinachamber.fortyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.map.*
import com.fourinachamber.fortyfive.game.card.CardTextureManager
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.onjNamespaces.*
import com.fourinachamber.fortyfive.rendering.RenderPipeline
import com.fourinachamber.fortyfive.screen.CustomBoxPlaygroundScreen
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.MapScreen
import com.fourinachamber.fortyfive.screen.Screens.AddMaxHPScreen
import com.fourinachamber.fortyfive.screen.Screens.HealOrMaxHPScreen
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.screenBuilder.FromKotlinScreenBuilder
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenBuilder
import com.fourinachamber.fortyfive.steam.SteamHandler
import com.fourinachamber.fortyfive.utils.*
import onj.customization.OnjConfig
import onj.value.OnjArray
import onj.value.OnjObject

import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * main game object
 */
object FortyFive : Game() {

    const val logTag = "forty-five"

    var currentRenderPipeline: RenderPipeline? = null
        private set

    val cardTextureManager: CardTextureManager = CardTextureManager()

    private var currentScreen: OnjScreen? = null
    private var nextScreen: OnjScreen? = null

    var currentGame: GameController? = null

    val serviceThread: ServiceThread = ServiceThread()

    var cleanExit: Boolean = true

    private var inScreenTransition: Boolean = false

    private val mainThreadTasks: ConcurrentHashMap<() -> Any?, Promise<*>> = ConcurrentHashMap()

    private val screenChangeCallbacks: MutableList<() -> Unit> = mutableListOf()

    lateinit var steamHandler: SteamHandler
        private set

    private var renderCounter: Long = 0L
    val renderTimes: IntArray = IntArray(15 * 60)

    private var screenTransitionCount: Long = 0L
    val screenTransitionTimes: IntArray = IntArray(5)

    private val timedCallbacks: MutableMap<() -> Unit, Long> = mutableMapOf()

    private val tutorialEncounterContext = object : GameController.EncounterContext {

        override val encounterIndex: Int = 0 // = first tutorial encounter

        override val forwardToScreen: String
            get() = "mapScreen"

        override fun completed() {
            SaveState.playerCompletedFirstTutorialEncounter = true
        }
    }

    override fun create() {
        init()
//        resetAll()
//        newRun(false)
        when (UserPrefs.startScreen) {
            UserPrefs.StartScreen.INTRO -> changeToScreen(ConfigFileManager.screenBuilderFor("introScreen"))
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

    fun onScreenChange(callback: () -> Unit) {
        screenChangeCallbacks.add(callback)
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
            nextScreen?.update(Gdx.graphics.deltaTime, isEarly = true)
            currentRenderPipeline?.render(Gdx.graphics.deltaTime)
        }
        renderTimes[(renderCounter % renderTimes.size).toInt()] = renderTime.toInt()
        renderCounter++
    }

    fun changeToScreen(screenBuilder: ScreenBuilder, controllerContext: Any? = null) = Gdx.app.postRunnable {
        if (inScreenTransition) return@postRunnable
        inScreenTransition = true
        val currentScreen = currentScreen
        if (currentScreen?.transitionAwayTimes != null) currentScreen.transitionAway()
        val screen = screenBuilder.build(controllerContext)
        nextScreen = screen

        fun onScreenChange() {
            FortyFiveLogger.title("changing screen to ${screenBuilder.name}")
            SoundPlayer.currentMusic(screen.music, screen)
            currentScreen?.dispose()
            screen.update(Gdx.graphics.deltaTime, isEarly = true)
            this.currentScreen = screen
            nextScreen = null
            currentRenderPipeline?.dispose()
            currentRenderPipeline = RenderPipeline(screen, screen).also {
                it.showDebugMenu = currentRenderPipeline?.showDebugMenu ?: false
            }
            setScreen(screen)
            // TODO: not 100% clean, this function is sometimes called when it isn't necessary
            MapManager.invalidateCachedAssets()
            inScreenTransition = false
            screenChangeCallbacks.forEach { it() }
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

    @AllThreadsAllowed
    fun useRenderPipeline(renderPipeline: RenderPipeline) {
        currentRenderPipeline?.dispose()
        currentRenderPipeline = renderPipeline.also {
            it.showDebugMenu = currentRenderPipeline?.showDebugMenu ?: false
        }
    }

    fun newRun(forwardToLooseScreen: Boolean) {
        FortyFiveLogger.title("newRun called; forwardToLooseScreen = $forwardToLooseScreen")
        PermaSaveState.newRun()
        if (forwardToLooseScreen) SaveState.copyStats()
        SaveState.reset()
        MapManager.newRunSync()
        if (forwardToLooseScreen) changeToScreen(ConfigFileManager.screenBuilderFor("looseScreen"))
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
            registerNameSpace("Style", StyleNamespace)
            registerNameSpace("Screen", ScreenNamespace)
            registerNameSpace("Map", MapNamespace)
        }
        ConfigFileManager.init()
        ConfigFileManager.addScreen("mapScreen", creator = { FromKotlinScreenBuilder(MapScreen()) })
        ConfigFileManager.addScreen("healOrMaxHPScreen", creator = { FromKotlinScreenBuilder(HealOrMaxHPScreen()) })
//        ConfigFileManager.addScreen("healOrMaxHPScreen", creator = { FromKotlinScreenBuilder(HealOrMaxHPScreen()) })
        ConfigFileManager.addScreen("addMaxHPScreen", creator = { FromKotlinScreenBuilder(AddMaxHPScreen()) })
        TemplateString.init()
        FortyFiveLogger.init()
        steamHandler = SteamHandler()
        UserPrefs.read()
        SoundPlayer.init()
        GameDirector.init()
        MapManager.init()
//        resetAll()
//        MapManager.generateMapsSync()
//        newRun(false)

        if (!Gdx.files.internal("saves/perma_savefile.onj").file().exists()) {
            resetAll()
        }
        PermaSaveState.read()
        SaveState.read()
        MapManager.read()
        GraphicsConfig.init()
        ResourceManager.init()
        serviceThread.start()
        cardTextureManager.init()
        RandomCardSelection.init()
//        resetAll()
//        newRun()
//        val cards = OnjParser.parseFile(Gdx.files.internal("config/cards.onj").file()) as OnjObject
//        printAllCards(cards)
//        println(cards.get<OnjArray>("cards").value.size)
//        println(cards.get<OnjArray>("cards").value.map {it as OnjObject}.map { it.get<String>("name") }.joinToString(separator = ",\n", transform = { "'$it'" }))
    }

    // this abomination prints all cards in respect to their rarities (a rarity3 card is printed three times)
    private fun printAllCards(cards: OnjObject) = cards
        .get<OnjArray>("cards")
        .value
        .map { it as OnjObject }
        .zip { it.get<OnjArray>("tags") }
        .mapSecond { it.value.find { (it.value as String).startsWith("rarity") } }
        .mapSecond {
            when (it?.value) {
                "rarity1" -> 1
                "rarity2" -> 2
                "rarity3" -> 3
                else -> null
            }
        }
        .filter { it.second != null }
        .map { (card, num) -> List(num!!) { card.get<String>("name") } }
        .flatten()
        .joinToString(separator = ",\n", transform = { "'$it'" })
        .let { println(it) }

    override fun dispose() {
        FortyFiveLogger.debug(logTag, "game closing")
        MapManager.write()
        PermaSaveState.write()
        SaveState.write()
        UserPrefs.write()
        currentScreen?.dispose()
        serviceThread.close()
        ResourceManager.end()
        super.dispose()
    }
}
