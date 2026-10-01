package com.microwavestudios.fortyfive.testing

import com.badlogic.gdx.ApplicationListener

class ApplicationListenerRelay(private val listeners: List<ApplicationListener>) : ApplicationListener {

    override fun create() {
        listeners.forEach(ApplicationListener::create)
    }

    override fun resize(width: Int, height: Int) {
        listeners.forEach { it.resize(width, height) }
    }

    override fun render() {
        listeners.forEach(ApplicationListener::render)
    }

    override fun pause() {
        listeners.forEach(ApplicationListener::pause)
    }

    override fun resume() {
        listeners.forEach(ApplicationListener::resume)
    }

    override fun dispose() {
        listeners.forEach(ApplicationListener::dispose)
    }

}
