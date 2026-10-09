package com.microwavestudios.fortyfive.game.controller

import com.microwavestudios.fortyfive.game.Talisman
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.run.Encounter

interface EncounterContext {

    val encounter: Encounter
    val isExtraction: Boolean

    val forceCards: List<CardType>?
        get() = null

    val forceBackground: ResourceHandle?
        get() = null

    val forceTalisman: Talisman?
        get() = null

    fun completed()
}
