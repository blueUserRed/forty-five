package com.fourinachamber.fourtyfive.keyInput

data class KeyInputMapEntry(
    val keycode: Keycode,
    val action: KeyAction,
    val modifierKeys: List<Keycode>
)

typealias Keycode = Int
