package com.microwavestudios.fortyfive.game.controller

import com.microwavestudios.fortyfive.run.Encounter

interface EncounterContext {

    val encounter: Encounter

    val forceCards: List<String>?
        get() = null

    fun completed()
}
