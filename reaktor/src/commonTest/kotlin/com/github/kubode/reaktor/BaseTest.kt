package com.github.kubode.reaktor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

expect abstract class BaseTest() {
}

fun BaseTest.runTest(block: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        block()
    }
}
