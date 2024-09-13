package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event
import com.fourinachamber.fortyfive.screen.general.customActor.DisplayDetailActor
import com.fourinachamber.fortyfive.screen.general.customActor.KotlinStyledActor
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
        "TutorialConfirmedEvent" to { TutorialConfirmedEvent() },
        "HoverEnterEvent" to { HoverEnterEvent() },
        "HoverLeaveEvent" to { HoverLeaveEvent() },
        "FocusChangeEvent" to { HoverLeaveEvent() },
        "QuitGameEvent" to { QuitGameEvent() },
        "AbandonRunEvent" to { AbandonRunEvent() },
        "ResetGameEvent" to { ResetGameEvent() },
    )

    private val eventClasses: Map<String, KClass<out Event>> = mapOf(
        "ButtonClickEvent" to ButtonClickEvent::class,
        "ShootRevolverEvent" to ShootRevolverEvent::class,
        "EndTurnEvent" to EndTurnEvent::class,
        "DrawCardEvent" to DrawCardEvent::class,
        "PopupConfirmationEvent" to PopupConfirmationEvent::class,
        "ParryEvent" to ParryEvent::class,
        "TutorialConfirmedEvent" to TutorialConfirmedEvent::class,
        "HoverEnterEvent" to HoverEnterEvent::class,
        "HoverLeaveEvent" to HoverLeaveEvent::class,
        "FocusChangeEvent" to HoverLeaveEvent::class,
        "QuitGameEvent" to QuitGameEvent::class,
        "AbandonRunEvent" to AbandonRunEvent::class,
        "ResetGameEvent" to ResetGameEvent::class,
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

class TutorialConfirmedEvent : Event()

class QuitGameEvent : Event()
class AbandonRunEvent : Event()
class ResetGameEvent : Event()

class FocusChangeEvent(val old: Actor?, val new: Actor?, val fromMouse: Boolean = true) : Event()
class SelectChangeEvent(val old: List<Actor>, val new: List<Actor>, val fromMouse: Boolean = true) : Event()

class DetailDisplayStateChange(val displayStarted: Boolean = true) : Event()

/**
 * used by the [GameController][com.fourinachamber.fortyfive.game.GameController] so it knows when the player confirmed
 * a popup
 */
class PopupSelectionEvent(val cardNum: Int) : Event()

/**
 * binds a listener for the [ButtonClickEvent] to this actor
 */
inline fun Actor.onButtonClick(crossinline block: @MainThreadOnly (Event) -> Unit) {
    this.addListener { event ->
        if (event !is ButtonClickEvent) return@addListener false
        block(event)
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

inline fun Actor.onFocusChange(crossinline block: (Actor?, Actor?) -> Unit) {
    this.addListener { event ->
        if (event !is FocusChangeEvent) return@addListener false
        if (this is KotlinStyledActor) {
            if (!this.isFocusable) throw RuntimeException("You tried to focus an unfocusable Element") // this should never happen
            isFocused = this == event.new
        }
        block(event.old, event.new)
        true
    }
}
inline fun Actor.onFocus(crossinline block: (Boolean) -> Unit) {
    this.addListener { event ->
        if (event !is FocusChangeEvent) return@addListener false
        if (this is KotlinStyledActor) {
            if (!this.isFocusable) throw RuntimeException("You tried to focus an unfocusable Element") // this should never happen
            isFocused = this == event.new
        }
        block(event.fromMouse)
        true
    }
}

inline fun Actor.onSelectChange(crossinline block: @MainThreadOnly (List<Actor>, List<Actor>) -> Unit) {
    this.addListener { event ->
        if (event !is SelectChangeEvent) return@addListener false
        if (this is KotlinStyledActor) {
//            if (!this.isTouchable) throw RuntimeException("You tried to select an unTouchable Element") // this should never happen
//            this.touchable = if (this in event.new) Touchable.enabled else Touchable.childrenOnly
            this.isSelected = this in event.new
        }
        block(event.old, event.new)
        true
    }
}

inline fun Actor.onSelectChange(crossinline block: @MainThreadOnly (List<Actor>, List<Actor>, Boolean) -> Unit) {
    this.addListener { event ->
        if (event !is SelectChangeEvent) return@addListener false
        if (this is KotlinStyledActor) {
//            if (!this.isTouchable) throw RuntimeException("You tried to select an unTouchable Element") // this should never happen
//            this.touchable = if (this in event.new) Touchable.enabled else Touchable.childrenOnly
            this.isSelected = this in event.new
        }
        block(event.old, event.new, event.fromMouse)
        true
    }
}

inline fun Actor.onSelect(crossinline block: @MainThreadOnly () -> Unit) {
    this.addListener { event ->
        if (event !is SelectChangeEvent) return@addListener false
        if (this is KotlinStyledActor) {
//            if (!this.isTouchable) throw RuntimeException("You tried to select an unTouchable Element") // this should never happen
//            this.touchable = if (this in event.new) Touchable.enabled else Touchable.childrenOnly
            this.isSelected = this in event.new
            if (isSelected) {
                block()
                return@addListener true
            }
        }
        false
    }
}

inline fun <T> T.onDetailDisplayStateChange(crossinline block: @MainThreadOnly (Boolean) -> Unit) where T : DisplayDetailActor, T : Actor {
    this.addListener { event ->
        if (event !is DetailDisplayStateChange) return@addListener false
        block(event.displayStarted)
        true
    }
}
