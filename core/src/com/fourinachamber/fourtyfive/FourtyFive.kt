package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.fourinachamber.fourtyfive.card.CardGenerator
import com.fourinachamber.fourtyfive.game.Effect
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.game.OnjExtensions
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

    override fun create() {
        init()
//        val cardGenerator = CardGenerator(Gdx.files.internal("cards/card_generator_config.onj"))
//        cardGenerator.prepare()
//        cardGenerator.generateCards()
        menuScreen = ScreenBuilderFromOnj(Gdx.files.internal("screens/game_screen.onj")).build()
        curScreen = menuScreen
    }

    private fun init() {
        OnjExtensions.init()

        val animationConfig =
            OnjParser.parseFile(Gdx.files.internal("config/animation_config.onj").file())
        val animationConfigSchema =
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/animation_config.onjschema").file())

        animationConfigSchema.assertMatches(animationConfig)

        animationConfig as OnjObject

        GameScreenController.init(animationConfig)
        EnemyAction.init(animationConfig)
        Effect.init(animationConfig)
    }

    override fun dispose() {
        menuScreen.dispose()
    }
}