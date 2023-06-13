package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputProcessor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import onj.value.OnjArray
import onj.value.OnjInt
import onj.value.OnjNamedObject
import onj.value.OnjObject

/**
 * an entry for the [KeyInputMap]
 */
data class KeyInputMapEntry(
    val keycode: Keycode,
    val action: KeyAction,
    val modifierKeys: List<Keycode>
)

typealias Keycode = Int

/**
 * Binds keys to certain actions
 */
class KeyInputMap(
    private val entries: List<KeyInputMapEntry>,
    private val screen: OnjScreen
) : InputProcessor {

    private val modifiers: MutableSet<Keycode> = mutableSetOf()

    override fun keyDown(keycode: Keycode): Boolean {
        if (keycode in modifierKeys) {
            modifiers.add(keycode)
            return true
        }

        var bestCandidate: KeyAction? = null
        var bestCandidateModifierCount = -1
        for (entry in entries) {
            if (entry.keycode != keycode) continue
            if (!areAllModifiersPressed(entry.modifierKeys)) continue
            if (entry.modifierKeys.size > bestCandidateModifierCount) {
                bestCandidate = entry.action
                bestCandidateModifierCount = entry.modifierKeys.size
            }
        }
        return bestCandidate?.invoke(screen) ?: false
    }

    private fun areAllModifiersPressed(modifierKeys: List<Keycode>): Boolean {
        for (key in modifierKeys) {
            if (key !in modifiers) return false
        }
        return true
    }

    override fun keyUp(keycode: Keycode): Boolean {
        if (keycode !in modifierKeys) return false
        modifiers.remove(keycode)
        return true
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        return false
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return false
    }

    companion object {

        /**
         * array of all keys that are considered to be modifiers
         */
        val modifierKeys = arrayOf(
            Keys.ALT_LEFT,
            Keys.ALT_RIGHT,
            Keys.CONTROL_LEFT,
            Keys.CONTROL_RIGHT,
            Keys.SHIFT_LEFT,
            Keys.SHIFT_RIGHT
        )

        /**
         * creates a new KeyInputMap using an OnjArray
         */
        fun readFromOnj(actions: OnjArray, screen: OnjScreen): KeyInputMap {
            val entries = actions
                .value
                .map { obj ->

                    obj as OnjObject
                    val action = KeyActionFactory.getAction(obj.get<OnjNamedObject>("action"))
                    val modifiers = obj
                        .get<OnjArray>("modifiers")
                        .value
                        .map {
                            (it as OnjInt).value.toInt()
                        }

                    KeyInputMapEntry(
                        obj.get<Long>("keycode").toInt(),
                        action,
                        modifiers
                    )
                }

            return KeyInputMap(entries, screen)
        }

    }

}