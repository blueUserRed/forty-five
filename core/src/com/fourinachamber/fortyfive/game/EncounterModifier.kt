package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.game.GameController.RevolverRotation
import com.fourinachamber.fortyfive.game.card.Card
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

    class Moist : EncounterModifier() {

        private val cardModifiers: MutableList<Card.CardModifier> = mutableListOf()

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = Timeline.timeline {
            action {
                val modifier = Card.CardModifier(
                    0,
                    null,
                    validityChecker = { true }
                )
                card.addModifier(modifier)
                cardModifiers.add(modifier)
            }
        }

        override fun executeAfterRevolverWasShot(
            card: Card?,
            controller: GameController
        ): Timeline = Timeline.timeline {
            action {
                card ?: return@action
                val modifier = cardModifiers.firstOrNull { it in card.modifiers } ?: return@action
                cardModifiers.remove(modifier)
            }
        }

        override fun executeAfterRevolverRotated(
            rotation: RevolverRotation,
            controller: GameController
        ): Timeline = Timeline.timeline {
            action {
                controller
                    .revolver
                    .slots
                    .mapNotNull { it.card }
                    .forEach { updateModifierOfCard(it, rotation) }
            }
        }

        private fun updateModifierOfCard(card: Card, rotation: RevolverRotation) {
            val oldModifier = cardModifiers.firstOrNull { it in card.modifiers }
            val damage = (oldModifier?.damage ?: 0) - rotation.amount
            val newModifier = Card.CardModifier(
                damage,
                TemplateString("Bullet lost $damage damage due to the Moist Encounter Modifier"),
                validityChecker = { true }
            )
            oldModifier?.let {
                card.removeModifier(it)
                cardModifiers.remove(it)
            }
            card.addModifier(newModifier)
            cardModifiers.add(newModifier)
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
            "moist" -> Moist()
            else -> throw Exception("Unknown Encounter Modifier: $name")
        }
    }
}
