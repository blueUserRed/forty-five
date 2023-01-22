package com.fourinachamber.fourtyfive.keyInput

data class KeyInputMapEntry(
    val keycode: Keycode,
    val modifierKeys: List<Keycode>,
    val action: KeyAction
)

typealias Keycode = Int
