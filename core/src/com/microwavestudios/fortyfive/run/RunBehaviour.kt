package com.microwavestudios.fortyfive.run

abstract class RunBehaviour {

    class ModifyAllEncounters(val encounterModifier: String) : RunBehaviour() {
        override fun addEncounterModifier(): String = encounterModifier
    }

    class ModifySteps(val stepChange: Int) : RunBehaviour() {
        override fun modifyMinSteps(original: Int): Int = original + stepChange
        override fun modifyMaxSteps(original: Int): Int = original + stepChange
    }

    open fun addEncounterModifier(): String? = null

    open fun modifyMinSteps(original: Int): Int = original
    open fun modifyMaxSteps(original: Int): Int = original

}
