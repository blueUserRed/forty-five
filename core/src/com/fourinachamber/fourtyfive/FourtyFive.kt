package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.game.*
import com.fourinachamber.fourtyfive.game.enemy.Enemy
import com.fourinachamber.fourtyfive.game.enemy.EnemyAction
import com.fourinachamber.fourtyfive.onjNamespaces.OnjExtensions
import com.fourinachamber.fourtyfive.screen.ScreenBuilderFromOnj
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
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
    var curScreen: Screen? = null
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
        OnjExtensions.init()
        FourtyFiveLogger.init()
        SaveState.read()

        val graphicsConfig =
            OnjParser.parseFile(Gdx.files.internal("config/graphics_config.onj").file())
        val animationConfigSchema =
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/graphics_config.onjschema").file())

        animationConfigSchema.assertMatches(graphicsConfig)

        graphicsConfig as OnjObject

        GameController.init(graphicsConfig)
        EnemyAction.init(graphicsConfig)
        Effect.init(graphicsConfig)
        Card.init(graphicsConfig)
        StatusEffect.init(graphicsConfig)
        Enemy.init(graphicsConfig)
    }

    override fun dispose() {
        FourtyFiveLogger.medium(logTag, "game closing")
        SaveState.write()
        super.dispose()
    }

}