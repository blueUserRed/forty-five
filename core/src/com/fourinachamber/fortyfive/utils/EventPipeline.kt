package com.fourinachamber.fortyfive.utils

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass
import kotlin.reflect.cast

class EventPipeline {

    private val watchers: CopyOnWriteArrayList<Pair<(Any) -> Unit, KClass<*>>> = CopyOnWriteArrayList()
    private val linkedTo: MutableList<EventPipeline> = mutableListOf()

    fun fire(event: Any) {
        watchers.forEach { (callback, clazz) ->2
            if (clazz.isInstance(event)) callback(clazz.cast(event))
        }
        linkedTo.forEach { it.fireFromLinked(event, mutableListOf(this)) }
    }

    private fun fireFromLinked(event: Any, previous: MutableList<EventPipeline>) {
        watchers.forEach { (callback, clazz) ->
            if (clazz.isInstance(event)) callback(clazz.cast(event))
        }
        linkedTo.forEach {
            if (it in previous) return@forEach
            previous.add(it)
            it.fireFromLinked(event, previous)
            previous.remove(it)
        }
    }

    fun <T : Any> watchFor(callback: (T) -> Unit, clazz: KClass<T>) {
        @Suppress("UNCHECKED_CAST")
        watchers.add((callback as (Any) -> Unit) to clazz)
    }

    inline fun <reified T : Any> watchFor(noinline callback: (T) -> Unit) {
        watchFor(callback, T::class)
    }

    fun link(other: EventPipeline) {
        linkedTo.add(other)
        other.linkedTo.add(this)
    }

}
