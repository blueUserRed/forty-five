package com.fourinachamber.fourtyfive.screen.general

import com.fourinachamber.fourtyfive.game.GameController
import com.fourinachamber.fourtyfive.screen.gameComponents.CardSelectionScreenController
import com.fourinachamber.fourtyfive.screen.gameComponents.IntroScreenController
import com.fourinachamber.fourtyfive.utils.AllThreadsAllowed
import com.fourinachamber.fourtyfive.utils.MainThreadOnly
import onj.value.OnjNamedObject

object ScreenControllerFactory {

    private val controllers: MutableMap<String, (OnjNamedObject) -> ScreenController> = mutableMapOf(
        "GameScreenController" to { onj -> GameController(onj) },
        "CardSelectionScreenController" to { onj -> CardSelectionScreenController(onj) },
        "IntroScreenController" to { onj -> IntroScreenController(onj) }
    )

    /**
     * will return an instance of the ScreenController with name [name]
     * @throws RuntimeException when no controller with that name exists
     * @param onj the onjObject containing the configuration of the controller
     */
    @AllThreadsAllowed
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
    @MainThreadOnly
    open fun init(onjScreen: OnjScreen) { }

    /**
     * called every frame
     */
    @MainThreadOnly
    open fun update() { }

    /**
     * called before the controller is changed to different one
     */
    @MainThreadOnly
    open fun end() { }

}
