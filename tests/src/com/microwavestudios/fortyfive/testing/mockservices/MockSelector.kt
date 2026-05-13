package com.microwavestudios.fortyfive.testing.mockservices

import com.microwavestudios.fortyfive.game.ISelector
import com.microwavestudios.fortyfive.utils.Promise
import com.microwavestudios.fortyfive.utils.asPromise
import com.microwavestudios.fortyfive.utils.requireNot

class MockSelector<T> : ISelector<T> {

    private var selectionSetup: Boolean = false
    private var nextSelection: T? = null

    fun setupNextSelection(value: T) {
        requireNot(selectionSetup) { "cant overwrite selection" }
        nextSelection = value
        selectionSetup = true
    }

    override fun startSelect(): Promise<out T?> {
        require(selectionSetup) { "no value to return from startSelect" }
        selectionSetup = false
        return nextSelection.asPromise()
    }
}
