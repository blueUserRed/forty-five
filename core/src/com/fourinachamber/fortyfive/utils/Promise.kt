package com.fourinachamber.fortyfive.utils

@Suppress("UNCHECKED_CAST")
class Promise<T> {

    var isResolved: Boolean = false
        private set

    private var result: T? = null

    private val callbacks: MutableList<(result: T) -> Unit> = mutableListOf()

    fun onResolve(callback: (result: T) -> Unit) {
        if (isResolved) callback(result as T)
        callbacks.add(callback)
    }

    fun resolve(result: T) {
        if (isResolved) throw RuntimeException("Promise was already resolved")
        this.result = result
        isResolved = true
        callbacks.forEach { it(result) }
    }

    fun getOrError(): T = if (isResolved) result as T else throw RuntimeException("Promise was not resolved yet")

    fun getOrNull(): T? = if (isResolved) result else null

    fun getOr(default: T): T = if (isResolved) result as T else default

}

fun <T> T.asPromise(): Promise<T> = Promise<T>().also { it.resolve(this) }
