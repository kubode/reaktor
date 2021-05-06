package com.example.playgroundreactor

class Greeting {
    fun greeting(): String {
        return "Hello, ${Platform().platform}!"
    }
}