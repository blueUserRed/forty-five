package com.microwavestudios.fortyfive.game.card

import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.run.RunGeneratorConfig
import kotlin.random.Random

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

    /**
     * behaviours returned here will be added to the card
     */
    open fun behaviours(): List<BulletBehaviour>? = null

    open fun traitEffects(): List<String> = emptyList()

    open fun canBeAppliedTo(card: Card): Boolean = true

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

    object Jammed : Stamp("jammed", "Jammed") {

        override val description: String = $$"""
            This bullet has $trait$JAMMED$trait$
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun behaviours(): List<BulletBehaviour> = listOf(BulletBehaviour.Jammed)
    }

    object Undead : Stamp("undead", "Undead") {

        override val description: String = $$"""
            This bullet has $trait$UNDEAD$trait$
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun behaviours(): List<BulletBehaviour> = listOf(BulletBehaviour.Undead)
    }

    object Gauge : Stamp("gauge", "Gauge") {

        override val description: String = $$"""
            This bullet has $trait$SPRAY$trait$
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun behaviours(): List<BulletBehaviour> = listOf(BulletBehaviour.Spray)
    }

    object HighVelocity : Stamp("highVelocity", "High Velocity") {

        override val description: String = $$"""
            This bullet has $trait$HIGH VELOCITY$trait$
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun behaviours(): List<BulletBehaviour> = listOf(BulletBehaviour.HighVelocity)
    }

    object Phantom : Stamp("phantom", "Phantom") {

        override val description: String = $$"""
            This bullet has $trait$PHANTOM$trait$
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun behaviours(): List<BulletBehaviour> = listOf(BulletBehaviour.Phantom)
    }

    object Catalyst : Stamp("catalyst", "Catalyst") {

        override val description: String = """
            When shooting this Bullet at an enemy, add 1 to every parameter of all status effects this enemy has.
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun behaviours(): List<BulletBehaviour> = listOf(BulletBehaviour.Catalyst)
    }

    object Spirit : Stamp("spirit", "Spirit") {

        override val description: String = $$"""
            This bullet has $trait$SPIRIT$trait$
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun modifyBaseCost(card: Card, original: Int): Int {
            if(original > 1)
            {
                val newCost = original - 1
                return newCost
            }

            //Runs if base cost is 1 or less
            return original
        }

        override fun canBeAppliedTo(card: Card): Boolean = card.originalBaseCost >= 2

        override fun behaviours(): List<BulletBehaviour> = listOf(BulletBehaviour.Spirit)
    }

    object FiftyCal : Stamp("fiftyCal", ".50 Cal") {

        override val description: String = $$"""
            This bullet has $trait$PIERCING$trait$
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun traitEffects(): List<String> {
            return listOf("piercing")
        }
    }

}

/**
 * used for creating new stamps
 */
object StampFactory {

    private val stampCreators: MutableMap<String, () -> Stamp> = mutableMapOf()

    init {
        addStampCreator(Stamp.Bewitched)
        addStampCreator(Stamp.PoisonTip)
        addStampCreator(Stamp.Jammed)
        addStampCreator(Stamp.Undead)
        addStampCreator(Stamp.Gauge)
        addStampCreator(Stamp.HighVelocity)
        addStampCreator(Stamp.Phantom)
        addStampCreator(Stamp.Catalyst)
        addStampCreator(Stamp.Spirit)
        addStampCreator(Stamp.FiftyCal)
    }

    fun getRandomStamp(majorDifficulty: Int, random: Random): Stamp {
        val stampPools = RunGeneratorConfig.stampPools
        var allowedStamps: List<String>? = null
        var difficultyToCheck = majorDifficulty
        while (difficultyToCheck >= 0) {
            val curPool = stampPools[difficultyToCheck]
            if (curPool != null) {
                allowedStamps = curPool
                break
            }
            difficultyToCheck--
        }
        requireNotNull(allowedStamps) { "no allowed stamps configured for difficulty $majorDifficulty" }
        val stampName = allowedStamps.random(random)
        return createStamp(stampName)
    }

    fun addStampCreator(stamp: Stamp) {
        addStampCreator(stamp.name) { stamp }
    }

    private fun addStampCreator(name: String, creator: () -> Stamp) {
        require(!stampCreators.containsKey(name)) { "Stamp with name '$name' already exists" }
        stampCreators[name] = creator
    }

    fun createStamp(name: String): Stamp {
        val creator = stampCreators[name]
        requireNotNull(creator) { "no stamp with name '$name'" }
        return creator()
    }

}
