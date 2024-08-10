package com.fourinachamber.fortyfive.utils

import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class SubscribeableObserver<T>(initialValue: T) {

    private val callbacks: MutableList<(old: T, new: T) -> Unit> = mutableListOf()
    private var backingField: T = initialValue

    fun getValue(): T = backingField

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return backingField
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        callbacks.forEach { it(backingField, value) }
        backingField = value
    }

    fun subscribe(callback: (old: T, new: T) -> Unit) {
        callbacks.add(callback)
    }

}

class AutomaticResourceGetter<T : Any>(
    handleProperty: SubscribeableObserver<String?>,
    val resourceType: KClass<T>,
    val guardLifetime: Lifetime
) : ResourceBorrower {

    private var backingField: T? = null
    private var currentPromise: Promise<T>? = null
    private var currentLifetime: EndableLifetime? = null

    init {
        handleProperty.subscribe(::onHandleChange)
        onHandleChange(null, handleProperty.getValue())
    }

    private fun onHandleChange(old: String?, new: String?) {
        if (old == new) return
        if (new == null) {
            currentLifetime?.die()
            backingField = null
            currentPromise = null
            return
        }
        val newLifetime = EndableLifetime()
        val guardedLifetime = guardLifetime.shorter(newLifetime)
        val promise = ResourceManager.request(this, guardedLifetime, new, resourceType)
        currentPromise = promise
        promise.onResolve { result ->
            if (currentPromise !== promise) return@onResolve
            backingField = result
            currentLifetime?.die()
            currentPromise = null
            currentLifetime = newLifetime
        }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = backingField

}

inline fun <reified T : Any> automaticResourceGetter(
    propertyObserver: SubscribeableObserver<String?>,
    guardLifetime: Lifetime,
) = AutomaticResourceGetter(
    propertyObserver,
    T::class,
    guardLifetime
)
