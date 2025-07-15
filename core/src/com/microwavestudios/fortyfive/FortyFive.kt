package com.microwavestudios.fortyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.*
import com.microwavestudios.fortyfive.game.card.CardTextureManager
import com.microwavestudios.fortyfive.map.MapManager
import com.microwavestudios.fortyfive.game.card.RandomCardSelection
import com.microwavestudios.fortyfive.onjNamespaces.CardsNamespace
import com.microwavestudios.fortyfive.onjNamespaces.CommonNamespace
import com.microwavestudios.fortyfive.onjNamespaces.MapNamespace
import com.microwavestudios.fortyfive.oven.BakeTask
import com.microwavestudios.fortyfive.oven.Oven
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.profile.ProfileManager
import com.microwavestudios.fortyfive.rendering.RenderPipeline
import com.microwavestudios.fortyfive.resources.ResourceManager
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.SoundPlayer
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.actors.DebugActorImpl
import com.microwavestudios.fortyfive.screen.screens.IntroScreen
import com.microwavestudios.fortyfive.screen.screens.MapScreen
import com.microwavestudios.fortyfive.screen.screens.TitleScreen
import com.microwavestudios.fortyfive.screen.screens.WinRunScreen
import com.microwavestudios.fortyfive.steam.SteamHandler
import com.microwavestudios.fortyfive.utils.*
import onj.customization.OnjConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

object FortyFive : Game() {

    const val logTag = "forty-five"

    val cardTextureManager = CardTextureManager()
    val serviceThread = ServiceThread()
    val soundPlayer = SoundPlayer()
    val logger = FortyFiveLogger()
    val resourceManager = ResourceManager()
    val profileManager = ProfileManager()
    val screenManager = ScreenManager(TitleScreen, null)

    private val _lifetime: EndableLifetime = EndableLifetime()
    val gameLifetime: Lifetime
        get() = _lifetime

    lateinit var steamHandler: SteamHandler
        private set

    var currentRenderPipeline: RenderPipeline? = null
        private set

    var currentScreen: OnjScreen? = null

    var cleanExit: Boolean = true
    lateinit var appArguments: AppArguments

    private val mainThreadTasks: ConcurrentHashMap<() -> Any?, Promise<*>> = ConcurrentHashMap()

    private var renderCounter: Long = 0L
    val renderTimes: IntArray = IntArray(15 * 60)

//    private var screenTransitionCount: Long = 0L
    val screenTransitionTimes: IntArray = IntArray(5)

    private val timedCallbacks: MutableMap<() -> Unit, Long> = mutableMapOf()

    override fun create() {

        init()
        if (appArguments.bakeRun) {
            Oven().bake(appArguments.bakeTasks)
            return
        }

        profileManager.currentProfile = Profile.loadProfile("A")

        when (UserPrefs.startScreen) {
            UserPrefs.StartScreen.INTRO -> screenManager.appendScreen(IntroScreen)
            UserPrefs.StartScreen.TITLE -> screenManager.appendScreen(TitleScreen)
            UserPrefs.StartScreen.MAP -> toMap()
        }
        screenManager.screenFinished()
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
        MapManager.init()

        if (!Gdx.files.internal("saves/perma_savefile.onj").file().exists()) {
            resetAll()
        }
        PermaSaveState.read()
        SaveState.read()
        GraphicsConfig.init()
        resourceManager.init()
        serviceThread.start()
        cardTextureManager.init()
        RandomCardSelection.init()
    }

    override fun dispose() {
        logger.debug(logTag, "game closing")
        DebugActorImpl.dumpActorsWithDebugWarnings()
        profileManager.currentProfile?.write()
        profileManager.currentProfile?.writeMaps()
        PermaSaveState.write()
        SaveState.write()
        UserPrefs.write()
        _lifetime.die()
        soundPlayer.end()
        currentScreen?.dispose()
        currentRenderPipeline?.dispose()
        serviceThread.close()
        resourceManager.end()
        super.dispose()
    }


    data class AppArguments(val bakeRun: Boolean, val bakeTasks: List<BakeTask>)

}
