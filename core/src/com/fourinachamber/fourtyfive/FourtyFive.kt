package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.fourinachamber.fourtyfive.cards.CardGenerator
import com.fourinachamber.fourtyfive.screens.ScreenBuilderFromOnj

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
        val cardGenerator = CardGenerator(Gdx.files.internal("cards/card_generator_config.onj"))
        cardGenerator.prepare()
        cardGenerator.generateCards()
        menuScreen = ScreenBuilderFromOnj(Gdx.files.internal("screens/game_screen.onj")).build()
        curScreen = menuScreen
    }

    override fun dispose() {
        menuScreen.dispose()
    }
}