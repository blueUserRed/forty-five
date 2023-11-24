package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.game.GameController.RevolverRotation
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.Trigger
import com.fourinachamber.fortyfive.game.card.TriggerInformation
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import java.lang.Exception

sealed class EncounterModifier {

    object Rain : EncounterModifier() {
        override fun shouldApplyStatusEffects(): Boolean = false
    }

    object Frost : EncounterModifier() {
        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = RevolverRotation.None
    }

    object BewitchedMist : EncounterModifier() {

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = when (rotation) {
            is RevolverRotation.Right -> RevolverRotation.Left(rotation.amount)
            is RevolverRotation.Left -> RevolverRotation.Right(rotation.amount)
            else -> rotation
        }
    }

    object Lookalike : EncounterModifier() {

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = controller.tryToPutCardsInHandTimeline(card.name)
    }

    object Moist : EncounterModifier() {

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = Timeline.timeline {
            val rotationTransformer = { old: Card.CardModifier, triggerInformation: TriggerInformation -> Card.CardModifier(
                damage = old.damage - (triggerInformation.multiplier ?: 1),
                source = old.source,
                validityChecker = old.validityChecker,
                transformers = old.transformers
            )}
            val modifier = Card.CardModifier(
                damage = 0,
                source = "moist modifier",
                validityChecker = { card.inGame },
                transformers = mapOf(
                    Trigger.ON_REVOLVER_ROTATION to rotationTransformer
                )
            )
            card.addModifier(modifier)
        }


    }

    open fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = rotation

    open fun shouldApplyStatusEffects(): Boolean = true

    open fun executeAfterBulletWasPlacedInRevolver(card: Card, controller: GameController): Timeline? = null

    open fun executeAfterRevolverWasShot(card: Card?, controller: GameController): Timeline? = null

    open fun executeAfterRevolverRotated(rotation: RevolverRotation, controller: GameController): Timeline? = null

    companion object {

        fun getFromName(name: String) = when (name.lowercase()) {
            "rain" -> Rain
            "frost" -> Frost
            "bewitchedmist" -> BewitchedMist
            "lookalike" -> Lookalike
            "moist" -> Moist
            else -> throw RuntimeException("Unknown Encounter Modifier: $name")
        }
    }
}
