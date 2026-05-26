package com.microwavestudios.fortyfive.game.card

import com.microwavestudios.fortyfive.game.Poison
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl.Zone
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
     * gives the behaviour the opportunity to change a modifier before it is added to the card
     */
    open fun modifyDamageModifier(
        card: Card,
        controller: GameController,
        modifier: CardDamageModifier
    ): CardDamageModifier = modifier

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

    open fun putInHandAfterDestroy(card: Card, controller: GameController): Boolean = false

    abstract override fun equals(other: Any?): Boolean

    override fun hashCode(): Int = this::class.qualifiedName!!.hashCode()

    object Bewitched : BulletBehaviour(true) {

        override fun modifyRotationDirection(
            card: Card,
            controller: GameController,
            direction: RevolverRotation
        ): RevolverRotation {
            if (direction is RevolverRotation.Right) return RevolverRotation.Left(direction.amount)
            return direction
        }

        override fun equals(other: Any?): Boolean = other is Bewitched
    }

    object PoisonTip : BulletBehaviour(true) {

        override fun additionalEffects(): List<Effect> = Effect.GiveStatus(
            { controller, card, _ -> Poison(card!!.curDamage(controller!!)) },
            false,
            EffectData(
                trigger = Trigger.triggerForSituation<GameSituation.OnShot> { situation, card, _, _ ->
                    situation.card === card
                }
            )
        ).let { listOf(it) }

        override fun modifyOnShotDamage(
            card: Card,
            controller: GameController,
            damage: Int
        ): Int = 0

        override fun equals(other: Any?): Boolean = other is PoisonTip
    }

    object Everlasting : BulletBehaviour(true) {

        override fun keepInRevolverAfterShot(
            card: Card,
            controller: GameController,
            wasParry: Boolean,
            parryDamage: Int
        ): Boolean = !controller.isEverlastingDisabled

        override fun equals(other: Any?): Boolean = other is Everlasting
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

        override fun equals(other: Any?): Boolean = other is Undead
    }

    object ShotProtected : BulletBehaviour(true) {

        override fun preventsShooting(
            controller: GameController,
            card: Card
        ): Boolean = controller.turnCounter == card.enteredOnTurn

        override fun equals(other: Any?): Boolean = other is ShotProtected
    }

    object Replaceable : BulletBehaviour(true) {

        override fun canBeReplaced(
            controller: GameController,
            card: Card,
            by: Card
        ): Boolean = true

        override fun equals(other: Any?): Boolean = other is Replaceable
    }

    object Spray : BulletBehaviour(true) {

        override fun targetedEnemies(
            controller: GameController,
            card: Card
        ): List<Enemy> = controller.allEnemies

        override fun equals(other: Any?): Boolean = other is Spray
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

        override fun equals(other: Any?): Boolean = other is Thorns
    }

    object Jammed : BulletBehaviour(supportsBeingAddedLater = true) {
        override fun modifyRotationDirection(
            card: Card,
            controller: GameController,
            direction: RevolverRotation
        ): RevolverRotation {
            return RevolverRotation.None
        }

        override fun equals(other: Any?): Boolean = other is Jammed
    }


    object Catalyst : BulletBehaviour(supportsBeingAddedLater = true) {

        //Loop over targeted enemy/enemies and if they have status effects, add 1 to them
        override fun modifyOnShotDamage(card: Card, controller: GameController, damage: Int): Int {
            for(enemy in card.targetedEnemies(controller))
            {
                for(status in enemy.statusEffects)
                {
                    status.increment(1)
                }
            }

            return super.modifyOnShotDamage(card, controller, damage)
        }
        override fun equals(other: Any?): Boolean = other is Catalyst
    }

    object Phantom : BulletBehaviour(supportsBeingAddedLater = true) {
        //if !drawnfromtop, deal damage
        override fun additionalEffects(): List<Effect> =
            listOf(
                Effect.DamageDirectly(
                damage = {cont,_,_,self -> self?.curDamage(cont) ?: 0},
                false,
                EffectData(
                    trigger = Trigger.triggerForSituation<GameSituation.CardsDrawn> { situation, card, _, _ ->
                        card in situation.cards && situation.isFromBottom
                    }
                )),
                Effect.DamageDirectly(
                    damage = {cont,_,_,self -> self?.curDamage(cont) ?: 0},
                    false,
                    EffectData(
                        trigger = Trigger.triggerForSituation<GameSituation.ZoneChange> { situation, card, _, _ ->
                            !situation.before && situation.card === card &&
                                    situation.oldZone != Zone.STACK &&
                                    situation.newZone == Zone.HAND
                        }
                    )
                )
            )


        //Does no damage on shot
        override fun modifyOnShotDamage(
            card: Card,
            controller: GameController,
            damage: Int
        ): Int = 0

        override fun equals(other: Any?): Boolean = other is Phantom
    }

    object HighVelocity : BulletBehaviour(supportsBeingAddedLater = true) {
        //Does no damage on shot
        override fun modifyOnShotDamage(
            card: Card,
            controller: GameController,
            damage: Int
        ): Int = 0

        //TODO: Needs to check for if bullet has been removed from chamber
        override fun additionalEffects(): List<Effect> = Effect.DamageDirectly(
            damage = {cont,_,_,self -> self?.curDamage(cont) ?: 0},
            false,
            EffectData(
                trigger = Trigger.triggerForSituation<GameSituation.ZoneChange> { situation, card, _, cont ->
                    !situation.before && situation.card === card &&
                            situation.oldZone == Zone.REVOLVER &&
                            situation.newZone != Zone.REVOLVER
                }
            )
        ).let { listOf(it) }

        override fun equals(other: Any?): Boolean = other is HighVelocity
    }

    class Amplify(val damageIncrease: Int) : BulletBehaviour(true) {

        override fun modifyDamageModifier(
            card: Card,
            controller: GameController,
            modifier: CardDamageModifier
        ): CardDamageModifier {
            if (modifier.damage == 0 && modifier.damageMultiplier == 1f) return modifier
            if (modifier.damage < 0 || modifier.damageMultiplier < 1f) return modifier
            val newModifier = modifier.copy(damage = modifier.damage + damageIncrease)
            return newModifier
        }

        override fun equals(other: Any?): Boolean = other is Amplify && other.damageIncrease == damageIncrease

        override fun hashCode(): Int = super.hashCode() * damageIncrease.hashCode()
    }


    object Spirit : BulletBehaviour(supportsBeingAddedLater = true) {
        //Does no damage on shot
        override fun modifyOnShotDamage(
            card: Card,
            controller: GameController,
            damage: Int
        ): Int = 0

        override fun equals(other: Any?): Boolean = other is Spirit
    }

    object PutInHandAfterDestroy : BulletBehaviour(true) {

        override fun putInHandAfterDestroy(
            card: Card,
            controller: GameController
        ): Boolean = true

        override fun equals(other: Any?): Boolean = other is PutInHandAfterDestroy
    }

}
