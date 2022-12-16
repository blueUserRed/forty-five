package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Application
import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.card.CardGenerator
import com.fourinachamber.fourtyfive.game.*
import com.fourinachamber.fourtyfive.game.enemy.Enemy
import com.fourinachamber.fourtyfive.game.enemy.EnemyAction
import com.fourinachamber.fourtyfive.screen.ScreenBuilderFromOnj
import onj.OnjObject
import onj.OnjParser
import onj.OnjParserException
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

//    private lateinit var gameScreen: Screen


    override fun create() {
        init()
        Gdx.app.logLevel = Application.LOG_DEBUG
        val cardGenerator = CardGenerator(Gdx.files.internal("cards/card_generator_config.onj"))
        cardGenerator.prepare()
        cardGenerator.generateCards()
        curScreen = ScreenBuilderFromOnj(Gdx.files.internal("screens/intro_screen.onj")).build()
    }

    private fun init() {
        OnjExtensions.init()
        SaveState.read()

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
        Enemy.init(graphicsConfig)
    }

}