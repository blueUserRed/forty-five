package com.fourinachamber.fourtyfive.screen.general

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event
import com.fourinachamber.fourtyfive.utils.MainThreadOnly

//TODO: there are a few problems that could be solved using custom events

class ButtonClickEvent : Event()

inline fun Actor.onButtonClick(crossinline block: @MainThreadOnly () -> Unit) {
    this.addListener { event ->
        if (event !is ButtonClickEvent) return@addListener false
        block()
        true
    }
}
