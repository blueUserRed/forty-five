package com.microwavestudios.fortyfive.testing

import com.microwavestudios.fortyfive.utils.ANSI

abstract class Assertion {

    abstract fun check(): String?
    abstract override  fun toString(): String

    class Equal(val left: AssertionValue<*>, val right: AssertionValue<*>) : Assertion() {

        override fun check(): String? {
            val left = left.get()
            val right = right.get()
            if (left == right) return null
            return "'$left' does not equal '$right'"
        }

        override fun toString(): String = "$left ${ANSI.purple}equal${ANSI.reset} $right"
    }

    class NotEqual(val left: AssertionValue<*>, val right: AssertionValue<*>) : Assertion() {

        override fun check(): String? {
            val left = left.get()
            val right = right.get()
            if (left != right) return null
            return "'$left' is equal to '$right'"
        }

        override fun toString(): String = "$left ${ANSI.purple}notEqual${ANSI.reset} $right"

    }

    class IsNull(val value: AssertionValue<*>) : Assertion() {

        override fun check(): String? {
            val value = value.get() ?: return null
            return "'$value != null'"
        }

        override fun toString(): String =
            "${ANSI.white}($value${ANSI.white}).${ANSI.purple}isNull${ANSI.reset}"
    }

}

abstract class AssertionValue<out T> {

    abstract fun get(): T

    abstract override fun toString(): String

    fun <T> immediate(value: T): AssertionValue<T> = object : AssertionValue<T>() {
        override fun get(): T = value
        override fun toString(): String = "${ANSI.blue}'$value'${ANSI.reset}"
    }

    override fun equals(other: Any?): Boolean = throw RuntimeException("comparing AssertionValues using == is forbidden")

    infix fun equal(other: AssertionValue<*>): Assertion = Assertion.Equal(this, other)

    infix fun equal(other: Any): Assertion = Assertion.Equal(this, immediate(other))

    infix fun notEqual(other: AssertionValue<*>): Assertion = Assertion.NotEqual(this, other)

    infix fun notEqual(other: Any): Assertion = Assertion.NotEqual(this, immediate(other))

    fun isNull(): Assertion = Assertion.IsNull(this)
}
