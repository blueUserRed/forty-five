package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.Input.Keys

private fun createKeyEntries(vararg keys: Int): List<KeyInputMapKeyEntry> {
    return keys.map { KeyInputMapKeyEntry(it) }
}

enum class KeyPreset(val keys: List<KeyInputMapKeyEntry>) {
    LEFT(createKeyEntries(Keys.LEFT, Keys.A)),
    RIGHT(createKeyEntries(Keys.RIGHT, Keys.D)),
    UP(createKeyEntries(Keys.UP, Keys.W)),
    DOWN(createKeyEntries(Keys.DOWN, Keys.S)),
    ESCAPE(createKeyEntries(Keys.ESCAPE, Keys.E)),
    ACTION(createKeyEntries(Keys.SPACE, Keys.ENTER, Keys.NUMPAD_ENTER)),
    ;

    companion object {
        fun fromKeyCode(input: Int): KeyPreset? {
            return KeyPreset.entries.find { preset ->
                preset.keys.any { it.keycode == input }
            }
        }
    }
}