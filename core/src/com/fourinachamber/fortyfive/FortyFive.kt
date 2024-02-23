package com.fourinachamber.fortyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.map.*
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.onjNamespaces.*
import com.fourinachamber.fortyfive.rendering.RenderPipeline
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenBuilder
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.utils.*
import onj.customization.OnjConfig
import onj.parser.OnjParser
import onj.value.OnjArray
import onj.value.OnjObject

/**
 * main game object
 */
object FortyFive : Game() {

    const val logTag = "forty-five"

    var currentRenderPipeline: RenderPipeline? = null
        private set

    private var currentScreen: OnjScreen? = null

    var currentGame: GameController? = null

    val serviceThread: ServiceThread = ServiceThread()

    var cleanExit: Boolean = true

    private var inScreenTransition: Boolean = false

    private val tutorialEncounterContext = object : GameController.EncounterContext {

        override val encounterIndex: Int = 0 // = first tutorial encounter

        override val forwardToScreen: String
            get() = MapManager.mapScreenPath

        override fun completed() {
            SaveState.playerCompletedFirstTutorialEncounter = true
        }
    }

    override fun create() {
        init()
//        resetAll()
//        newRun(false)
//        changeToInitialScreen()
        changeToScreen("screens/intro_screen.onj")
    }

    fun changeToInitialScreen() {
        if (!SaveState.playerCompletedFirstTutorialEncounter) {
            MapManager.changeToEncounterScreen(tutorialEncounterContext)
        } else {
            MapManager.changeToMapScreen()
        }
    }

    override fun render() {
        val screen = currentScreen
        currentScreen?.update(Gdx.graphics.deltaTime)
        if (screen !== currentScreen) currentScreen?.update(Gdx.graphics.deltaTime)
        currentRenderPipeline?.render(Gdx.graphics.deltaTime)
    }

    fun changeToScreen(screenPath: String, controllerContext: Any? = null) = Gdx.app.postRunnable {
        if (inScreenTransition) return@postRunnable
        inScreenTransition = true
        val currentScreen = currentScreen
        if (currentScreen?.transitionAwayTime != null) currentScreen.transitionAway()
        val screen = ScreenBuilder(Gdx.files.internal(screenPath)).build(controllerContext)
        // Updates StyleManagers immediately, to prevent the first frame from appearing bugged
        screen.update(Gdx.graphics.deltaTime, isEarly = true)
        serviceThread.sendMessage(ServiceThreadMessage.PrepareResources)

        fun onScreenChange() {
            FortyFiveLogger.title("changing screen to $screenPath")
            SoundPlayer.currentMusic(screen.music, screen)
            currentScreen?.dispose()
            this.currentScreen = screen
            currentRenderPipeline?.dispose()
            currentRenderPipeline = RenderPipeline(screen, screen).also {
                it.showFps = currentRenderPipeline?.showFps ?: false
            }
            setScreen(screen)
            // TODO: not 100% clean, this function is sometimes called when it isn't necessary
            MapManager.invalidateCachedAssets()
            inScreenTransition = false
            ResourceManager.trimPrepared()
        }

        if (currentScreen == null) {
            onScreenChange()
        } else currentScreen.afterMs(currentScreen.transitionAwayTime ?: 0) {
            onScreenChange()
        }
    }

    @AllThreadsAllowed
    fun useRenderPipeline(renderPipeline: RenderPipeline) {
        currentRenderPipeline?.dispose()
        currentRenderPipeline = renderPipeline.also {
            it.showFps = currentRenderPipeline?.showFps ?: false
        }
    }

    fun newRun(forwardToLooseScreen: Boolean) {
        FortyFiveLogger.title("newRun called; forwardToLooseScreen = $forwardToLooseScreen")
        PermaSaveState.newRun()
        if (forwardToLooseScreen) SaveState.copyStats()
        SaveState.reset()
        MapManager.newRunSync()
        if (forwardToLooseScreen) changeToScreen("screens/loose_screen.onj")
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        currentRenderPipeline?.sizeChanged()
    }

    fun resetAll() {
        PermaSaveState.reset()
        SaveState.reset()
        MapManager.resetAllSync()
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
        TemplateString.init()
        FortyFiveLogger.init()
        SoundPlayer.init()
        GameDirector.init()
        MapManager.init()
//        resetAll()
//        MapManager.generateMapsSync()
//        newRun()

        if (!Gdx.files.internal("saves/perma_savefile.onj").file().exists()) {
            resetAll()
        }
        PermaSaveState.read()
        SaveState.read()
        MapManager.read()
        GraphicsConfig.init()
        ResourceManager.init()
        serviceThread.start()
        serviceThread.sendMessage(ServiceThreadMessage.PrepareCards(true))
        RandomCardSelection.init()

//        resetAll()
//        newRun()
//        val cards = OnjParser.parseFile(Gdx.files.internal("config/cards.onj").file()) as OnjObject
//        println(cards.get<OnjArray>("cards").value.size)
//        println(cards.get<OnjArray>("cards").value.map {it as OnjObject}.map { it.get<String>("name") }.joinToString(separator = ",\n", transform = { "'$it'" }))
    }

    override fun dispose() {
        FortyFiveLogger.debug(logTag, "game closing")
        MapManager.write()
        PermaSaveState.write()
        SaveState.write()
        currentScreen?.dispose()
        serviceThread.close()
        ResourceManager.trimPrepared()
        ResourceManager.end()
        super.dispose()
    }
}
