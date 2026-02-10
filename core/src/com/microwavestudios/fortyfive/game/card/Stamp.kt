package com.microwavestudios.fortyfive.game.card

import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.resources.ResourceHandle

abstract class Stamp(
    val name: String,
    val title: String,
) {

    abstract val description: String
    abstract val icon: ResourceHandle

    open fun modifyRotationDirection(
        direction: RevolverRotation,
        controller: GameController
    ): RevolverRotation = direction

    open fun additionalEffects(): List<Effect>? = null

    open fun modifyBaseDamage(card: Card, original: Int): Int = original
    open fun modifyBaseCost(card: Card, original: Int): Int = original

    open fun modifyOnShotDamage(card: Card, controller: GameController, damage: Int): Int = damage
    open fun modifyParryValue(card: Card, controller: GameController, parryValue: Int): Int = parryValue

    object Bewitched : Stamp("bewitched", "Bewitched") {

        override val description: String = "The revolver rotates left instead of right"
        override val icon: ResourceHandle = "card_stamp_test"

        override fun modifyRotationDirection(direction: RevolverRotation, controller: GameController): RevolverRotation {
            if (direction is RevolverRotation.Right) return RevolverRotation.Left(direction.amount)
            return direction
        }
    }

}


object StampFactory {

    private val stampCreator: MutableMap<String, () -> Stamp> = mutableMapOf()

    init {
        addStampCreator(Stamp.Bewitched.name) { Stamp.Bewitched }
    }

    fun addStampCreator(name: String, creator: () -> Stamp) {
        require(!stampCreator.containsKey(name)) { "Stamp with name '$name' already exists" }
        stampCreator[name] = creator
    }

    fun createStamp(name: String): Stamp {
        val creator = stampCreator[name]
        requireNotNull(creator) { "no stamp with name '$name'" }
        return creator()
    }

}
