package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.Input

class Input(val causes: Array<Cause>) {

    sealed class Cause {

        class Keyboard(
            val primaryKey: KeyCode,
            val modifierKeys: Array<ModifierKey> = arrayOf(),
            val requireStates: Array<InputState> = arrayOf()
        ) : Cause()

        class Mouse(val button: MouseButton, val requireDirectHit: Boolean = true) : Cause()
    }

}

class InputState(val causedByStates: Array<InputState>)

enum class MouseButton(val code: Int) {
    LEFT(0), RIGHT(1), MIDDLE(2), BACK(3), FORWARD(4)
}

enum class ModifierKey(val codes: Array<KeyCode>) {
    SHIFT(arrayOf(Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT)),
    CTRL(arrayOf(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT)),
    ALT(arrayOf(Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT))
}

typealias KeyCode = Int
