package com.fourinachamber.fortyfive.game.controller

interface EncounterContext {

    val encounterIndex: Int

    val forwardToScreen: String

    val forceCards: List<String>?
        get() = null

    fun completed()
}
