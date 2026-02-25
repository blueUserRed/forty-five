package com.microwavestudios.fortyfive.keyInput

import com.badlogic.gdx.Input.Keys

object GameInputs {

    val interact = Input("interact", arrayOf(
        Input.Cause.Mouse(MouseButton.LEFT),
        Input.Cause.Keyboard(
            Keys.ENTER,
            requireStates = arrayOf(States.trueFocused)
        ),
        Input.Cause.ControllerButtonBased(
            ControllerButton.A,
            requireStates = arrayOf(States.trueFocused)
        )
    ))

    val focusNext = Input(
        "focusNext",
        arrayOf(
            Input.Cause.Keyboard(Keys.TAB),
            Input.Cause.ControllerButtonBased(ControllerButton.SHOULDER_RIGHT)
        ),
    )

    val focusPrevious = Input(
        "focusPrevious",
        arrayOf(
            Input.Cause.Keyboard(Keys.TAB, arrayOf(ModifierKey.SHIFT)),
            Input.Cause.ControllerButtonBased(ControllerButton.SHOULDER_LEFT)
        )
    )

    val scrollUp = Input(
        "scrollUp",
        arrayOf(
            Input.Cause.KeyHeldDown(Keys.UP),
            Input.Cause.ControllerAxisHeld(ControllerAxis.RIGHT_Y, -0.3f)
        )
    )

    val scrollDown = Input(
        "scrollDown",
        arrayOf(
            Input.Cause.KeyHeldDown(Keys.DOWN),
            Input.Cause.ControllerAxisHeld(ControllerAxis.RIGHT_Y, 0.3f)
        )
    )

    val scrollLeft = Input(
        "scrollLeft",
        arrayOf(
            Input.Cause.KeyHeldDown(Keys.LEFT),
            Input.Cause.ControllerAxisHeld(ControllerAxis.RIGHT_X, -0.3f)
        )
    )

    val scrollRight = Input(
        "scrollRight",
        arrayOf(
            Input.Cause.KeyHeldDown(Keys.RIGHT),
            Input.Cause.ControllerAxisHeld(ControllerAxis.RIGHT_X, 0.3f)
        )
    )

    val focusUp = Input(
        "focusUp",
        arrayOf(
            Input.Cause.Keyboard(Keys.W),
            Input.Cause.ControllerButtonBased(ControllerButton.DPAD_UP),
            Input.Cause.ControllerAxisFlick(ControllerAxis.LEFT_Y, -0.6f)
        )
    )

    val focusDown = Input(
        "focusDown",
        arrayOf(
            Input.Cause.Keyboard(Keys.S),
            Input.Cause.ControllerButtonBased(ControllerButton.DPAD_DOWN),
            Input.Cause.ControllerAxisFlick(ControllerAxis.LEFT_Y, 0.6f)
        )
    )

    val focusLeft = Input(
        "focusLeft",
        arrayOf(
            Input.Cause.Keyboard(Keys.A),
            Input.Cause.ControllerButtonBased(ControllerButton.DPAD_LEFT),
            Input.Cause.ControllerAxisFlick(ControllerAxis.LEFT_X, -0.6f)
        )
    )

    val focusRight = Input(
        "focusRight",
        arrayOf(
            Input.Cause.Keyboard(Keys.D),
            Input.Cause.ControllerButtonBased(ControllerButton.DPAD_RIGHT),
            Input.Cause.ControllerAxisFlick(ControllerAxis.LEFT_X, 0.6f)
        )
    )

    val toggleFullScreen = Input(
        "toggleFullScreen",
        arrayOf(
            Input.Cause.Keyboard(Keys.F),
            Input.Cause.ControllerButtonBased(ControllerButton.START)
        ),
    )

    val cancel = Input(
        "cancel",
        arrayOf(
            Input.Cause.Keyboard(Keys.ESCAPE),
            Input.Cause.ControllerButtonBased(ControllerButton.B)
        )
    )

    val enemyAnimConfirmation = Input(
        "enemyAnimConfirmation",
        arrayOf(
            Input.Cause.Mouse(MouseButton.LEFT),
            Input.Cause.Keyboard(Keys.ENTER),
            Input.Cause.ControllerButtonBased(ControllerButton.A)
        )
    )


    val switchSelectorToLeft = Input(
        "switchToLeft",
        arrayOf(
            Input.Cause.Keyboard(Keys.LEFT, requireStates = arrayOf(States.trueFocused)),
            Input.Cause.Keyboard(Keys.A, requireStates = arrayOf(States.trueFocused)),
            Input.Cause.ControllerButtonBased(
                ControllerButton.DPAD_LEFT,
                requireStates = arrayOf(States.trueFocused)
            ),
            Input.Cause.ControllerAxisFlick(
                ControllerAxis.LEFT_X,
                -0.6f,
                requireStates = arrayOf(States.trueFocused)
            )
        )
    )

    val switchSelectorToRight = Input(
        "switchToRight",
        arrayOf(
            Input.Cause.Keyboard(Keys.RIGHT, requireStates = arrayOf(States.trueFocused)),
            Input.Cause.Keyboard(Keys.D, requireStates = arrayOf(States.trueFocused)),
            Input.Cause.ControllerButtonBased(
                ControllerButton.DPAD_RIGHT,
                requireStates = arrayOf(States.trueFocused)
            ),
            Input.Cause.ControllerAxisFlick(
                ControllerAxis.LEFT_X,
                0.6f,
                requireStates = arrayOf(States.trueFocused)
            )
        )
    )

