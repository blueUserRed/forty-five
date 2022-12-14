package com.fourinachamber.fourtyfive.screen

import com.fourinachamber.fourtyfive.game.CardSelectionScreenController
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.game.IntroScreenController
import onj.*

object ScreenControllerFactory {

    private val controllers: MutableMap<String, (OnjNamedObject) -> ScreenController> = mutableMapOf(
        "GameScreenController" to { onj -> GameScreenController(onj) },
        "CardSelectionScreenController" to { onj -> CardSelectionScreenController(onj) },
        "IntroScreenController" to { onj -> IntroScreenController(onj) }
    )

    /**
     * will return an instance of the ScreenController with name [name]
     * @throws RuntimeException when no controller with that name exists
     * @param onj the onjObject containing the configuration of the controller
     */
    fun controllerOrError(name: String, onj: OnjNamedObject): ScreenController {
        val behaviourCreator = controllers[name] ?: throw RuntimeException("Unknown behaviour: $name")
        return behaviourCreator(onj)
    }
}

/**
 * ScreenControllers can be used to add advanced functionality to a screen
 */
abstract class ScreenController {

    /**
     * called when this is set as a controller for a screen
     */
    open fun init(screenDataProvider: ScreenDataProvider) { }

    /**
     * called every frame
     */
    open fun update() { }

    /**
     * called before the controller is changed to different one
     */
    open fun end() { }

}
