package com.microwavestudios.fortyfive.game.controller

interface EncounterContext {

    val encounterIndex: Int

    val forceCards: List<String>?
        get() = null

    fun completed()
}
