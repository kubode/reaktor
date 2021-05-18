package com.github.kubode.reaktor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

expect abstract class BaseTest()

@Suppress("unused") // Because it's impossible to describe functions in the expect class.
fun BaseTest.runTest(timeoutMillis: Long = 1000, block: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        withTimeout(timeoutMillis) {
            block()
        }
    }
}
