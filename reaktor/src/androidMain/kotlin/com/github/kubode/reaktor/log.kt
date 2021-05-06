package com.github.kubode.reaktor

import android.util.Log

actual fun log(message: String) {
    Log.v("Shared", message)
}
