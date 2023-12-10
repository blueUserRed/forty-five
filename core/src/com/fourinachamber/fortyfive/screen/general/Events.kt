package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import kotlin.reflect.KClass

object EventFactory {

    private val eventCreators: Map<String, () -> Event> = mapOf(
        "ButtonClickEvent" to { ButtonClickEvent() },
        "ShootRevolverEvent" to { ShootRevolverEvent() },
        "EndTurnEvent" to { EndTurnEvent() },
        "DrawCardEvent" to { DrawCardEvent() },
        "PopupConfirmationEvent" to { PopupConfirmationEvent() },
        "ParryEvent" to { ParryEvent() },
        "HoverEnterEvent" to { HoverEnterEvent() },
        "HoverLeaveEvent" to { HoverLeaveEvent() },
    )

    private val eventClasses: Map<String, KClass<out Event>> = mapOf(
        "ButtonClickEvent" to ButtonClickEvent::class,
        "ShootRevolverEvent" to ShootRevolverEvent::class,
        "EndTurnEvent" to EndTurnEvent::class,
        "DrawCardEvent" to DrawCardEvent::class,
        "PopupConfirmationEvent" to PopupConfirmationEvent::class,
        "ParryEvent" to ParryEvent::class,
        "HoverEnterEvent" to HoverEnterEvent::class,
        "HoverLeaveEvent" to HoverLeaveEvent::class,
    )

    fun createEvent(name: String): Event = eventCreators[name]?.invoke()
        ?: throw RuntimeException("unknown event $name")

    fun eventClass(name: String): KClass<out Event> = eventClasses[name]
        ?: throw RuntimeException("unknown event $name")

}

/**
 * This event gets fired when an actor was clicked using the mouse or using the keyboard
 */
class ButtonClickEvent : Event()

class HoverEnterEvent : Event()

class HoverLeaveEvent : Event()

/**
 * used by the [GameController][com.fourinachamber.fortyfive.game.GameController] so it knows when to shoot
 */
class ShootRevolverEvent : Event()

/**
 * used by the [GameController][com.fourinachamber.fortyfive.game.GameController] so it knows when the player wants to
 * end the turn
 */
class EndTurnEvent : Event()

/**
 * used by the [GameController][com.fourinachamber.fortyfive.game.GameController] so it knows when the player wants to
 * draw a card
 */
class DrawCardEvent : Event()

/**
 * used by the [GameController][com.fourinachamber.fortyfive.game.GameController] so it knows when the player confirmed
 * a popup
 */
class PopupConfirmationEvent : Event()

class ParryEvent : Event()

/**
 * used by the [GameController][com.fourinachamber.fortyfive.game.GameController] so it knows when the player confirmed
 * a popup
 */
class PopupSelectionEvent(val cardNum: Int) : Event()

/**
 * binds a listener for the [ButtonClickEvent] to this actor
 */
inline fun Actor.onButtonClick(crossinline block: @MainThreadOnly () -> Unit) {
    this.addListener { event ->
        if (event !is ButtonClickEvent) return@addListener false
        block()
        true
    }
}

/**
 * binds a listener for the [ButtonClickEvent] to this actor
 */
inline fun Actor.onHoverEnter(crossinline block: @MainThreadOnly () -> Unit) {
    this.addListener { event ->
        if (event !is HoverEnterEvent) return@addListener false
        block()
        true
    }
}

/**
 * binds a listener for the [ButtonClickEvent] to this actor
 */
inline fun Actor.onHoverLeave(crossinline block: @MainThreadOnly () -> Unit) {
    this.addListener { event ->
        if (event !is HoverLeaveEvent) return@addListener false
        block()
        true
    }
}
