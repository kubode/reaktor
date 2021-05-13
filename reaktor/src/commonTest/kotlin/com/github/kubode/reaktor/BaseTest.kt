package com.github.kubode.reaktor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

expect abstract class BaseTest()

@Suppress("unused") // Because it's impossible to describe functions in the expect class.
fun BaseTest.runTest(block: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        block()
    }
}
