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
import com.fourinachamber.fortyfive.map.statusbar.CardCollectionScreenController
import com.fourinachamber.fortyfive.screen.gameComponents.*
import com.fourinachamber.fortyfive.utils.AllThreadsAllowed
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import onj.value.OnjNamedObject

object ScreenControllerFactory {

    private val controllers: MutableMap<String, (OnjNamedObject, OnjScreen) -> ScreenController> = mutableMapOf(
        "GameScreenController" to { onj, screen -> GameController(screen, onj) },
        "IntroScreenController" to { onj, screen -> IntroScreenController(screen, onj) },
        "DialogScreenController" to { onj, screen -> DialogScreenController(screen, onj) },
        "ShopScreenController" to { onj, screen -> ShopScreenController(screen, onj) },
        "ChooseCardScreenController" to { onj, screen -> ChooseCardScreenController(screen, onj) },
        "HealOrMaxHPScreenController" to { onj, screen -> HealOrMaxHPScreenController(screen, onj.get<String>("addLifeActorName")) },
        "AddMaxHPScreenController" to { _, screen -> AddMaxHPScreenController(screen) },
        "MapScreenController" to { _, screen -> MapScreenController(screen) },
        "StatsScreenController" to { _, screen -> StatsScreenController(screen) },
        "TitleScreenController" to { _, screen -> TitleScreenController(screen) },
        "CreditScreenController" to { _, screen -> CreditScreenController(screen) },
        "BiomeBackgroundScreenController" to { onj, screen -> BiomeBackgroundScreenController(screen, onj.get<Boolean>("useSecondary")) },
        "DraftScreenController" to { _, screen -> DraftScreenController(screen) },
        "FadeToBlackScreenController" to { onj, screen -> FadeToBlackScreenController(screen, onj) },
        "CardCollectionScreenController" to { onj, screen -> CardCollectionScreenController(screen, onj) },
    )

    /**
     * will return an instance of the ScreenController with name [name]
     * @throws RuntimeException when no controller with that name exists
     * @param onj the onjObject containing the configuration of the controller
     */
    @AllThreadsAllowed
    fun controllerOrError(name: String, screen: OnjScreen, onj: OnjNamedObject): ScreenController {
        val behaviourCreator = controllers[name] ?: throw RuntimeException("Unknown behaviour: $name")
        return behaviourCreator(onj, screen)
    }
}

/**
 * ScreenControllers can be used to add advanced functionality to a screen
 */
abstract class ScreenController {

    private val eventHandlers: MutableMap<String, (Event) -> Unit> = mutableMapOf()

    /**
     * called when this is set as a controller for a screen
     */
    @MainThreadOnly
    open fun init(context: Any?) { }

    open fun onShow() {}

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

    open fun onTransitionAway() { }

    fun initEventHandler() {
        val eventHandlers: Map<String, (Event) -> Unit> = this::class
            .java
            .methods
            .filter { it.isAnnotationPresent(EventHandler::class.java) }
            .associate { it.name to { event -> it.invoke(this, event, event.target) } }
        this.eventHandlers.putAll(eventHandlers)
    }

    fun injectActors(screen: OnjScreen) {
        this::class
            .java
            .declaredFields
            .filter { it.isAnnotationPresent(Inject::class.java) }
            .forEach { field ->
                val annotation = field.getAnnotation(Inject::class.java)
                val name = annotation.name.ifBlank { field.name }
                val actor = screen.namedActorOrNull(name) ?:
                    throw RuntimeException(
                        "tried to inject actor with name $name into field of ${this::class.simpleName} " +
                                "but no actor with that name was found"
                    )
                if (!field.type.isInstance(actor)) {
                    throw RuntimeException(
                        "tried to inject actor with name $name into field of ${this::class.simpleName}" +
                        "but type of field '${field.type.simpleName}' is not compatible with type of actor" +
                        " '${actor::class.simpleName}'"
                    )
                }
                field.isAccessible = true
                field.set(this, actor)
            }
    }

    fun handleEventListener(name: String, event: Event): Boolean {
        val handler = eventHandlers[name] ?: return false
        handler(event)
        return true
    }

}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class EventHandler

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Inject(val name: String = "")
