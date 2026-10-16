package com.microwavestudios.fortyfive.testing

import com.badlogic.gdx.ApplicationListener
import com.microwavestudios.fortyfive.utils.Timeline

abstract class FullGameTest : ApplicationListener {

    abstract val name: String

    private val testTimeline: Timeline = Timeline().also { it.startTimeline() }


    override fun render() {
        testTimeline.updateTimeline()
    }

    abstract fun testTimeline(): Timeline


    override fun create() {
    }

    override fun resize(width: Int, height: Int) {
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override fun dispose() {
    }
}
