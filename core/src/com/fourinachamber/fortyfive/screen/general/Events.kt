package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event
import com.fourinachamber.fortyfive.utils.MainThreadOnly

/**
 * This event gets fired when an actor was clicked using the mouse or using the keyboard
 */
class ButtonClickEvent : Event()

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
