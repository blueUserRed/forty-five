package com.fourinachamber.fourtyfive.screens

import onj.OnjNamedObject
import onj.OnjObject

object ScreenControllerFactory {

    private val controllers: MutableMap<String, (OnjObject) -> ScreenController> = mutableMapOf(
        "GameScreenController" to { onj -> GameScreenController() }
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
     * called before the controller is changed to different one
     */
    open fun end() { }

}

/**
 * the Controller for the main game screen
 */
class GameScreenController : ScreenController() {

    private var curScreen: ScreenDataProvider? = null

    override fun init(screenDataProvider: ScreenDataProvider) {
        curScreen = screenDataProvider
    }

    override fun end() {
        curScreen = null
    }
}
