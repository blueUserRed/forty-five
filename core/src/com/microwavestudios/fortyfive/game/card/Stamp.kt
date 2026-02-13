package com.microwavestudios.fortyfive.game.card

import com.microwavestudios.fortyfive.resources.ResourceHandle

/**
 * a stamp can be put on a card to modify its behaviour
 * @property name The name used to represent the stamp internally and in savefiles
 * @property title The name formatted properly that is shown to the user in the UI. Uses
 * [AdvancedText][com.microwavestudios.fortyfive.screen.commonComponents.AdvancedText] formatting.
 */
abstract class Stamp(
    val name: String,
    val title: String,
) {

    /**
     * Short description of what the stamp does. Uses
     * [AdvancedText][com.microwavestudios.fortyfive.screen.commonComponents.AdvancedText] formatting.
     * Can reference keywords/traits or status effects from descriptions.onj
     */
    abstract val description: String
    /**
     * icon that represent this stamp both on the card and in the UI
     */
    abstract val icon: ResourceHandle

    /**
     * called during construction of a card to modify the base damage of a card. The base damage is the damage
     * that is considered "normal" for that card, as if it where the damage declared in cards.onj. All card modifiers
     * that further change the damage will be applied on top of the base damage.
     */
    open fun modifyBaseDamage(card: Card, original: Int): Int = original

    /**
     * called during construction of a card to modify the base cost of a card. The base cost is the cost value
     * that is considered "normal" for that card, as if it where the cost declared in cards.onj. All card modifiers
     * that further change the cost will be applied on top of the base cost.
     */
    open fun modifyBaseCost(card: Card, original: Int): Int = original

    open fun behaviours(): List<BulletBehaviour>? = null


    object Bewitched : Stamp("bewitched", "Bewitched") {

        override val description: String = """
            The revolver rotates left instead of right when shooting or parrying with this bullet.
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun behaviours(): List<BulletBehaviour> = listOf(BulletBehaviour.Bewitched)
    }

    object PoisonTip : Stamp("poisonTip", "Poison Tip") {

        override val description: String = $$"""
           This Bullet has $keyword$POISON TIP$keyword$
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun behaviours(): List<BulletBehaviour> = listOf(BulletBehaviour.PoisonTip)
    }

}

/**
 * used for creating new stamps
 */
object StampFactory {

    private val stampCreators: MutableMap<String, () -> Stamp> = mutableMapOf()

    init {
        addStampCreator(Stamp.Bewitched.name) { Stamp.Bewitched }
        addStampCreator(Stamp.PoisonTip.name) { Stamp.PoisonTip }
    }

    fun addStampCreator(name: String, creator: () -> Stamp) {
        require(!stampCreators.containsKey(name)) { "Stamp with name '$name' already exists" }
        stampCreators[name] = creator
    }

    fun createStamp(name: String): Stamp {
        val creator = stampCreators[name]
        requireNotNull(creator) { "no stamp with name '$name'" }
        return creator()
    }

}
