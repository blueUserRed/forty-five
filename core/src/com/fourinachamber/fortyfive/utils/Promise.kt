package com.fourinachamber.fortyfive.utils

import com.fourinachamber.fortyfive.FortyFive

@Suppress("UNCHECKED_CAST")
class Promise<T> {

    var isResolved: Boolean = false
        private set

    val isNotResolved: Boolean
        get() = !isResolved

    private var result: T? = null

    private val callbacks: MutableList<(result: T) -> Unit> = mutableListOf()

    fun then(callback: (result: T) -> Unit) {
        synchronized(callbacks) {
            if (isResolved) {
                callback(result as T)
                return
            }
            callbacks.add(callback)
        }
    }

    fun thenMainThread(callback: (result: T) -> Unit) {
        synchronized(callbacks) {
            if (isResolved) {
                FortyFive.mainThreadTask { callback(result as T) }
                return
            }
            callbacks.add {
                FortyFive.mainThreadTask { callback(result as T) }
            }
        }
    }

    fun resolve(result: T) {
        synchronized(callbacks) {
            if (isResolved) throw RuntimeException("Promise was already resolved")
            this.result = result
            isResolved = true
            callbacks.forEach { it(result) }
        }
    }

    fun getOrError(): T = if (isResolved) result as T else throw RuntimeException("Promise was not resolved yet")

    fun getOrNull(): T? = if (isResolved) result else null

    fun getOr(default: T): T = if (isResolved) result as T else default

    inline fun ifResolved(block: (T) -> Unit) {
        if (isResolved) block(getOrError())
    }

}

fun <T> T.asPromise(): Promise<T> = Promise<T>().also { it.resolve(this) }

fun <T, U> Promise<T>.map(mapper: (T) -> U): Promise<U> {
    val promise = Promise<U>()
    this.then { promise.resolve(mapper(it)) }
    return promise
}

fun <T, U> Promise<T>.chain(next: (T) -> Promise<U>): Promise<U> {
    val promise = Promise<U>()
    this.then { first ->
        val chained = next(first)
        chained.then { promise.resolve(it) }
    }
    return promise
}

fun <T, U> Promise<T>.chainMainThread(next: (T) -> Promise<U>): Promise<U> {
    val promise = Promise<U>()
    this.then { first ->
        FortyFive.mainThreadTask {
            val chained = next(first)
            chained.then { promise.resolve(it) }
        }
    }
    return promise
}
