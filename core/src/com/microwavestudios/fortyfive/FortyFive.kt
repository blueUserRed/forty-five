package com.microwavestudios.fortyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.controllers.Controller
import com.badlogic.gdx.controllers.ControllerAdapter
import com.badlogic.gdx.controllers.Controllers
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.*
import com.microwavestudios.fortyfive.game.card.CardTextureManager
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.map.events.specialevent.SpecialEventFactory
import com.microwavestudios.fortyfive.onjNamespaces.GameNamespace
import com.microwavestudios.fortyfive.onjNamespaces.CommonNamespace
import com.microwavestudios.fortyfive.onjNamespaces.SpecialEventNamespace
import com.microwavestudios.fortyfive.oven.BakeTask
import com.microwavestudios.fortyfive.oven.Oven
import com.microwavestudios.fortyfive.plugin.PluginManager
import com.microwavestudios.fortyfive.profile.GlobalSave
import com.microwavestudios.fortyfive.profile.IProfileManager
import com.microwavestudios.fortyfive.profile.ProfileManager
import com.microwavestudios.fortyfive.rendering.RenderPipeline
import com.microwavestudios.fortyfive.resources.ResourceManager
import com.microwavestudios.fortyfive.screen.ISoundPlayer
import com.microwavestudios.fortyfive.screen.InputProcessorProvider
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.SoundPlayer
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.actors.DebugActorImpl
import com.microwavestudios.fortyfive.screen.screens.*
import com.microwavestudios.fortyfive.steam.SteamHandler
import com.microwavestudios.fortyfive.utils.*
import onj.customization.OnjConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

object FortyFive : Game() {

    private const val logTag = "forty-five"

    const val DEMO_MODE: Boolean = true


    /** see [CardTextureManager] */
    var cardTextureManager = CardTextureManager()
        private set

    /** see [ServiceThread] */
    var serviceThread = ServiceThread()
        private set

    /** see [SoundPlayer] */
    var soundPlayer: ISoundPlayer = SoundPlayer()
        private set

    /** see [FortyFiveLogger] */
    var logger = FortyFiveLogger()
        private set

    /** see [ResourceManager] */
    var resourceManager = ResourceManager()
        private set

    var profileManager: IProfileManager = ProfileManager()
        private set

    /** see [ScreenManager] */
    var screenManager = ScreenManager(TitleScreen, null)
        private set

    var globalSave = GlobalSave()
        private set

    var pluginManager = PluginManager()
        private set

    /** allows defining alternative input processors. Useful for testing */
    var inputProcessorProvider: InputProcessorProvider = InputProcessorProvider.default
        private set


    private var _lifetime: EndableLifetime = EndableLifetime()
    val gameLifetime: Lifetime
        get() = _lifetime

    lateinit var steamHandler: SteamHandler
        private set

    var currentRenderPipeline: RenderPipeline? = null
        private set

    var currentScreen: RenderableScreen? = null

    var cleanExit: Boolean = true
    lateinit var appArguments: AppArguments

    private val mainThreadTasks: ConcurrentHashMap<() -> Any?, Promise<*>> = ConcurrentHashMap()

    private var renderCounter: Long = 0L
    val renderTimes: IntArray = IntArray(15 * 60)

    private val timedCallbacks: MutableMap<() -> Unit, Long> = mutableMapOf()

    private val controllerListener = object : ControllerAdapter() {

        override fun connected(controller: Controller?) {
            controller?.let {
                currentScreen?.inputManager?.controllerConnected(it)
            }
        }

        override fun disconnected(controller: Controller?) {
            controller?.let {
                currentScreen?.inputManager?.controllerDisconnected(it)
            }
        }
    }


    override fun create() {
        initControllers()
        pluginManager.start()

        if (appArguments.bakeRun) {
            Oven().bake(appArguments.bakeTasks)
            return
        }
        if (appArguments.mapEditor) {
            screenManager.appendScreen(MapEditorScreen, object : MapEditorContext {
                override val map: DetailMap? = null
                override var mapPath: String? = appArguments.providedMapPath
            })
            screenManager.screenFinished()
            return
        }

        globalSave.setToCorrectWindowMode()
        if (!globalSave.skipIntroScreen) screenManager.appendScreen(IntroScreen)
        screenManager.appendScreen(TitleScreen)
        screenManager.screenFinished()
    }

    fun toMap() {
        screenManager.newBaseScreen(MapScreen, MapScreenContext.default)
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
        screenManager.update()
        val renderTime = measureTimeMillis {
            pluginManager.onRender()
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

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        currentRenderPipeline?.sizeChanged()
    }

    fun reset() {
        with(OnjConfig) {
            deregisterNamespace("Common")
            deregisterNamespace("Game")
            deregisterNamespace("SpecialEvent")
        }
        cardTextureManager = CardTextureManager()
        serviceThread = ServiceThread()
        soundPlayer = SoundPlayer()
        logger = FortyFiveLogger()
        resourceManager = ResourceManager()
        profileManager = ProfileManager()
        screenManager = ScreenManager(TitleScreen, null)
        globalSave = GlobalSave()
        pluginManager = PluginManager()
        _lifetime = EndableLifetime()
        steamHandler = SteamHandler()
        currentRenderPipeline = null
        currentScreen = null
        screen = null
        cleanExit = true
        mainThreadTasks.clear()
        timedCallbacks.clear()
        if (Gdx.app != null) Controllers.removeListener(controllerListener)
    }

    fun overrideGlobalsForTest(
        mockSoundPlayer: ISoundPlayer = SoundPlayer(),
        mockProfileManager: IProfileManager = ProfileManager(),
        inputProcessorProvider: InputProcessorProvider = InputProcessorProvider.default,
    ) {
        soundPlayer = mockSoundPlayer
        profileManager = mockProfileManager
        this.inputProcessorProvider = inputProcessorProvider
    }

    fun init() {
        ShaderProgram.pedantic = false
        with(OnjConfig) {
            registerNamespace("Common", CommonNamespace)
            registerNamespace("Game", GameNamespace)
            registerNamespace("SpecialEvent", SpecialEventNamespace)
        }
        ConfigFileManager.init()
        TemplateString.init()
        logger.init()
        profileManager.init()
        steamHandler = SteamHandler()
        globalSave.readFromDisk()
        pluginManager.init()
        pluginManager.earlyInit()
        soundPlayer.init()
        GraphicsConfig.init()
        SpecialEventFactory.init()
        resourceManager.init()
        if (serviceThread.state == Thread.State.NEW) serviceThread.start()
        cardTextureManager.init()
        if (logger.versionTag != "--dev--") return
        File(".onj").mkdirs()
        OnjConfig.dumpOnjEnv(File(".onj/forty-five.onjenv"))
    }

    private fun initControllers() {
        if (Gdx.app == null) return
        globalSave.currentControllerUid = Controllers.getControllers().firstOrNull()?.uniqueId
        Controllers.addListener(controllerListener)
    }

    override fun dispose() {
        logger.debug(logTag, "game closing")
        DebugActorImpl.dumpActorsWithDebugWarnings()
        pluginManager.onEnd()
        profileManager.currentProfile?.write()
        globalSave.write()
        _lifetime.die()
        soundPlayer.end()
        currentScreen?.dispose()
        currentRenderPipeline?.dispose()
        serviceThread.close()
        resourceManager.end()
        super.dispose()
    }


    data class AppArguments(
        val bakeRun: Boolean,
        val bakeTasks: List<BakeTask>,
        val mapEditor: Boolean,
        val providedMapPath: String?,
    )

}
