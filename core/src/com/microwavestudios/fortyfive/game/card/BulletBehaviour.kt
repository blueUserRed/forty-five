package com.microwavestudios.fortyfive.game.card

import com.microwavestudios.fortyfive.game.Poison
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.collectTimeline

abstract class BulletBehaviour(val supportsBeingAddedLater: Boolean) {

    /**
     * called before the card is constructed to add effects in addition to the ones declared in cards.onj
     */
    open fun additionalEffects(): List<Effect>? = null

    /**
     * modifies the amount of damage the card deals when it is shot. Called just before the damage is dealt.
     * This modifies the damage just for this one specific shot and does not affect the
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

    /**
     * called immediately after the behaviour is added to the bullet
     */
    open fun init(card: Card) {}

    /**
     * called before the bullet is shot/parried with and has the ability to change how the revolver rotates after
     */
    open fun modifyRotationDirection(
        card: Card,
        controller: GameController,
        direction: RevolverRotation
    ): RevolverRotation = direction

    /**
     * called after the bullet is shot or parried with. When true is returned, the bullet will remain in the revolver
     * and not protecting modifiers will be used up
     */
    open fun keepInRevolverAfterShot(
        card: Card,
        controller: GameController,
        wasParry: Boolean,
        parryDamage: Int,
    ): Boolean = false

    /**
     * called after the bullet is shot or parried with. When true is returned, the bullet is put in the hand, when
     * false is returned, the bullet is put in the drawpile (the normal behaviour)
     */
    open fun putInHandInsteadOfStackAfterShot(
        card: Card,
        controller: GameController,
        wasParry: Boolean,
        parryDamage: Int,
    ): Boolean = false

    /**
     * called when something tries to apply a protecting modifier to the bullet. If true is returned, the modifier
     * will not be applied
     */
    open fun disableProtectingModifiers(): Boolean = false

    /**
     * called when this bullet is in the revolver and the player drags another bullet from the hand to this bullet.
     * If the function returns true, this bullet will be replaced by the other bullet
     */
    open fun canBeReplaced(controller: GameController, card: Card, by: Card): Boolean = false

    /**
     * called when the bullet is about to be shot. If true is returned, the player is not allowed to shoot the revolver
     */
    open fun preventsShooting(controller: GameController, card: Card): Boolean = false

    /**
     * overrides the enemies targeted by the bullet. If null is returned, `controller.targetedEnemy()` is used. If
     * multiple behaviours override this function, the first behaviour that doesn't return null takes priority
     */
    open fun targetedEnemies(controller: GameController, card: Card): List<Enemy>? = null

    /**
     * called after the bullet was shot or parried with, but before it is returned to the drawpile/hand. If a
     * timeline is returned, it will be included in the GameControllers main-timeline.
     */
    open fun afterShotTimeline(
        card: Card,
        controller: GameController,
        wasParry: Boolean,
        parryDamage: Int
    ): Timeline? = null


    object Bewitched : BulletBehaviour(true) {

        override fun modifyRotationDirection(
            card: Card,
            controller: GameController,
            direction: RevolverRotation
        ): RevolverRotation {
            if (direction is RevolverRotation.Right) return RevolverRotation.Left(direction.amount)
            return direction
        }
    }

    object PoisonTip : BulletBehaviour(true) {

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

    object Everlasting : BulletBehaviour(true) {

        override fun keepInRevolverAfterShot(
            card: Card,
            controller: GameController,
            wasParry: Boolean,
            parryDamage: Int
        ): Boolean = !controller.isEverlastingDisabled
    }

    object Undead : BulletBehaviour(true) {

        override fun putInHandInsteadOfStackAfterShot(
            card: Card,
            controller: GameController,
            wasParry: Boolean,
            parryDamage: Int
        ): Boolean = true

        override fun disableProtectingModifiers(): Boolean = true

        override fun init(card: Card) {
            card.clearProtectingModifiers()
        }
    }

    object ShotProtected : BulletBehaviour(true) {

        override fun preventsShooting(
            controller: GameController,
            card: Card
        ): Boolean = controller.turnCounter == card.enteredOnTurn
    }

    object Replaceable : BulletBehaviour(true) {

        override fun canBeReplaced(
            controller: GameController,
            card: Card,
            by: Card
        ): Boolean = true
    }

    object Spray : BulletBehaviour(true) {

        override fun targetedEnemies(
            controller: GameController,
            card: Card
        ): List<Enemy> = controller.allEnemies
    }

    object Thorns : BulletBehaviour(true) {

        override fun afterShotTimeline(
            card: Card,
            controller: GameController,
            wasParry: Boolean,
            parryDamage: Int
        ): Timeline? {
            if (!wasParry) return null
            return card
                .targetedEnemies(controller)
                .map { it.damage(parryDamage) }
                .collectTimeline()
        }
    }
}
