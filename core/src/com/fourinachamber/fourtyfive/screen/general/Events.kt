package com.fourinachamber.fourtyfive.screen.general

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event

//TODO: there are a few problems that could be solved using custom events

class ButtonClickEvent : Event()

inline fun Actor.onButtonClick(crossinline block: () -> Unit) {
    this.addListener { event ->
        if (event !is ButtonClickEvent) return@addListener false
        block()
        true
    }
}
