package com.github.kubode.reaktor

import platform.Foundation.NSLog

actual fun log(message: String) {
    NSLog(message)
}
