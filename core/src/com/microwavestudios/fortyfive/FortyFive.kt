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
import com.microwavestudios.fortyfive.onjNamespaces.GameNamespace
import com.microwavestudios.fortyfive.onjNamespaces.CommonNamespace
import com.microwavestudios.fortyfive.oven.BakeTask
import com.microwavestudios.fortyfive.oven.Oven
import com.microwavestudios.fortyfive.plugin.PluginManager
import com.microwavestudios.fortyfive.profile.GlobalSave
import com.microwavestudios.fortyfive.profile.IProfileManager
import com.microwavestudios.fortyfive.profile.ProfileManager
import com.microwavestudios.fortyfive.rendering.RenderPipeline
import com.microwavestudios.fortyfive.resources.ResourceManager
import com.microwavestudios.fortyfive.screen.ISoundPlayer
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
    val cardTextureManager = CardTextureManager()

    /** see [ServiceThread] */
    val serviceThread = ServiceThread()

    /** see [SoundPlayer] */
    var soundPlayer: ISoundPlayer = SoundPlayer()
        private set

    /** see [FortyFiveLogger] */
    val logger = FortyFiveLogger()

    /** see [ResourceManager] */
    val resourceManager = ResourceManager()

    var profileManager: IProfileManager = ProfileManager()
        private set

    /** see [ScreenManager] */
    val screenManager = ScreenManager(TitleScreen, null)

    val globalSave = GlobalSave()

    val pluginManager = PluginManager()


    private val _lifetime: EndableLifetime = EndableLifetime()
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

    override fun create() {
        init()
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

    fun initTest(mockSoundPlayer: ISoundPlayer, mockProfileManager: IProfileManager) {
        soundPlayer = mockSoundPlayer
        profileManager = mockProfileManager
        with(OnjConfig) {
            registerNamespace("Common", CommonNamespace)
            registerNamespace("Game", GameNamespace)
        }
        ConfigFileManager.init()
        logger.init()
        profileManager.init()
        globalSave.readFromDisk()
        pluginManager.init()
        pluginManager.earlyInit()
        soundPlayer.init()
        GraphicsConfig.init()
    }

    private fun init() {
        ShaderProgram.pedantic = false
        with(OnjConfig) {
            if (getNamespace("Common") == null) registerNamespace("Common", CommonNamespace)
            if (getNamespace("Game") == null) registerNamespace("Game", GameNamespace)
        }
        initControllers()
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
        resourceManager.init()
        if (serviceThread.state == Thread.State.NEW) serviceThread.start()
        cardTextureManager.init()
        if (logger.versionTag != "--dev--") return
        File(".onj").mkdirs()
        OnjConfig.dumpOnjEnv(File(".onj/forty-five.onjenv"))
    }

    private fun initControllers() {
        globalSave.currentControllerUid = Controllers.getControllers().firstOrNull()?.uniqueId
        Controllers.addListener(object : ControllerAdapter() {

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
        })
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
