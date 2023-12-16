package com.fourinachamber.fortyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.map.*
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.onjNamespaces.*
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenBuilder
import com.fourinachamber.fortyfive.rendering.Renderable
import com.fourinachamber.fortyfive.screen.ResourceManager
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

    private var currentRenderable: Renderable? = null
    private var currentScreen: OnjScreen? = null

    var currentGame: GameController? = null

    val serviceThread: ServiceThread = ServiceThread()

    var cleanExit: Boolean = true

    override fun create() {
        init()
        serviceThread.start()
        changeToScreen("screens/map_screen.onj")
    }

    override fun render() {
        currentRenderable?.render(Gdx.graphics.deltaTime)
    }

    fun changeToScreen(screenPath: String, controllerContext: Any? = null) {
        val currentScreen = currentScreen
        if (currentScreen?.transitionAwayTime != null) currentScreen.transitionAway()
        val screen = ScreenBuilder(Gdx.files.internal(screenPath)).build(controllerContext)

        serviceThread.sendMessage(ServiceThreadMessage.PrepareResources)

        fun onScreenChange() {
            FortyFiveLogger.title("changing screen")
            currentScreen?.dispose()
            this.currentScreen = screen
            currentRenderable = screen
            setScreen(screen)
            // TODO: not 100% clean, this function is sometimes called when it isn't necessary
            MapManager.invalidateCachedAssets()
        }

        if (currentScreen == null) {
            onScreenChange()
        } else currentScreen.afterMs(currentScreen.transitionAwayTime ?: 0) {
            onScreenChange()
        }
    }

    @AllThreadsAllowed
    fun useRenderPipeline(renderable: Renderable) {
        currentRenderable = renderable
        renderable.init()
    }

    fun newRun() {
        PermaSaveState.newRun()
        SaveState.reset()
        MapManager.newRunSync()
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
//        newRun()
        PermaSaveState.read()
        SaveState.read()
        MapManager.read()
        GraphicsConfig.init()
        ResourceManager.init()
        RandomCardSelection.init()
//        val cards = OnjParser.parseFile(Gdx.files.internal("config/cards.onj").file()) as OnjObject
//        println(cards.get<OnjArray>("cards").value.size)
    }

    override fun dispose() {
        FortyFiveLogger.debug(logTag, "game closing")
        MapManager.write()
        PermaSaveState.write()
        SaveState.write()
        currentScreen?.dispose()
        serviceThread.close()
        ResourceManager.end()
        super.dispose()
    }
}