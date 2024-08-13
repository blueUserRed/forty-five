package com.fourinachamber.fortyfive.utils

interface Lifetime {

    fun onEnd(callback: () -> Unit)

    fun longer(lifetime: Lifetime): Lifetime {
        val new = EndableLifetime()
        onEnd {
            lifetime.onEnd { new.die() }
        }
        return new
    }

    fun shorter(lifetime: Lifetime): Lifetime {
        val new = EndableLifetime()
        onEnd { new.die() }
        lifetime.onEnd { new.die() }
        return new
    }

}

interface EndableLifetime : Lifetime {

    fun die()
}

fun EndableLifetime(): EndableLifetime = object : EndableLifetime {

    private val callbacks: MutableList<() -> Unit> = mutableListOf()

    private var died: Boolean = false

    override fun onEnd(callback: () -> Unit) {
        if (died) {
            callback()
            return
        }
        callbacks.add(callback)
    }

    override fun die() {
        if (died) return
        died = true
        callbacks.forEach { it() }
    }
}
