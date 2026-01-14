package com.microwavestudios.fortyfive.utils

import com.microwavestudios.fortyfive.FortyFive

/**
 * A Promise is a container for a value. A function can return a promise
 * instead of the concrete value if the value may not be ready after the
 * function finishes. When te value then becomes available later, the
 * promise is resolved.
 */
@Suppress("UNCHECKED_CAST")
class Promise<T> {

    var isResolved: Boolean = false
        private set

    val isNotResolved: Boolean
        get() = !isResolved

    private var result: T? = null

    private val callbacks: MutableList<(result: T) -> Unit> = mutableListOf()

    /**
     * executed when the promise resolves, or immediately when the promise is
     * already resolved.
     */
    fun then(callback: (result: T) -> Unit) {
        synchronized(callbacks) {
            if (isResolved) {
                callback(result as T)
                return
            }
            callbacks.add(callback)
        }
    }

    /**
     * like [then], but uses [FortyFive.mainThreadTask] to execute [callback] on the
     * main (render) thread.
     */
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
            require(isNotResolved) { "Promise was already resolved" }
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

    companion object {
        val nullPromise: Promise<Nothing?> = Promise<Nothing?>().also { it.resolve(null) }
    }

}

/**
 * returns a [Promise] resolved with ``this``
 */
fun <T> T.asPromise(): Promise<T> = Promise<T>().also { it.resolve(this) }

/**
 * returns a new promise that resolves when ``this`` resolves, but maps the result
 * using [mapper]
 */
fun <T, U> Promise<T>.map(mapper: (T) -> U): Promise<U> {
    val promise = Promise<U>()
    this.then { promise.resolve(mapper(it)) }
    return promise
}

/**
 * waits until `this` resolves, the runs [next] and waits for it to resolve as well.
 * The returned promise waits for both promises and resolves with the value returned by the
 * promise from [next]
 */
fun <T, U> Promise<T>.chain(next: (T) -> Promise<U>): Promise<U> {
    val promise = Promise<U>()
    this.then { first ->
        val chained = next(first)
        chained.then { promise.resolve(it) }
    }
    return promise
}

/**
 * like [chain], but uses [FortyFive.mainThreadTask] to run [next] on the main (render) thread
 */
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
