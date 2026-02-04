package com.microwavestudios.fortyfive.utils

import com.badlogic.gdx.utils.Disposable

/**
 * Lifetimes are used to keep track of how long certain parts of the game are
 * active (alive). For example, each screen holds a lifetime that dies when the
 * game transitions to a different screen. Lifetimes are mainly used to keep track of
 * when to dispose resources, but they can be used for various other tasks as well.
 */
interface Lifetime {

    /**
     * [callback] gets called when the lifetime dies, or immediately if it is already dead.
     */
    fun onEnd(callback: () -> Unit)

    /**
     * returns a new lifetime that dies when both this _and_ [lifetime] have died
     */
    fun longer(lifetime: Lifetime): Lifetime {
        val new = EndableLifetime()
        onEnd {
            lifetime.onEnd { new.die() }
        }
        return new
    }

    /**
     * returns a new lifetime that dies when this _or_ [lifetime] have died
     */
    fun shorter(lifetime: Lifetime): Lifetime {
        val new = EndableLifetime()
        onEnd { new.die() }
        lifetime.onEnd { new.die() }
        return new
    }

    /**
     * automatically calls [Disposable.dispose] on [disposable] when the lifetime ends
     */
    fun tieDisposable(disposable: Disposable) {
        onEnd { disposable.dispose() }
    }

    companion object {

        /** lifetime that never dies */
        val endless = object : Lifetime {

            override fun onEnd(callback: () -> Unit) {
            }
        }
    }

}

interface EndableLifetime : Lifetime {

    fun die()
}

/**
 * creates a new lifetime with a `die` function
 */
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
