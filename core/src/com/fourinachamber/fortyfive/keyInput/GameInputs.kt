package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.Input.Keys

object GameInputs {

    val interact = Input("interact", arrayOf(
        Input.Cause.Mouse(MouseButton.LEFT),
        Input.Cause.Keyboard(
            Keys.ENTER,
            requireStates = arrayOf(States.trueFocused)
        )
    ))

    val toDel = Input("interact", arrayOf(
        Input.Cause.Mouse(MouseButton.LEFT),
        Input.Cause.Keyboard(
            Keys.C,
            requireStates = arrayOf(States.trueFocused)
        )
    ))

    val focusNext = Input(
        "focusNext",
        arrayOf(Input.Cause.Keyboard(Keys.TAB))
    )

    val focusPrevious = Input(
        "focusPrevious",
        arrayOf(Input.Cause.Keyboard(Keys.TAB, arrayOf(ModifierKey.SHIFT)))
    )

    val focusUp = Input(
        "focusUp",
        arrayOf(
            Input.Cause.Keyboard(Keys.UP),
            Input.Cause.Keyboard(Keys.W)
        )
    )

    val focusDown = Input(
        "focusDown",
        arrayOf(
            Input.Cause.Keyboard(Keys.DOWN),
            Input.Cause.Keyboard(Keys.S)
        )
    )

    val focusLeft = Input(
        "focusLeft",
        arrayOf(
            Input.Cause.Keyboard(Keys.LEFT),
            Input.Cause.Keyboard(Keys.A)
        )
    )

    val focusRight = Input(
        "focusRight",
        arrayOf(
            Input.Cause.Keyboard(Keys.RIGHT),
            Input.Cause.Keyboard(Keys.D)
        )
    )

    val cancel = Input(
        "cancel",
        arrayOf(Input.Cause.Keyboard(Keys.ESCAPE))
    )

    val switchSelectorToLeft = Input(
        "switchToLeft",
        arrayOf(
            Input.Cause.Keyboard(Keys.LEFT, requireStates = arrayOf(States.trueFocused)),
            Input.Cause.Keyboard(Keys.A, requireStates = arrayOf(States.trueFocused)),
        )
    )

    val switchSelectorToRight = Input(
        "switchToRight",
        arrayOf(
            Input.Cause.Keyboard(Keys.RIGHT, requireStates = arrayOf(States.trueFocused)),
            Input.Cause.Keyboard(Keys.D, requireStates = arrayOf(States.trueFocused)),
        )
    )

    val toggleDebugMenu = Input(
        "toggleDebugMenu",
        arrayOf(
            Input.Cause.Keyboard(Keys.D, modifierKeys = arrayOf(ModifierKey.ALT))
        )
    )

    val nextDebugMenuPage = Input(
        "nextDebugMenuPage",
        arrayOf(
            Input.Cause.Keyboard(Keys.J)
        )
    )

    val previousDebugMenuPage = Input(
        "previousDebugMenuPage",
        arrayOf(
            Input.Cause.Keyboard(Keys.K)
        )
    )

    val initDragAndDrop = Input("initDragAndDrop", arrayOf(
        Input.Cause.Keyboard(Keys.ENTER, requireStates = arrayOf(States.trueFocused))
    ))

    val confirmDragAndDrop = Input("confirmDragAndDrop", arrayOf(
        Input.Cause.Keyboard(Keys.ENTER, requireStates = arrayOf(States.trueFocused))
    ))

    val triggerCard = Input("triggerCard", arrayOf(
        Input.Cause.Mouse(MouseButton.RIGHT),
        Input.Cause.Keyboard(
            Keys.ENTER,
            modifierKeys = arrayOf(ModifierKey.SHIFT),
            requireStates = arrayOf(States.trueFocused)
        )
    ))

    object States {

        val trueFocused = InputState("trueFocused", arrayOf(
            InputManager.BaseStates.keyboardFocus,
            InputManager.BaseStates.mouseHover
        ))

        val manuallyFocused = InputState("manuallyFocused", arrayOf())

        val focused = InputState("focused", arrayOf(
            trueFocused,
            manuallyFocused
        ))

        val awaitingDrop = InputState(
            "awaitingDrop",
            arrayOf(
                InputManager.BaseStates.awaitingDropFromMouse,
                InputManager.BaseStates.awaitingDropFromKeyboard
            )
        )
    }

}
