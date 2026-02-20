package com.microwavestudios.fortyfive.keyInput

import com.badlogic.gdx.controllers.ControllerMapping
import com.badlogic.gdx.Input.Keys

class Input(val name: String, val causes: Array<Cause>) {

    override fun toString(): String = "Input($name)"

    override fun equals(other: Any?): Boolean = other is Input && other.name == name

    sealed class Cause {

        class Keyboard(
            val primaryKey: KeyCode,
            val modifierKeys: Array<ModifierKey> = arrayOf(),
            val requireStates: Array<InputState> = arrayOf()
        ) : Cause()

        class Mouse(val button: MouseButton, val requireDirectHit: Boolean = true) : Cause()

        class ControllerButtonBased(
            val button: ControllerButton,
            val requireStates: Array<InputState> = arrayOf()
        ) : Cause()
    }

}

fun InputState(name: String, causedByStates: Array<InputState>): InputState = InputState(
    name, causedByStates.map { arrayOf(it) }.toTypedArray()
)

class InputState(val name: String, val causedByStates: Array<Array<InputState>> = arrayOf()) {

    override fun toString(): String = "InputState($name)"
}

enum class MouseButton(val code: Int) {
    LEFT(0), RIGHT(1), MIDDLE(2), BACK(3), FORWARD(4)
}

enum class ModifierKey(val codes: Array<KeyCode>) {
    SHIFT(arrayOf(Keys.SHIFT_LEFT, Keys.SHIFT_RIGHT)),
    CTRL(arrayOf(Keys.CONTROL_LEFT, Keys.CONTROL_RIGHT)),
    ALT(arrayOf(Keys.ALT_LEFT, Keys.ALT_RIGHT))
}

enum class ControllerButton {

    A {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonA
    },
    B {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonB
    },
    X {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonX
    },
    Y {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonY
    },
    DPAD_LEFT {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonDpadLeft
    },
    DPAD_RIGHT {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonDpadRight
    },
    DPAD_UP {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonDpadUp
    },
    DPAD_DOWN {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonDpadDown
    },
    SHOULDER_LEFT {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonL1
    },
    SHOULDER_RIGHT {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonR1
    },
    START {
        override fun getCode(mapping: ControllerMapping): Int = mapping.buttonStart
    },

    ;

    abstract fun getCode(mapping: ControllerMapping): Int

}

typealias KeyCode = Int