    val moveSliderToLeft = Input(
        "moveSliderToLeft",
        arrayOf(
            Input.Cause.KeyHeldDown(Keys.LEFT, requireStates = arrayOf(States.trueFocused)),
            Input.Cause.KeyHeldDown(Keys.A, requireStates = arrayOf(States.trueFocused)),
            Input.Cause.ControllerAxisHeld(
                ControllerAxis.LEFT_X,
                -0.6f,
                requireStates = arrayOf(States.trueFocused)
            ),
            Input.Cause.ControllerAxisHeld(
                ControllerAxis.RIGHT_X,
                -0.6f,
                requireStates = arrayOf(States.trueFocused)
            )
        )
    )

    val moveSliderToRight = Input(
        "moveSliderToRight",
        arrayOf(
            Input.Cause.KeyHeldDown(Keys.RIGHT, requireStates = arrayOf(States.trueFocused)),
            Input.Cause.KeyHeldDown(Keys.D, requireStates = arrayOf(States.trueFocused)),
            Input.Cause.ControllerAxisHeld(
                ControllerAxis.LEFT_X,
                0.6f,
                requireStates = arrayOf(States.trueFocused)
            ),
            Input.Cause.ControllerAxisHeld(
                ControllerAxis.RIGHT_X,
                0.6f,
                requireStates = arrayOf(States.trueFocused)
            )
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
            Input.Cause.Keyboard(Keys.K)
        )
    )

    val previousDebugMenuPage = Input(
        "previousDebugMenuPage",
        arrayOf(
            Input.Cause.Keyboard(Keys.J)
        )
    )

    val initDragAndDrop = Input("initDragAndDrop", arrayOf(
        Input.Cause.Keyboard(Keys.ENTER, requireStates = arrayOf(InputManager.BaseStates.keyboardFocus)),
        Input.Cause.ControllerButtonBased(ControllerButton.A, requireStates = arrayOf(States.trueFocused))
    ))

    val confirmDragAndDrop = Input("confirmDragAndDrop", arrayOf(
        Input.Cause.Keyboard(Keys.ENTER, requireStates = arrayOf(States.trueFocused)),
        Input.Cause.ControllerButtonBased(ControllerButton.A, requireStates = arrayOf(States.trueFocused))
    ))

    val triggerCard = Input("triggerCard", arrayOf(
        Input.Cause.Mouse(MouseButton.RIGHT),
        Input.Cause.Keyboard(
            Keys.ENTER,
            modifierKeys = arrayOf(ModifierKey.SHIFT),
            requireStates = arrayOf(States.trueFocused)
        ),
        Input.Cause.ControllerButtonBased(ControllerButton.X, requireStates = arrayOf(States.trueFocused))
    ))

    val skipCredits = Input("skipCredits", arrayOf(
        Input.Cause.Keyboard(Keys.ENTER),
        Input.Cause.ControllerButtonBased(ControllerButton.A)
    ))

    val dialogContinue = Input("dialogContinue", arrayOf(
        Input.Cause.Keyboard(Keys.ENTER),
        Input.Cause.ControllerButtonBased(ControllerButton.A)
    ))


    val mapEditorDelete = Input("mapEditorDelete", arrayOf(
        Input.Cause.Keyboard(Keys.DEL),
        Input.Cause.Keyboard(Keys.BACKSPACE),
    ))
    val mapEditorConnect = Input("mapEditorConnect", arrayOf(
        Input.Cause.Keyboard(Keys.C),
    ))
    val mapEditorMove = Input("mapEditorMove", arrayOf(
        Input.Cause.Keyboard(Keys.M),
    ))
    val mapEditorScale = Input("mapEditorScale", arrayOf(
        Input.Cause.Keyboard(Keys.S),
    ))
    val mapEditorSwitchMode = Input("mapEditorSwitchMode", arrayOf(
        Input.Cause.Keyboard(Keys.X),
    ))
    val mapEditorNext = Input("mapEditorNext", arrayOf(
        Input.Cause.Keyboard(Keys.J),
    ))
    val mapEditorPrevious = Input("mapEditorPrevious", arrayOf(
        Input.Cause.Keyboard(Keys.H),
    ))

    object States {

        val trueFocused = InputState("trueFocused", arrayOf(
            InputManager.BaseStates.keyboardFocus,
            InputManager.BaseStates.mouseHover
        ))

        val manuallyFocused = InputState("manuallyFocused")

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

        val inDrag = InputState(
            "inDrag",
            arrayOf(
                InputManager.BaseStates.mouseDrag,
                InputManager.BaseStates.keyboardDrag,
            )
        )

        val awaitingDropFocused = InputState(
            "awaitingDropFocused",
            arrayOf(
                arrayOf(InputManager.BaseStates.awaitingDropFromMouse, InputManager.BaseStates.draggedHover),
                arrayOf(InputManager.BaseStates.awaitingDropFromKeyboard, InputManager.BaseStates.keyboardFocus)
            )
        )

    }
}
