package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.game.*
import com.fourinachamber.fourtyfive.game.card.CardGenerator
import com.fourinachamber.fourtyfive.map.*
import com.fourinachamber.fourtyfive.onjNamespaces.CardsNamespace
import com.fourinachamber.fourtyfive.onjNamespaces.CommonNamespace
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ScreenBuilder
import com.fourinachamber.fourtyfive.onjNamespaces.ScreenNamespace
import com.fourinachamber.fourtyfive.onjNamespaces.StyleNamespace
import com.fourinachamber.fourtyfive.rendering.Renderable
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
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

        // TODO: this code below is just for testing

        val mapScreen = ScreenBuilder(Gdx.files.internal("screens/map_test.onj")).build()

        val mapWidget = mapScreen.namedActorOrError("map") as DetailMapWidget

        mapWidget.setMap(GameMap(SeededMapGenerator.generateDef().build()))

        changeToScreen(mapScreen)

//        val screen = ScreenBuilder(Gdx.files.internal("screens/intro_screen.onj")).build()
//        changeToScreen(screen)
    }

    // TODO: just for testing
    fun createTestingMap(): MapNode {
        val firstBuilder = MapNodeBuilder(40f, 70f, mutableListOf(), false)
        val secondBuilder = MapNodeBuilder(80f, 70f, mutableListOf(), false)
        firstBuilder.edgesTo.add(secondBuilder)
        secondBuilder.edgesTo.add(firstBuilder)
//        val map = MapNode(listOf(
//            MapNode(listOf(), false, 40f, 70f),
//            MapNode(listOf(
//                MapNode(listOf(
//                    MapNode(listOf(
//                        MapNode(listOf(
//                            MapNode(listOf(), false, 240f, 30f)
//                        ), false, 200f, 30f)
//                    ), false, 120f, 30f)
//                ), false, 80f, 30f)
//            ), false, 40f, 30f)
//        ), false, 10f, 60f)
        return firstBuilder.build()
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