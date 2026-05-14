package com.microwavestudios.fortyfive.testing

open class TestException(message: String, original: Exception? = null) : RuntimeException(message, original)

class AssertionTestException(
    testTitle: String,
    message: String
) : TestException("\nAssert failed in $testTitle: $message")

class WrappedTestException(
    test: String,
    original: Exception
) : TestException("Exception thrown in $test:", original)
