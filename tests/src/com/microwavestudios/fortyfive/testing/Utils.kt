package com.microwavestudios.fortyfive.testing

class NotAvailableInMockException : RuntimeException()

fun notAvailableInMock(): Nothing = throw NotAvailableInMockException()
