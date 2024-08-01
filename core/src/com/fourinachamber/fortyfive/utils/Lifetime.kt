package com.fourinachamber.fortyfive.utils

interface Lifetime {

    fun onEnd(callback: () -> Unit)
}

interface EndableLifetime : Lifetime {

    fun die()
}

@Suppress("FunctionName")
fun Lifetime(): EndableLifetime = object : EndableLifetime {

    private val callbacks: MutableList<() -> Unit> = mutableListOf()

    override fun onEnd(callback: () -> Unit) {
        callbacks.add(callback)
    }

    override fun die() {
        callbacks.forEach { it() }
    }
}
