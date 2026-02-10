package com.microwavestudios.fortyfive.game.card

import com.microwavestudios.fortyfive.game.Poison
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
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
     */
    abstract val description: String
    /**
     * icon that represent this stamp both on the card and in the UI
     */
    abstract val icon: ResourceHandle

    /**
     * called before the bullet is shot/parried with and has the ability to change how the revolver rotates after
     */
    open fun modifyRotationDirection(
        direction: RevolverRotation,
        controller: GameController
    ): RevolverRotation = direction

    /**
     * called before the card is constructed to add effects in addition to the ones declared in cards.onj
     */
    open fun additionalEffects(): List<Effect>? = null

    /**
     * called before the card is constructed to add trait-effects in addition to the ones declared in cards.onj
     */
    open fun additionalTraitEffects(): List<String>? = null

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
     * modifies the amount of damage the card deals when it is shot. Called just before the damage is dealt. Other
     * than [modifyBaseDamage] this modifies the damage just for this one specific shot and does not affect the
     * damage value written on the card. The [damage] value received already has all CardModifiers applied.
     */
    open fun modifyOnShotDamage(card: Card, controller: GameController, damage: Int): Int = damage

    /**
     * Modifies the amount the card parries for. Called when an enemy attacks and this card is in slot 5. This
     * functions modifies the parry value just for one specific parry event. The [parryValue] passed is either
     * the parryNumber of the card or the damage of the card, with all CardModifiers applied, if no parry number
     * is specified.
     */
    open fun modifyParryValue(card: Card, controller: GameController, parryValue: Int): Int = parryValue


    object Bewitched : Stamp("bewitched", "Bewitched") {

        override val description: String = "The revolver rotates left instead of right"
        override val icon: ResourceHandle = "card_stamp_test"

        override fun modifyRotationDirection(direction: RevolverRotation, controller: GameController): RevolverRotation {
            if (direction is RevolverRotation.Right) return RevolverRotation.Left(direction.amount)
            return direction
        }
    }

    object PoisonTip : Stamp("poisonTip", "Poison Tip") {

        override val description: String = $$"""
            When $status$POISON$status$ is active, adds the dmg value to the dmg value of the poison
            status effect instead of dealing dmg On-Shot
        """.trimIndent().replace('\n', ' ')

        override val icon: ResourceHandle = "card_stamp_test"

        override fun additionalEffects(): List<Effect> = Effect.GiveStatus(
            { controller, card, _ -> Poison(card!!.curDamage(controller!!)) },
            true,
            EffectData(
                trigger = Trigger.triggerForSituation<GameSituation.OnShot> { situation, card, triggerInfo, _ ->
                    situation.card === card && triggerInfo.targetedEnemies.any { enemy ->
                        enemy.statusEffects.any { it is Poison }
                    }
                }
            )
        ).let { listOf(it) }

        override fun modifyOnShotDamage(
            card: Card,
            controller: GameController,
            damage: Int
        ): Int = 0
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
