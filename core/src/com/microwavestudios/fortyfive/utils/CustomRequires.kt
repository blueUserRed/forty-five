@file:OptIn(ExperimentalContracts::class)

package com.microwavestudios.fortyfive.utils

import com.microwavestudios.fortyfive.screen.IScreen
import com.microwavestudios.fortyfive.screen.RenderableScreen
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun unreachable(): Nothing = throw RuntimeException("unreachable reached")

@Suppress("NOTHING_TO_INLINE")
inline fun requireNull(value: Any?) {
    contract {
        returns() implies (value == null)
    }
    requireNull(value) { "Value must be null" }
}

inline fun requireNull(value: Any?, lazyMessage: () -> String) {
    contract {
        returns() implies (value == null)
    }
    if (value != null) {
        val message = lazyMessage()
        throw IllegalArgumentException(message)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun requireNot(condition: Boolean) {
    contract {
        returns() implies (!condition)
    }
    requireNot(condition) { "Value must be false" }
}

inline fun requireNot(condition: Boolean, lazyMessage: () -> String) {
    contract {
        returns() implies (!condition)
    }
    if (condition) {
        val message = lazyMessage()
        throw IllegalArgumentException(message)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun requireRenderableScreen(screen: IScreen) {
    contract {
        returns() implies (screen is RenderableScreen)
    }
    if (screen !is RenderableScreen) {
        throw IllegalArgumentException("Requires renderable screen, but different implementation was passed instead")
    }
}
