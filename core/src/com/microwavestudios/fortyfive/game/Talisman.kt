package com.microwavestudios.fortyfive.game

import com.microwavestudios.fortyfive.utils.requireNot

abstract class Talisman {

    abstract val name: String
    abstract val title: String
    abstract val description: String

    open fun behaviours(): List<EncounterBehaviour> = listOf()


    object TalismanBullet : Talisman() {

        override val title: String = "Talisman Bullet"
        override val name: String = "talismanBullet"
        override val description: String = "All bullets have +3 dmg"

        override fun behaviours(): List<EncounterBehaviour> = listOf(
            EncounterBehaviour.ChangeDamageOfAllBullets(3, title)
        )
    }

}

object TalismanFactory {

    private val talismanCreator: MutableMap<String, () -> Talisman> = mutableMapOf()

    init {
        addTalisman(Talisman.TalismanBullet)
    }

    fun addTalisman(talisman: Talisman) {
        addTalisman(talisman.name, creator = { talisman })
    }

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
