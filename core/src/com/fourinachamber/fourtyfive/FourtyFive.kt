package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.card.CardGenerator
import com.fourinachamber.fourtyfive.game.Effect
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.game.OnjExtensions
import com.fourinachamber.fourtyfive.game.StatusEffect
import com.fourinachamber.fourtyfive.game.enemy.EnemyAction
import com.fourinachamber.fourtyfive.screen.ScreenBuilderFromOnj
import onj.OnjObject
import onj.OnjParser
import onj.OnjSchemaParser

/**
 * main game object
 */
object FourtyFive : Game() {

    /**
     * setting this variable will change the current screen and dispose the previous
     */
    var curScreen: Screen? = null
        set(value) {
            field?.dispose()
            field = value
            setScreen(field)
        }

    private lateinit var menuScreen: Screen
    private lateinit var gameScreen: Screen


    override fun create() {
        init()
        val cardGenerator = CardGenerator(Gdx.files.internal("cards/card_generator_config.onj"))
        cardGenerator.prepare()
        cardGenerator.generateCards()
        menuScreen = ScreenBuilderFromOnj(Gdx.files.internal("screens/game_screen_v2.onj")).build()

        curScreen = menuScreen
    }

    private fun init() {
        OnjExtensions.init()

        val graphicsConfig =
            OnjParser.parseFile(Gdx.files.internal("config/graphics_config.onj").file())
        val animationConfigSchema =
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/graphics_config.onjschema").file())

        animationConfigSchema.assertMatches(graphicsConfig)

        graphicsConfig as OnjObject

        GameScreenController.init(graphicsConfig)
        EnemyAction.init(graphicsConfig)
        Effect.init(graphicsConfig)
        Card.init(graphicsConfig)
        StatusEffect.init(graphicsConfig)
    }

    override fun dispose() {
        menuScreen.dispose()
    }
}