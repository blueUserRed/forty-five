package com.microwavestudios.fortyfive.screen

/**
 * ScreenControllers can be used to add advanced functionality to a screen
 */
abstract class ScreenController {

    open fun preInit(context: Any?) {}

    /**
     * called when this is set as a controller for a screen
     */
    open fun init(context: Any?) { }

    open fun onShow() {}

    open fun onActive() {}

    /**
     * called every frame
     */
    open fun update() { }

    /**
     * called before the controller is changed to different one
     */
    open fun end() { }

    open fun onTransitionAway() { }

    fun injectActors(screen: RenderableScreen) {
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

}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Inject(val name: String = "")
