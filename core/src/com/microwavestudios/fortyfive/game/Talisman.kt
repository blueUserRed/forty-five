package com.microwavestudios.fortyfive.game

import com.microwavestudios.fortyfive.utils.requireNot

abstract class Talisman {

    abstract val name: String
    abstract val title: String
    abstract val description: String
    open val flavourText: String? = null

    open fun behaviours(): List<EncounterBehaviour> = listOf()


    object TalismanBullet : Talisman() {

        override val title: String = "Talisman Bullet"
        override val name: String = "talismanBullet"
        override val description: String = "All bullets have +3 dmg"

        override fun behaviours(): List<EncounterBehaviour> = listOf(
            EncounterBehaviour.ChangeDamageOfAllBullets(3, title)
        )
    }

    object GoldNugget : Talisman() {

        override val name: String = "goldNugget"
        override val title: String = "Gold Nugget"
        override val description: String = "Start the Encounter with +2 handcards"

        override fun behaviours(): List<EncounterBehaviour> = listOf(
            EncounterBehaviour.AddCardsInInitialDraw(2)
        )
    }

    object FieldRations : Talisman() {

        override val name: String = "fieldRations"
        override val title: String = "Field Rations"
        override val description: String = """
            You start the encounter with 8 reserves in a turn.
            Every turn, you start the turn with 1 less.
            This can't reduce reserves to less than 2.
        """.trimIndent().replace('\n', ' ')

        override fun behaviours(): List<EncounterBehaviour> = listOf(
            EncounterBehaviour.FieldRations(8)
        )
    }

    object SleepingBag : Talisman() {

        override val name: String = "sleepingBag"
        override val title: String = "Sleeping Bag"
        override val description: String = """
            You start the turn with +1 reserves, as long as the revolver didn't rotate last turn.
        """.trimIndent().replace('\n', ' ')

        override fun behaviours(): List<EncounterBehaviour> = listOf(
            EncounterBehaviour.SleepingBag()
        )
    }

    object Lasso : Talisman() {

        override val name: String = "lasso"
        override val title: String = "Lasso"
        override val description: String = """
            Whenever you place a bullet in the revolver,
            return a different bullet from the revolver to your hand.
        """.trimIndent().replace('\n', ' ')

        override fun behaviours(): List<EncounterBehaviour> = listOf(
            EncounterBehaviour.Lasso
        )
    }

    object WintersGrace : Talisman() {

        override val name: String = "wintersGrace"
        override val title: String = "Winter's Grace"
        override val description: String = $$"""
            Every second Bullet that enters the revolver: 
            You get status $status$FROZEN$status$(1)
        """.trimIndent().replace('\n', ' ')

        override fun behaviours(): List<EncounterBehaviour> = listOf(
            EncounterBehaviour.WintersGrace()
        )
    }

    object Overstock : Talisman() {

        override val name: String = "overstock"
        override val title: String = "Overstock"

        override val description: String = """
            As long as your deck has at least 25 cards:
            You start the turn with +1 reserve
        """.trimIndent().replace('\n', ' ')

        override val flavourText: String = """
            Maybe 3 extra pairs of underwear and the 10 bottles whisky were a little bit overkill, I admit.
        """.trimIndent().replace('\n', ' ')

        override fun behaviours(): List<EncounterBehaviour> = listOf(
            EncounterBehaviour.BonusReserveIfDeckHasAtLeast25Cards
        )
    }

}

object TalismanFactory {

    private val talismanCreator: MutableMap<String, () -> Talisman> = mutableMapOf()

    init {
        addTalisman(Talisman.TalismanBullet)
        addTalisman(Talisman.GoldNugget)
        addTalisman(Talisman.FieldRations)
        addTalisman(Talisman.SleepingBag)
        addTalisman(Talisman.Lasso)
        addTalisman(Talisman.WintersGrace)
        addTalisman(Talisman.Overstock)
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
