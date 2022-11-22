package com.fourinachamber.fourtyfive

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.fourinachamber.fourtyfive.screen.ScreenBuilderFromOnj

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
//        val cardGenerator = CardGenerator(Gdx.files.internal("cards/card_generator_config.onj"))
//        cardGenerator.prepare()
//        cardGenerator.generateCards()
        menuScreen = ScreenBuilderFromOnj(Gdx.files.internal("screens/title_screen.onj")).build()
        gameScreen = ScreenBuilderFromOnj(Gdx.files.internal("screens/game_screen.onj")).build()
        curScreen = menuScreen
    }

    override fun dispose() {
        menuScreen.dispose()
    }
}