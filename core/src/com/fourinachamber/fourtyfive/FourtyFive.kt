package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.game.*
import com.fourinachamber.fourtyfive.game.card.CardGenerator
import com.fourinachamber.fourtyfive.map.DetailMapWidget
import com.fourinachamber.fourtyfive.onjNamespaces.CardsNamespace
import com.fourinachamber.fourtyfive.onjNamespaces.CommonNamespace
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ScreenBuilder
import com.fourinachamber.fourtyfive.onjNamespaces.ScreenNamespace
import com.fourinachamber.fourtyfive.onjNamespaces.StyleNamespace
import com.fourinachamber.fourtyfive.rendering.BetterShaderPreProcessor
import com.fourinachamber.fourtyfive.rendering.Renderable
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.MainThreadOnly
import com.fourinachamber.fourtyfive.utils.TemplateString
import onj.customization.OnjConfig

/**
 * main game object
 */
object FourtyFive : Game() {

    const val generateCards: Boolean = false

    const val logTag = "fourty-five"

    private var currentRenderable: Renderable? = null
    private var currentScreen: OnjScreen? = null

    var currentGame: GameController? = null

    override fun create() {
        init()

        if (generateCards) runCardGenerator()

        val mapScreen = ScreenBuilder(Gdx.files.internal("screens/map_test.onj")).build()
        val map = mapScreen.namedActorOrError("map") as DetailMapWidget
        changeToScreen(mapScreen)

//        val screen = ScreenBuilder(Gdx.files.internal("screens/intro_screen.onj")).build()
//        changeToScreen(screen)
    }

    override fun render() {
        currentRenderable?.render(Gdx.graphics.deltaTime)
    }

    private fun runCardGenerator() {
        val cardGenerator = CardGenerator(Gdx.files.internal("cards/card_generator_config.onj"))
        cardGenerator.prepare()
        cardGenerator.generateCards()
    }

    fun changeToScreen(screen: OnjScreen) {
        FourtyFiveLogger.title("changing screen")
        currentScreen?.dispose()
        currentScreen = screen
        currentRenderable = screen
        setScreen(screen)
    }

    fun useRenderPipeline(renderable: Renderable) {
        currentRenderable = renderable
    }

    private fun init() {
        with(OnjConfig) {
            registerNameSpace("Common", CommonNamespace)
            registerNameSpace("Cards", CardsNamespace)
            registerNameSpace("Style", StyleNamespace)
            registerNameSpace("Screen", ScreenNamespace)
        }
        FourtyFiveLogger.init()
        SaveState.read()
        GraphicsConfig.init()
        ResourceManager.init()
    }

    override fun dispose() {
        FourtyFiveLogger.medium(logTag, "game closing")
        SaveState.write()
        currentScreen?.dispose()
        ResourceManager.end()
        super.dispose()
    }

}