package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.game.GameController.RevolverRotation

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

    open fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = rotation
    open fun shouldApplyStatusEffects(): Boolean = true

}
