package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.scenes.scene2d.Event
import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.StatsScreenController
import com.fourinachamber.fortyfive.map.detailMap.MapScreenController
import com.fourinachamber.fortyfive.map.events.chooseCard.ChooseCardScreenController
import com.fourinachamber.fortyfive.map.events.dialog.DialogScreenController
import com.fourinachamber.fortyfive.map.events.heals.AddMaxHPScreenController
import com.fourinachamber.fortyfive.map.events.heals.HealOrMaxHPScreenController
import com.fourinachamber.fortyfive.map.events.shop.ShopScreenController
import com.fourinachamber.fortyfive.screen.gameComponents.IntroScreenController
import com.fourinachamber.fortyfive.utils.AllThreadsAllowed
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import onj.value.OnjNamedObject

object ScreenControllerFactory {

    private val controllers: MutableMap<String, (OnjNamedObject) -> ScreenController> = mutableMapOf(
        "GameScreenController" to { onj -> GameController(onj) },
        "IntroScreenController" to { onj -> IntroScreenController(onj) },
        "DialogScreenController" to { onj -> DialogScreenController(onj) },
        "ShopScreenController" to { onj -> ShopScreenController(onj) },
        "ChooseCardScreenController" to { onj -> ChooseCardScreenController(onj) },
        "HealOrMaxHPScreenController" to { onj -> HealOrMaxHPScreenController(onj) },
        "AddMaxHPScreenController" to {onj -> AddMaxHPScreenController(onj) },
        "MapScreenController" to { onj -> MapScreenController(onj) },
        "StatsScreenController" to { StatsScreenController() },
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
    open fun init(onjScreen: OnjScreen, context: Any?) { }

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

    /**
     * called when an event managed to bubble all the way up to root
     */
    @MainThreadOnly
    open fun onUnhandledEvent(event: Event) { }

}
