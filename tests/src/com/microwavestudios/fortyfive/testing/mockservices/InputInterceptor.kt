package com.microwavestudios.fortyfive.testing.mockservices

import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor

class InputInterceptor : InputProcessor {

    private val multiplexer: InputMultiplexer = InputMultiplexer()

    val artificialInputSender: InputProcessor = object : InputProcessor by multiplexer {}

    fun intercept(vararg processors: InputProcessor): InputProcessor {
        processors.forEach { processor ->
            multiplexer.addProcessor(processor)
        }
        return this
    }

    override fun keyDown(keycode: Int): Boolean = false

    override fun keyUp(keycode: Int): Boolean = false

    override fun keyTyped(character: Char): Boolean = false

    override fun touchDown(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean = false

    override fun touchUp(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean = false

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = false

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false

    override fun scrolled(amountX: Float, amountY: Float): Boolean = false
}
