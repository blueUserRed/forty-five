package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.game.*
import com.fourinachamber.fourtyfive.game.enemy.Enemy
import com.fourinachamber.fourtyfive.game.enemy.EnemyAction
import com.fourinachamber.fourtyfive.onjNamespaces.CardsNamespace
import com.fourinachamber.fourtyfive.onjNamespaces.CommonNamespace
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ScreenBuilderFromOnj
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import onj.customization.OnjConfig
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.value.OnjObject

/**
 * main game object
 */
object FourtyFive : Game() {

    const val logTag = "fourty-five"

    /**
     * setting this variable will change the current screen and dispose the previous
     */
    var curScreen: OnjScreen? = null
        set(value) {
            FourtyFiveLogger.title("changing screen")
            field?.dispose()
            field = value
            setScreen(field)
        }


    var currentGame: GameController? = null

    override fun create() {
        init()
//        val cardGenerator = CardGenerator(Gdx.files.internal("cards/card_generator_config.onj"))
//        cardGenerator.prepare()
//        cardGenerator.generateCards()
        curScreen = ScreenBuilderFromOnj(Gdx.files.internal("screens/intro_screen.onj")).build()
    }

    private fun init() {
        OnjConfig.registerNameSpace("Common", CommonNamespace)
        OnjConfig.registerNameSpace("Cards", CardsNamespace)
        FourtyFiveLogger.init()
        SaveState.read()
        GraphicsConfig.init()
    }

    override fun dispose() {
        FourtyFiveLogger.medium(logTag, "game closing")
        SaveState.write()
        super.dispose()
    }

}