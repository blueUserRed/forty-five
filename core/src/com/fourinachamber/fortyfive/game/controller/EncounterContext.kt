package com.fourinachamber.fortyfive.game.controller

import com.fourinachamber.fortyfive.screen.ScreenManager

interface EncounterContext {

    val encounterIndex: Int

    val screenChain: ScreenManager.ScreenChain

    val forceCards: List<String>?
        get() = null

    fun completed()
}
