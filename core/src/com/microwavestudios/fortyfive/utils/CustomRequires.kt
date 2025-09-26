@file:OptIn(ExperimentalContracts::class)

package com.microwavestudios.fortyfive.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun unreachable(): Nothing = throw RuntimeException("unreachable reached")

@Suppress("NOTHING_TO_INLINE")
inline fun requreNull(value: Any?) {
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
