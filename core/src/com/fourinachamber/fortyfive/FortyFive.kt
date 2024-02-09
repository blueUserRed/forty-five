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
import com.fourinachamber.fortyfive.utils.*
import onj.customization.OnjConfig

/**
 * main game object
 */
object FortyFive : Game() {

    const val logTag = "forty-five"

    private var currentRenderPipeline: RenderPipeline? = null
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
        if (SaveState.playerCompletedFirstTutorialEncounter) {
//            MapManager.changeToMapScreen()
            changeToScreen("screens/loose_screen.onj")
        } else {
            MapManager.changeToEncounterScreen(tutorialEncounterContext)
        }
    }

    override fun render() {
        currentScreen?.update(Gdx.graphics.deltaTime)
        currentRenderPipeline?.render(Gdx.graphics.deltaTime)
    }

    fun changeToScreen(screenPath: String, controllerContext: Any? = null) = Gdx.app.postRunnable {
        if (inScreenTransition) return@postRunnable
        inScreenTransition = true
        val currentScreen = currentScreen
        if (currentScreen?.transitionAwayTime != null) currentScreen.transitionAway()
        val screen = ScreenBuilder(Gdx.files.internal(screenPath)).build(controllerContext)

        serviceThread.sendMessage(ServiceThreadMessage.PrepareResources)

        fun onScreenChange() {
            FortyFiveLogger.title("changing screen to $screenPath")
            currentScreen?.dispose()
            this.currentScreen = screen
            currentRenderPipeline?.dispose()
            currentRenderPipeline = RenderPipeline(screen, screen)
            setScreen(screen)
            // TODO: not 100% clean, this function is sometimes called when it isn't necessary
            MapManager.invalidateCachedAssets()
            inScreenTransition = false
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
        currentRenderPipeline = renderPipeline
    }

    fun newRun(forwardToTutorialScreen: Boolean) {
        FortyFiveLogger.title("newRun called; forwardToTutorialScreen = $forwardToTutorialScreen")
        PermaSaveState.newRun()
        SaveState.reset()
        MapManager.newRunSync()
        if (forwardToTutorialScreen) MapManager.changeToEncounterScreen(tutorialEncounterContext)
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        currentRenderPipeline?.sizeChanged()
    }

    fun resetAll() {
        PermaSaveState.reset()
        SaveState.reset()
        MapManager.resetAllSync()
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
