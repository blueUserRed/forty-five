package com.fourinachamber.fourtyfive.game

import com.fourinachamber.fourtyfive.game.GameController.RevolverRotation

sealed class EncounterModifier {

    object Rain : EncounterModifier() {
        override fun shouldApplyStatusEffects(): Boolean = false
    }

    object Frost : EncounterModifier() {
        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = RevolverRotation.DONT
    }

    open fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = rotation
    open fun shouldApplyStatusEffects(): Boolean = true

}
