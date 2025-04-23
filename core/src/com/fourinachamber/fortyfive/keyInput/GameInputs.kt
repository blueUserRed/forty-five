package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.Input.Keys

object GameInputs {

    val interact = Input(arrayOf(
        Input.Cause.Mouse(MouseButton.LEFT),
        Input.Cause.Keyboard(
            Keys.ENTER,
            requireStates = arrayOf(States.focused)
        )
    ))

    val focusNext = Input(
        arrayOf(Input.Cause.Keyboard(Keys.TAB))
    )

    val focusPrevious = Input(
        arrayOf(Input.Cause.Keyboard(Keys.TAB, arrayOf(ModifierKey.SHIFT)))
    )

    object States {

        val trueFocused = InputState(arrayOf(
            InputManager.BaseStates.keyboardFocus,
            InputManager.BaseStates.mouseHover
        ))

        val manuallyFocused = InputState(arrayOf())

        val focused = InputState(arrayOf(
            trueFocused,
            manuallyFocused
        ))
    }

}
