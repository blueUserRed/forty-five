package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.game.*
import com.fourinachamber.fourtyfive.game.card.CardGenerator
import com.fourinachamber.fourtyfive.onjNamespaces.CardsNamespace
import com.fourinachamber.fourtyfive.onjNamespaces.CommonNamespace
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ScreenBuilder
import com.fourinachamber.fourtyfive.onjNamespaces.ScreenNamespace
import com.fourinachamber.fourtyfive.onjNamespaces.StyleNamespace
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.TemplateString
import onj.customization.OnjConfig

/**
 * main game object
 */
object FourtyFive : Game() {

    const val logTag = "fourty-five"

    private var currentScreen: OnjScreen? = null

    var currentGame: GameController? = null

    override fun create() {
        init()
        val cardGenerator = CardGenerator(Gdx.files.internal("cards/card_generator_config.onj"))
        cardGenerator.prepare()
        cardGenerator.generateCards()
        val screen = ScreenBuilder(Gdx.files.internal("screens/intro_screen.onj")).build()
        changeToScreen(screen)
//        curScreen = ScreenBuilderFromOnj(Gdx.files.internal("screens/intro_screen.onj")).build()
    }

    fun changeToScreen(screen: OnjScreen) {
        FourtyFiveLogger.title("changing screen")
        currentScreen?.dispose()
        currentScreen = screen
        setScreen(screen)
    }

//    override fun render() {
//        super.render()
//    }

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