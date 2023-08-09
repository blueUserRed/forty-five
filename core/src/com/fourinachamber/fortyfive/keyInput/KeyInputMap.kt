package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputProcessor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import onj.value.OnjArray
import onj.value.OnjInt
import onj.value.OnjNamedObject
import onj.value.OnjObject

/**
 * an entry for the [KeyInputMap]
 */
data class KeyInputMapKeyEntry(
    val keycode: Keycode,
    val action: KeyAction?,
    val modifierKeys: List<Keycode>
)

/**
 * an entry for the [KeyInputMap]
 */
data class KeyInputMapEntry(
    /**
     * highest prio wins
     */
    val priority: Int,
    val condition: KeyInputCondition,
    val singleKeys: List<KeyInputMapKeyEntry>,
    val defaultAction: KeyAction?,
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
        var bestCandidatePriority: Int = Int.MIN_VALUE
        val inputRanges = InputKeyRange.values()
        entries.filter { it.condition.check(screen) }
            .forEach { entryList ->
                entryList.singleKeys
                    .filter {
                        it.keycode == keycode || (inputRanges.find { range -> it.keycode == range.getCode() }
                            ?.inRange(keycode) ?: false)
                    }
                    .filter { areAllModifiersPressed(it.modifierKeys) }
                    .forEach { keyEntry ->
                        if (bestCandidatePriority < entryList.priority) {
                            bestCandidate = keyEntry.action ?: entryList.defaultAction
                            bestCandidatePriority = entryList.priority
                        } else if(bestCandidatePriority == entryList.priority){
                            FortyFiveLogger.severe(logTag, "There are multiple valid keys with the same priority!")
                            return false
                        }
                    }
            }
        return bestCandidate?.invoke(screen, keycode) ?: false
    }

    override fun keyTyped(character: Char): Boolean {
        return false
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

        const val logTag: String = "KeyInputMap"

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
                .map { entry ->
                    entry as OnjObject
                    val defaultActions = entry.get<OnjNamedObject?>("action")?.let { KeyActionFactory.getAction(it) }
                    val entries = mutableListOf<KeyInputMapKeyEntry>()
                    entry.get<OnjArray>("triggers").value.forEach { obj ->
                        obj as OnjObject
                        val option = obj.get<OnjNamedObject?>("action")?.let { KeyActionFactory.getAction(it) }
                        val modifiers = obj
                            .get<OnjArray>("modifiers")
                            .value
                            .map {
                                (it as OnjInt).value.toInt()
                            }
                        entries.add(
                            KeyInputMapKeyEntry(
                                obj.get<Long>("keycode").toInt(),
                                option,
                                modifiers
                            )
                        )
                    }
                    val priority = (entry.get<Long?>("priority") ?: 0).toInt()
                    val condition = entry.get<KeyInputCondition?>("condition") ?: KeyInputCondition.Always
                    KeyInputMapEntry(priority, condition, entries, defaultActions)
                }
            return KeyInputMap(entries, screen)
        }
    }
}

enum class InputKeyRange {
    ASCII {
        override fun inRange(nbr: Keycode): Boolean = nbr in (0 .. 128)
          //not correct, because Keys are weird, but it works fine enough
    },
    DIGIT {
        override fun inRange(nbr: Keycode): Boolean = nbr.toInt() in (Keys.NUM_0..Keys.NUM_9)
    };

    abstract fun inRange(nbr: Keycode): Boolean
    fun getCode(): Int = Keys.MAX_KEYCODE + 100 + ordinal
}

sealed class KeyInputCondition {
    object Always : KeyInputCondition() {
        override fun check(screen: OnjScreen): Boolean = true
    }

    class ScreenState(val state: String) : KeyInputCondition() {
        override fun check(screen: OnjScreen): Boolean = state in screen.screenState
    }

    class Or(val first: KeyInputCondition, val second: KeyInputCondition) : KeyInputCondition() {
        override fun check(screen: OnjScreen): Boolean = first.check(screen) || second.check(screen)
    }

    class And(val first: KeyInputCondition, val second: KeyInputCondition) : KeyInputCondition() {
        override fun check(screen: OnjScreen): Boolean = first.check(screen) && second.check(screen)
    }

    class Not(val first: KeyInputCondition) : KeyInputCondition() {
        override fun check(screen: OnjScreen): Boolean = !first.check(screen)
    }

    abstract fun check(screen: OnjScreen): Boolean
}