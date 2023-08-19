package com.fourinachamber.fortyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.utils.TextureGenerator
import com.fourinachamber.fortyfive.map.*
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.onjNamespaces.*
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenBuilder
import com.fourinachamber.fortyfive.rendering.Renderable
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.utils.*
import onj.customization.OnjConfig

/**
 * main game object
 */
object FortyFive : Game() {

    const val generateCards: Boolean = false
    const val generateWorldViewBackground: Boolean = false

    const val logTag = "forty-five"

    private var currentRenderable: Renderable? = null
    private var currentScreen: OnjScreen? = null

    var currentGame: GameController? = null

    val serviceThread: ServiceThread = ServiceThread()

    var cleanExit: Boolean = true

    override fun create() {
        init()
        serviceThread.start()
        if (generateCards) runCardGenerator()
        if (generateWorldViewBackground) runWorldViewBackgroundGenerator()
        changeToScreen("screens/map_screen.onj")
    }

    override fun render() {
        currentRenderable?.render(Gdx.graphics.deltaTime)
    }

    private fun runCardGenerator() {
        val cardGenerator = TextureGenerator(Gdx.files.internal("cards/card_generator_config.onj"))
        cardGenerator.prepare()
        cardGenerator.generate()
    }

    private fun runWorldViewBackgroundGenerator() {
        val textureGenerator = TextureGenerator(Gdx.files.internal("maps/world_view/background_generator_config.onj"))
        textureGenerator.prepare()
        textureGenerator.generate()
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
        MapManager.init() // re-read and reset things like the current map
    }

    fun resetAll() {
        PermaSaveState.reset()
        SaveState.reset()
        MapManager.resetAllSync()
    }

    private fun init() {
        with(OnjConfig) {
            registerNameSpace("Common", CommonNamespace)
            registerNameSpace("Cards", CardsNamespace)
            registerNameSpace("Style", StyleNamespace)
            registerNameSpace("Screen", ScreenNamespace)
            registerNameSpace("Map", MapNamespace)
        }
        TemplateString.init()
        FortyFiveLogger.init()
        resetAll()
        newRun()
        PermaSaveState.read()
        SaveState.read()
        MapManager.init()
        GraphicsConfig.init()
        ResourceManager.init()
        RandomCardSelection.init()
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