package com.microwavestudios.fortyfive.game

import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardCostModifier
import com.microwavestudios.fortyfive.game.card.CardDamageModifier
import com.microwavestudios.fortyfive.game.card.CardModifierData
import com.microwavestudios.fortyfive.game.card.GameSituation
import com.microwavestudios.fortyfive.game.card.Trigger
import com.microwavestudios.fortyfive.game.card.TriggerInformation
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl.Zone
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.utils.Timeline
import kotlin.math.max
import kotlin.math.roundToInt

abstract class EncounterBehaviour {

    object NoStatusEffects : EncounterBehaviour() {

        override fun shouldApplyStatusEffects(): Boolean = false
    }


    object NoRevolverRotation : EncounterBehaviour() {
        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = RevolverRotation.None
        override fun disableEverlasting(): Boolean = true
    }

    object MirrorRevolverRotations : EncounterBehaviour() {

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = when (rotation) {
            is RevolverRotation.Right -> RevolverRotation.Left(rotation.amount)
            is RevolverRotation.Left -> RevolverRotation.Right(rotation.amount)
            else -> rotation
        }
    }

    object Lookalike : EncounterBehaviour() {

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = controller.tryToPutCardsInHandTimeline(card.type)
    }

    class Moist(val sourceName: String) : EncounterBehaviour() {

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = Timeline.timeline {
            val rotationTransformer = { old: CardDamageModifier, triggerInformation: TriggerInformation -> CardDamageModifier(
                damage = old.damage - (triggerInformation.multiplier ?: 1),
                data = CardModifierData(
                    source = old.data.source,
                    validityChecker = old.data.validityChecker,
                ),
                transformers = old.transformers
            )}
            val modifier = CardDamageModifier(
                damage = 0,
                data = CardModifierData(
                    source = sourceName,
                    validityChecker = { _, _, _ -> card.inZone(Zone.REVOLVER) },
                ),
                transformers = listOf(
                    Trigger.triggerForSituation<GameSituation.RevolverRotation>() to rotationTransformer
                )
            )
            card.addDamageModifier(modifier, controller)
        }
    }

    class SteelNerves : EncounterBehaviour() {

        private var baseTime: Long = -1
        private var lastDigit: Int = -1

        override fun onStart(controller: GameController) {
            controller.gameEvents.fire(GameControllerImpl.Events.SteelNervesCountdown(10))
        }

        override fun update(controller: GameController) {
            if (baseTime == -1L) return

            if (controller.playerLost || controller.hasWon) {
                baseTime = -1L
            }

            val now = TimeUtils.millis()
            val diff = max(10 - ((now - baseTime).toDouble() / 1000.0).roundToInt(), 0)
            if (diff != lastDigit) {
                lastDigit = diff
                controller.gameEvents.fire(GameControllerImpl.Events.SteelNervesCountdown(diff))
            }
            if (now - baseTime < 10_000) return
            if (controller.isUIFrozen) return
            baseTime = -1
            controller.shoot()
        }

        override fun executeAfterRevolverWasShot(card: Card?, controller: GameController): Timeline = Timeline.timeline {
            action {
                baseTime = TimeUtils.millis()
                lastDigit = 10
            }
        }

        override fun executeOnEndTurn(): Timeline = Timeline.timeline {
            action {
                baseTime = -1
            }
        }

        override fun executeOnPlayerTurnStart(controller: GameController): Timeline = Timeline.timeline {
            action {
                baseTime = TimeUtils.millis()
                lastDigit = 10
            }
        }
    }

    class DrawMoreCards(val amount: Int) : EncounterBehaviour() {
        override fun additionalCardsToDrawInSpecialDraw(): Int = amount
        override fun additionalCardsToDrawInNormalDraw(): Int = amount
    }

    class ChangeBulletCost(val costChange: Int, val sourceName: String) : EncounterBehaviour() {

        override fun initBullet(card: Card, controller: GameController) {
            card.addCostModifier(
                CardCostModifier(
                    data = CardModifierData(
                        source = sourceName,
                        keepActive = true
                    ),
                    costChange = costChange,
                )
            )
        }
    }

    class ShootingRevolverCostsReserves(val cost: Int) : EncounterBehaviour() {

        override fun canShootRevolver(controller: GameController): Boolean {
            return controller.curReserves >= cost
        }

        override fun executeAfterRevolverWasShot(card: Card?, controller: GameController): Timeline = Timeline.timeline {
            controller.tryPay(cost, controller.shootButton)
        }
    }

    object BulletSkipping : EncounterBehaviour() {

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation =
            rotation.withAmount(rotation.amount * 2)
    }

    object Sacrifice : EncounterBehaviour() {

        override fun executeOnPlayerTurnStart(controller: GameController): Timeline = Timeline.timeline {
            later {
                val selector = CardInRevolverSelector(controller, "Select bullet to destroy")
                val promise = selector.startSelect()
                waitForPromise(promise)
                later {
                    val result = promise.getOrNull()
                    if (result != null) include(controller.destroyCardTimeline(result))
                }
            }
        }
    }

    object SorryNotSorry : EncounterBehaviour() {

        override fun executeOnPlayerTurnStart(controller: GameController): Timeline = Timeline.timeline {
            later {
                controller.cardsInRevolver().randomOrNull()?.let {
                    include(controller.bounceBulletTimeline(it))
                }
            }
        }
    }

    object Confused : EncounterBehaviour() {

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = RevolverRotation.None

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = Timeline.timeline {
            includeLater({
                controller.rotateRevolverTimeline(
                    card.getRotationDirection(controller),
                    ignoreEncounterModifiers = true
                )
            })
        }

        override fun disableEverlasting(): Boolean = true
    }

    class ChangeDamageOfAllBullets(val change: Int, val source: String) : EncounterBehaviour() {

        override fun initBullet(card: Card, controller: GameController) {
            val modifier = CardDamageModifier(
                damage = change,
                data = CardModifierData(
                    source = source,
                    keepActive = true
                )
            )
            card.addDamageModifier(modifier, controller)
        }
    }

    open fun update(controller: GameController) {}

    open fun onStart(controller: GameController) {}

    open fun executeOnEndTurn(): Timeline? = null

    open fun executeOnPlayerTurnStart(controller: GameController): Timeline? = null

    open fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = rotation

    open fun shouldApplyStatusEffects(): Boolean = true

    open fun disableEverlasting(): Boolean = false

    open fun executeAfterBulletWasPlacedInRevolver(card: Card, controller: GameController): Timeline? = null

    open fun executeAfterRevolverWasShot(card: Card?, controller: GameController): Timeline? = null

    open fun executeAfterRevolverRotated(rotation: RevolverRotation, controller: GameController): Timeline? = null

    open fun cardsInSpecialDrawMultiplier(): Float = 1f

    open fun cardsInNormalDrawMultiplier(): Float = 1f

    open fun additionalCardsToDrawInSpecialDraw(): Int = 0

    open fun additionalCardsToDrawInNormalDraw(): Int = 0

    open fun initBullet(card: Card, controller: GameController) {}

    open fun canShootRevolver(controller: GameController): Boolean = true

}
