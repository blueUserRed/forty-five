package com.microwavestudios.fortyfive.screen

import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.microwavestudios.fortyfive.keyInput.InputManager

fun interface InputProcessorProvider {

    fun getInputProcessor(stage: Stage, inputManager: InputManager): InputProcessor

    companion object {
        val default = InputProcessorProvider { stage, inputManager ->
            InputMultiplexer(stage, inputManager)
        }
    }
}
