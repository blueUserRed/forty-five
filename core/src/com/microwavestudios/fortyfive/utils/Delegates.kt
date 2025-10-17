package com.microwavestudios.fortyfive.utils

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class SubscribeableObserver<T>(initialValue: T) {

    private val callbacks: MutableList<(old: T, new: T) -> Unit> = mutableListOf()
    private var backingField: T = initialValue

    fun getValue(): T = backingField

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = backingField

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
    val guardLifetime: Lifetime,
    val hints: Array<String>
) : ResourceBorrower {

    private var backingField: T? = null
    var currentResourceHandle: ResourceHandle? = null
        private set

    private var activeResource: Resource<T>? = null
    private var wantedResource: String? = null

    private val onResolveCallbacks: MutableList<(newResource: T) -> Unit> = mutableListOf()

    private val resources: MutableList<Resource<T>> = mutableListOf()

    init {
        handleProperty.subscribe(::onHandleChange)
        onHandleChange(null, handleProperty.getValue())
    }

    fun onResourceChange(callback: (newResource: T) -> Unit) {
        onResolveCallbacks.add(callback)
    }

    private fun cleanupUnused() {
        resources.iterateRemoving { resource, remover ->
            if (resource.handle == wantedResource) return@iterateRemoving
            if (resource.handle == activeResource?.handle) return@iterateRemoving
            if (resource.alwaysLoaded) return@iterateRemoving
            resource.lifetime.die()
            remover()
        }
    }

    private fun onHandleChange(old: String?, new: String?) {
        if (old == new) return

        wantedResource = new
        if (new == null) {
            backingField = null
            currentResourceHandle = null
            activeResource = null
            cleanupUnused()
            return
        }

        val wanted = resources.find { it.handle == new }
        if (wanted != null && wanted.promise.isResolved) {
            backingField = wanted.promise.getOrError()
            currentResourceHandle = wanted.handle
            activeResource = wanted
        }
        if (wanted != null) return

        val lifetime = EndableLifetime()
        val guardedLifetime = lifetime.shorter(guardLifetime)
        val promise = FortyFive.resourceManager.request(this, guardedLifetime, new, resourceType)
        val newResource = Resource(new, promise, lifetime, alwaysLoaded = new in hints)
        resources.add(newResource)

        promise.then { result ->
            if (wantedResource != new) {
                cleanupUnused()
                return@then
            }
            backingField = result
            currentResourceHandle = new
            activeResource = newResource
            cleanupUnused()
            onResolveCallbacks.forEach { it(result) }
        }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = backingField

    private data class Resource<T>(
        val handle: String,
        val promise: Promise<T>,
        val lifetime: EndableLifetime,
        val alwaysLoaded: Boolean = false,
    )

}

inline fun <reified T : Any> automaticResourceGetter(
    propertyObserver: SubscribeableObserver<String?>,
    guardLifetime: Lifetime,
    hints: Array<String>,
) = AutomaticResourceGetter(
    propertyObserver,
    T::class,
    guardLifetime,
    hints
)
