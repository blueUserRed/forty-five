package com.microwavestudios.fortyfive.testing

class AssertionTestException(
    testTitle: String,
    message: String
) : RuntimeException("\nAssert failed in $testTitle: $message")

class WrappedTestException(
    test: String,
    original: Exception
) : RuntimeException("Exception thrown in $test:", original)
