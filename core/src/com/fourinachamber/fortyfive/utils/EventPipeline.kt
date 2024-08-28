package com.fourinachamber.fortyfive.utils

import kotlin.reflect.KClass
import kotlin.reflect.cast

class EventPipeline {

    private val watchers: MutableList<Pair<(Any) -> Unit, KClass<*>>> = mutableListOf()

    fun fire(event: Any) {
        watchers.forEach { (callback, clazz) ->
            if (clazz.isInstance(event)) callback(clazz.cast(event))
        }
    }

    fun <T : Any> watchFor(callback: (T) -> Unit, clazz: KClass<T>) {
        @Suppress("UNCHECKED_CAST")
        watchers.add((callback as (Any) -> Unit) to clazz)
    }

    inline fun <reified T : Any> watchFor(noinline callback: (T) -> Unit) {
        watchFor(callback, T::class)
    }

}
