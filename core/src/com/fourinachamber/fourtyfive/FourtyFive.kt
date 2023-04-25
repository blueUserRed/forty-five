package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.game.*
import com.fourinachamber.fourtyfive.game.card.CardGenerator
import com.fourinachamber.fourtyfive.map.*
import com.fourinachamber.fourtyfive.map.detailMap.MapEventFactory
import com.fourinachamber.fourtyfive.onjNamespaces.CardsNamespace
import com.fourinachamber.fourtyfive.onjNamespaces.CommonNamespace
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ScreenBuilder
import com.fourinachamber.fourtyfive.onjNamespaces.ScreenNamespace
import com.fourinachamber.fourtyfive.onjNamespaces.StyleNamespace
import com.fourinachamber.fourtyfive.rendering.Renderable
import com.fourinachamber.fourtyfive.screen.Resource
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import onj.customization.OnjConfig
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

/**
 * main game object
 */
object FourtyFive : Game() {

    const val generateCards: Boolean = false

    const val logTag = "fourty-five"

    private var currentRenderable: Renderable? = null
    private var currentScreen: OnjScreen? = null

    var currentGame: GameController? = null

    val serviceThread: ServiceThread = ServiceThread()

    override fun create() {
        init()
        serviceThread.start()
        if (generateCards) runCardGenerator()
        val t1 = measureTimeMillis {
            val message = ServiceThreadMessage.GenerateMaps()
            serviceThread.sendMessage(message)
            runBlocking {
                message.completed.await()
            }
        }
        val t2 = measureTimeMillis {
            MapManager.generateMapsSync()
        }
        println("t1: $t1, t2: $t2")
//        changeToScreen("screens/dialog_test.onj")
    }

    override fun render() {
        currentRenderable?.render(Gdx.graphics.deltaTime)
    }

    private fun runCardGenerator() {
        val cardGenerator = CardGenerator(Gdx.files.internal("cards/card_generator_config.onj"))
        cardGenerator.prepare()
        cardGenerator.generateCards()
    }

    fun changeToScreen(screenPath: String, controllerContext: Any? = null) {
        val currentScreen = currentScreen
        if (currentScreen?.transitionAwayTime != null) currentScreen.transitionAway()
        val screen = ScreenBuilder(Gdx.files.internal(screenPath)).build(controllerContext)

        serviceThread.sendMessage(ServiceThreadMessage.PrepareResources)

        if (currentScreen == null) {
            FourtyFiveLogger.title("changing screen")
            this.currentScreen = screen
            currentRenderable = screen
            setScreen(screen)
        } else {
            currentScreen.afterMs(currentScreen.transitionAwayTime ?: 0) {
                FourtyFiveLogger.title("changing screen")
                currentScreen.dispose()
                this.currentScreen = screen
                currentRenderable = screen
                setScreen(screen)
            }
        }
    }

    @AllThreadsAllowed
    fun useRenderPipeline(renderable: Renderable) {
        currentRenderable = renderable
    }

//    fun newRun() {
//        SaveState.reset()
//        MapManager.newRun()
//    }

    private fun init() {
        with(OnjConfig) {
            registerNameSpace("Common", CommonNamespace)
            registerNameSpace("Cards", CardsNamespace)
            registerNameSpace("Style", StyleNamespace)
            registerNameSpace("Screen", ScreenNamespace)
        }
        TemplateString.init()
        FourtyFiveLogger.init()
//        newRun()
        SaveState.read()
        MapManager.init()
        GraphicsConfig.init()
        ResourceManager.init()
    }

    override fun dispose() {
        FourtyFiveLogger.medium(logTag, "game closing")
        MapManager.write()
        SaveState.write()
        currentScreen?.dispose()
        serviceThread.close()
        ResourceManager.end()
        super.dispose()
    }

}