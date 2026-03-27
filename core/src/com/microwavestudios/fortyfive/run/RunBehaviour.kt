package com.microwavestudios.fortyfive.run

abstract class RunBehaviour {

    class ModifyAllEncounters(val encounterModifier: String) : RunBehaviour() {
        override val difficultyAdjustment: Float = 0f // handled by encounter
        override fun addEncounterModifier(): String? = encounterModifier
    }

    abstract val difficultyAdjustment: Float

    open fun addEncounterModifier(): String? = null

}
