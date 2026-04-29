package com.microwavestudios.fortyfive.game

import com.microwavestudios.fortyfive.utils.requireNot

abstract class Talisman {

    abstract val name: String
    abstract val description: String

}

object TalismanFactory {

    private val talismanCreator: MutableMap<String, () -> Talisman> = mutableMapOf()

    fun addTalisman(name: String, creator: () -> Talisman) {
        requireNot(talismanCreator.containsKey(name)) { "Talisman with name $name already exists" }
        talismanCreator[name] = creator
    }

    fun getTalisman(name: String): Talisman {
        val creator = talismanCreator[name]
        requireNotNull(creator) { "No talisman with name $name" }
        return creator()
    }

}
