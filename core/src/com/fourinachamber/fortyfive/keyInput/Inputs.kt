package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.Input

class Input(val name: String, val causes: Array<Cause>) {

    override fun toString(): String = "Input($name)"

    sealed class Cause {

        class Keyboard(
            val primaryKey: KeyCode,
            val modifierKeys: Array<ModifierKey> = arrayOf(),
            val requireStates: Array<InputState> = arrayOf()
        ) : Cause()

        class Mouse(val button: MouseButton, val requireDirectHit: Boolean = true) : Cause()
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
    SHIFT(arrayOf(Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT)),
    CTRL(arrayOf(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT)),
    ALT(arrayOf(Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT))
}

typealias KeyCode = Int
